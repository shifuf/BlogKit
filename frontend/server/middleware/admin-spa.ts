import { createError, setHeader, sendRedirect, getRequestURL } from 'h3';

function normalizeAdminBasePath(raw: string | undefined) {
  const value = String(raw || '/admin').trim() || '/admin';
  const withSlash = value.startsWith('/') ? value : `/${value}`;
  return withSlash.replace(/\/+$/, '') || '/admin';
}

// Admin SPA HTML is returned manually; inject admin-only CSP so it always applies.
// AMap JS API 2.0 WebGL uses new Function(); wasm-unsafe-eval alone is insufficient.
// AMap loads only in admin, so public pages keep a stricter CSP without eval.
const ADMIN_CSP = [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline' 'unsafe-eval' 'wasm-unsafe-eval' https://webapi.amap.com https://*.amap.com https://cdn.sencdn.com",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob: https: http:",
  "font-src 'self' data:",
  "media-src 'self' https: http: blob:",
  "connect-src 'self' https: http: ws: wss:",
  "worker-src 'self' blob:",
  "frame-src 'self'",
  "base-uri 'self'",
  "object-src 'none'"
].join('; ');

export default defineEventHandler(async event => {
  const adminBasePath = normalizeAdminBasePath(process.env.BLOG_ADMIN_BASE_PATH);
  const pathname = getRequestURL(event).pathname || '';
  if (pathname !== adminBasePath && !pathname.startsWith(`${adminBasePath}/`)) {
    return;
  }
  if (pathname === adminBasePath) {
    return sendRedirect(event, `${adminBasePath}/`, 302);
  }
  // Static assets under adminBasePath are served by nitro publicAssets.
  const lastSegment = pathname.slice(adminBasePath.length + 1);
  if (lastSegment && lastSegment.includes('.')) {
    return;
  }
  const indexHtml = await useStorage('assets:admin').getItem<string>('index.html');
  if (!indexHtml) {
    throw createError({
      statusCode: 503,
      message: 'Admin bundle is missing. Run npm run build in admin before building frontend.'
    });
  }
  setHeader(event, 'content-type', 'text/html; charset=utf-8');
  setHeader(event, 'cache-control', 'no-store');
  setHeader(event, 'Content-Security-Policy', ADMIN_CSP);
  return indexHtml;
});
