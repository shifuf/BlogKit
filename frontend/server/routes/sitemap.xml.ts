import { fetchPublishedArticles, escapeXml } from '../utils/seo';
import { resolveSiteUrl } from '../utils/site';
export default defineEventHandler(async event => {
  const site = resolveSiteUrl(event);
  const articles = await fetchPublishedArticles();
  const staticPaths = ['', '/category', '/tags', '/dynamics', '/search'];
  const urls: string[] = staticPaths.map(path => `  <url><loc>${escapeXml(site + path)}</loc></url>`);
  for (const article of articles) {
    const lastmod = article.publishedAt ? `<lastmod>${new Date(article.publishedAt).toISOString()}</lastmod>` : '';
    urls.push(`  <url><loc>${escapeXml(`${site}/article/${article.id}`)}</loc>${lastmod}<changefreq>weekly</changefreq></url>`);
  }
  setHeader(event, 'Content-Type', 'application/xml; charset=utf-8');
  setHeader(event, 'Cache-Control', 'public, max-age=3600');
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls.join('\n')}\n</urlset>`;
});
