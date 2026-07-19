import type { H3Event } from 'h3';

/**
 * 计算站点对外根地址（不带结尾斜杠）。
 * 优先用 NUXT_PUBLIC_SITE_URL（正式域名场景）；未配置时，按本次请求的协议+域名/IP 自动识别，
 * 从而做到「上传即用」：换服务器、换 IP 都无需改配置，SEO 绝对链接也指向当前访问地址。
 */
export function resolveSiteUrl(event: H3Event): string {
  const configured = String(useRuntimeConfig().public.siteUrl || '').trim();
  if (configured) {
    return configured.replace(/\/$/, '');
  }
  const headers = event.node.req.headers;
  const pick = (value: string | string[] | undefined) =>
    (Array.isArray(value) ? value[0] : value || '').split(',')[0].trim();
  const proto = pick(headers['x-forwarded-proto']) || 'http';
  const host = pick(headers['x-forwarded-host']) || pick(headers['host']);
  return host ? `${proto}://${host}` : '';
}
