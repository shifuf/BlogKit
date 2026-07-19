import { fetchPublishedArticles, escapeXml } from '../utils/seo';
import { resolveSiteUrl } from '../utils/site';

export default defineEventHandler(async event => {
  const site = resolveSiteUrl(event);
  const config = useRuntimeConfig();
  const articles = (await fetchPublishedArticles()).slice(0, 30);
  // 站点名优先读公开设置，失败则用默认
  let channelTitle = '我的博客';
  let channelDesc = '记录技术、项目与生活';
  try {
    const base = String(config.backendOrigin || '').replace(/\/$/, '');
    const settings = await $fetch<{ data?: { siteName?: string; siteDescription?: string; siteSubtitle?: string } }>(
      `${base}/api/v1/site-settings`
    );
    const data = (settings as any)?.data || settings;
    if (data?.siteName) channelTitle = data.siteName;
    if (data?.siteDescription || data?.siteSubtitle) {
      channelDesc = data.siteDescription || data.siteSubtitle;
    }
  } catch {
    // ignore
  }

  const items = articles.map(article => {
    const link = `${site}/article/${article.id}`;
    const pubDate = article.publishedAt ? new Date(article.publishedAt).toUTCString() : '';
    const lines = [
      '    <item>',
      `      <title>${escapeXml(article.title)}</title>`,
      `      <link>${escapeXml(link)}</link>`,
      `      <guid isPermaLink="true">${escapeXml(link)}</guid>`,
      `      <description>${escapeXml(article.summary || '')}</description>`,
      `      <dc:creator>${escapeXml(channelTitle)}</dc:creator>`
    ];
    if (pubDate) lines.push(`      <pubDate>${pubDate}</pubDate>`);
    if (article.categorySlug) {
      lines.push(`      <category>${escapeXml(article.categorySlug)}</category>`);
    }
    lines.push('    </item>');
    return lines.join('\n');
  }).join('\n');

  // 使用 application/xml + Dublin Core 命名空间，浏览器会按 XML 树展示（与常见 RSS 阅读器兼容）
  setHeader(event, 'Content-Type', 'application/xml; charset=utf-8');
  setHeader(event, 'Cache-Control', 'public, max-age=3600');
  setHeader(event, 'X-Content-Type-Options', 'nosniff');

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<rss xmlns:dc="http://purl.org/dc/elements/1.1/" version="2.0">',
    '  <channel>',
    `    <title>${escapeXml(channelTitle)}</title>`,
    `    <link>${escapeXml(site)}</link>`,
    `    <description>${escapeXml(channelDesc)}</description>`,
    '    <language>zh-CN</language>',
    `    <lastBuildDate>${new Date().toUTCString()}</lastBuildDate>`,
    `    <atom:link xmlns:atom="http://www.w3.org/2005/Atom" href="${escapeXml(`${site}/feed.xml`)}" rel="self" type="application/rss+xml"/>`,
    items,
    '  </channel>',
    '</rss>'
  ].join('\n');
});
