import { resolveSiteUrl } from '../utils/site';

function normalizeAdminBasePath(raw: string | undefined) {
  const value = String(raw || '/admin').trim() || '/admin';
  const withSlash = value.startsWith('/') ? value : `/${value}`;
  return withSlash.replace(/\/+$/, '') || '/admin';
}

export default defineEventHandler(event => {
  const site = resolveSiteUrl(event);
  const adminBasePath = normalizeAdminBasePath(process.env.BLOG_ADMIN_BASE_PATH);
  setHeader(event, 'Content-Type', 'text/plain; charset=utf-8');
  setHeader(event, 'Cache-Control', 'public, max-age=86400');
  return [
    'User-agent: *',
    'Allow: /',
    `Disallow: ${adminBasePath}`,
    'Disallow: /api/',
    'Disallow: /oauth/',
    '',
    `Sitemap: ${site}/sitemap.xml`,
    ''
  ].join('\n');
});