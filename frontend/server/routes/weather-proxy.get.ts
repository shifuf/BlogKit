import { defineEventHandler, getQuery, getRequestHeader, setHeader } from 'h3';

// 心知天气 widget-v3 数据代理：浏览器直连该接口存在跨域与 CSP 限制，改由 Nitro 服务端拉取后转发。
// 前端天气组件（components/Widget/Weather.vue）据此自绘 UI，不再依赖心知的 bundle.js 挂件脚本。
//
// 定位根因：心知 widget 接口按「实际发起连接的 socket IP」做 IP 定位，忽略 location 明文城市名，
// 只认自家 locationV3 编码；query 里传城市名无效。而服务端直连时它看到的是服务器出口 IP（如北京机房），
// 于是所有访客都显示服务器所在城市。实测心知会优先信任 X-Forwarded-For / X-Real-IP 头里的 IP 做定位，
// 因此这里把访客真实 IP 透传给心知，即可按访客所在地返回天气；拿不到真实公网 IP 时回退默认城市。
// Token 仅从环境变量读取，不再内置默认值（避免仓库泄露心知 token）。
const WIDGET_TOKEN = process.env.SENIVERSE_WIDGET_TOKEN || process.env.NUXT_SENIVERSE_WIDGET_TOKEN || '';
// 默认城市（北京）locationV3 编码：仅在拿不到访客公网 IP（本机/内网调试）时兜底，避免空定位。
const DEFAULT_LOCATION = 'WX4FBXXFKE4F';

// 内网/本机 IP：这些地址传给心知也定位不出，直接判定为「无有效访客 IP」。
const isPrivateOrLocal = (ip: string): boolean => {
  if (!ip) return true;
  if (ip === '::1' || ip === '0:0:0:0:0:0:0:1' || ip.startsWith('localhost')) return true;
  if (ip.startsWith('127.') || ip.startsWith('10.') || ip.startsWith('192.168.')) return true;
  if (ip.startsWith('169.254.')) return true; // link-local
  if (ip.startsWith('fc') || ip.startsWith('fd') || ip.startsWith('fe80')) return true; // IPv6 私有/链路本地
  if (ip.startsWith('172.')) {
    const second = Number(ip.split('.')[1]);
    return second >= 16 && second <= 31;
  }
  return false;
};

// 解析访客真实公网 IP：优先 X-Forwarded-For 链最右（nginx $proxy_add_x_forwarded_for 追加的真实连接 IP），
// 回退 X-Real-IP，再回退 socket 远端地址。与后端 ClientIpResolver 同思路（越靠右越可信）。
const resolveVisitorIp = (event: Parameters<typeof getQuery>[0]): string => {
  const xff = getRequestHeader(event, 'x-forwarded-for');
  if (xff) {
    const chain = xff.split(',').map(p => p.trim()).filter(Boolean);
    // 从右往左找到第一个非内网 IP，即真实访客（跳过反代自注入的内网条目）。
    for (let i = chain.length - 1; i >= 0; i--) {
      const candidate = normalizeIp(chain[i]);
      if (candidate && !isPrivateOrLocal(candidate)) {
        return candidate;
      }
    }
  }
  const realIp = getRequestHeader(event, 'x-real-ip');
  if (realIp) {
    const candidate = normalizeIp(realIp.trim());
    if (candidate && !isPrivateOrLocal(candidate)) {
      return candidate;
    }
  }
  const remote = normalizeIp(event.node.req.socket?.remoteAddress || '');
  return remote && !isPrivateOrLocal(remote) ? remote : '';
};

// 去掉 IPv4-mapped IPv6 前缀（::ffff:1.2.3.4 → 1.2.3.4）与端口。
const normalizeIp = (ip: string): string => {
  let value = ip.replace(/^::ffff:/i, '');
  // 形如 1.2.3.4:5678 的带端口写法（IPv6 带端口通常包在 [] 里，这里只处理 IPv4）。
  if (/^\d+\.\d+\.\d+\.\d+:\d+$/.test(value)) {
    value = value.split(':')[0];
  }
  return value;
};

export default defineEventHandler(async event => {
  if (!WIDGET_TOKEN) {
    setHeader(event, 'Content-Type', 'application/json; charset=utf-8');
    return { error: 'weather token not configured', message: '请配置 SENIVERSE_WIDGET_TOKEN 或 NUXT_SENIVERSE_WIDGET_TOKEN' };
  }
  const query = getQuery(event);
  const geolocation = query.geolocation === 'false' ? 'false' : 'true';
  const visitorIp = resolveVisitorIp(event);
  // 前端可显式传 location（心知 locationV3 编码）覆盖；否则按访客 IP 定位，拿不到公网 IP 时用默认城市兜底。
  const explicitLocation = typeof query.location === 'string' && query.location ? query.location : '';
  const location = explicitLocation || (visitorIp ? '' : DEFAULT_LOCATION);

  const url = `https://widget-v3.seniverse.com/api/weather/${WIDGET_TOKEN}`
    + `?unit=c&language=zh-Hans&location=${encodeURIComponent(location)}`
    + `&geolocation=${geolocation}&detected=zh-cn`;
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 5000);
    const headers: Record<string, string> = {
      'User-Agent': 'Mozilla/5.0',
      Accept: 'application/json'
    };
    // 把访客真实 IP 透传给心知，使其按访客所在地（而非服务器机房）定位。
    if (visitorIp) {
      headers['X-Forwarded-For'] = visitorIp;
      headers['X-Real-IP'] = visitorIp;
    }
    const res = await fetch(url, {
      headers,
      signal: controller.signal
    });
    clearTimeout(timer);
    if (!res.ok) {
      setHeader(event, 'cache-control', 'no-store');
      return { success: false, results: [] };
    }
    const data = await res.json();
    // 心知可能返回 HTTP 200 但 body 带 AUTHORIZATION_ERROR（token 无效/未授权）。
    if (data && (data.success === false || data.error === 'AUTHORIZATION_ERROR' || data.status === 'failed')) {
      setHeader(event, 'cache-control', 'no-store');
      const { sealPayload } = await import('../../utils/apiPayloadCrypto');
      return await sealPayload({
        success: false,
        results: [],
        error: data.error || 'WEATHER_ERROR',
        message: data.message || '心知天气授权失败，请检查 SENIVERSE_WIDGET_TOKEN'
      });
    }
    // 心知数据 10 分钟级更新；但按访客 IP 定位的结果因人而异，不能走共享边缘缓存，
    // 否则第一个访客的城市会被缓存给所有人（正是「一直是北京」的放大器）。改为按访客私有缓存 10 分钟。
    setHeader(event, 'cache-control', 'private, max-age=600');
    setHeader(event, 'vary', 'X-Forwarded-For, X-Real-IP');
    // 响应密封，避免浏览器 Network 面板直接看到心知明文
    const { sealPayload } = await import('../../utils/apiPayloadCrypto');
    return await sealPayload(data);
  } catch {
    setHeader(event, 'cache-control', 'no-store');
    const { sealPayload } = await import('../../utils/apiPayloadCrypto');
    return await sealPayload({ success: false, results: [] });
  }
});
