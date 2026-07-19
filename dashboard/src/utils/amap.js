// 高德地图 JS API 2.0 按需加载。
// 采用 2.0 + key + 安全码的最简加载逻辑；额外封装曾导致误 reject，故保持精简。
// key/安全码由调用方从后台「系统设置 → 首页配置」传入（存库）。
const PLUGINS = ['AMap.PlaceSearch', 'AMap.Geocoder', 'AMap.Geolocation', 'AMap.ToolBar'];
let loadPromise = null;

function loadScript(key, security) {
  if (window.AMap) return Promise.resolve(window.AMap);
  if (!key) return Promise.reject(new Error('未配置高德地图 Key，请在「系统设置 → 首页配置」中填写'));
  if (security) {
    window._AMapSecurityConfig = { securityJsCode: security };
  }
  return new Promise((resolve, reject) => {
    const existing = document.getElementById('amap-jsapi');
    if (existing) {
      existing.addEventListener('load', () => (window.AMap ? resolve(window.AMap) : reject(new Error('AMap 脚本已加载但未挂载'))));
      existing.addEventListener('error', () => reject(new Error('高德地图脚本加载失败（网络或域名被拦截）')));
      return;
    }
    const script = document.createElement('script');
    script.id = 'amap-jsapi';
    script.type = 'text/javascript';
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}`;
    script.onload = () => (window.AMap ? resolve(window.AMap) : reject(new Error('AMap 脚本已加载但未挂载（多为安全码/域名鉴权失败）')));
    script.onerror = () => reject(new Error('高德地图脚本加载失败（网络或域名被拦截）'));
    document.head.appendChild(script);
  });
}

// 返回 AMap，且保证所需插件已就绪。加载失败时清空缓存，便于下次重试。
// 首次调用需传入 key（及可选 security）；脚本一旦加载成功后续调用复用缓存。
export function loadAMap(key, security) {
  if (typeof window === 'undefined') return Promise.reject(new Error('no window'));
  if (loadPromise) return loadPromise;
  loadPromise = loadScript(key, security).then(AMap => new Promise(resolve => {
    AMap.plugin(PLUGINS, () => resolve(AMap));
  })).catch(err => {
    loadPromise = null;
    throw err;
  });
  return loadPromise;
}
