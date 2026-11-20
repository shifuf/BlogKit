import { categories as fallbackCategories, siteMeta } from '~/data/blog';
import type { Article, ArticleDetail, ArticleSummary, Category, CategorySummary, CommentItem, CommentSummary, FriendLinkSummary, LinkItem } from '~/types/blog';
import { formatDateTime, formatRelativeTime } from '~/utils/date';
const fallbackCovers = [{
  url: '/layout-interaction/static/picture/no-cover.png',
  color: '#388ff0'
}, {
  url: '/layout-interaction/static/picture/no-cover.png',
  color: '#41289a'
}, {
  url: '/layout-interaction/static/picture/no-cover.png',
  color: '#3a7bee'
}, {
  url: '/layout-interaction/static/picture/no-cover.png',
  color: '#6792c1'
}, {
  url: '/layout-interaction/static/picture/no-cover.png',
  color: '#387cf0'
}];
const fallbackCoverAt = (seed: number) => fallbackCovers[Math.abs(seed) % fallbackCovers.length]!;
// 无封面占位图配色（柔和、带一点颜色），按 seed 轮换。
const PLACEHOLDER_COLORS = ['#5b8def', '#7c6ccf', '#3aa0a8', '#e08a3c', '#9a6ad8', '#4aa564'];
const placeholderColorAt = (seed: number) => PLACEHOLDER_COLORS[Math.abs(seed) % PLACEHOLDER_COLORS.length]!;
/**
 * 「暂无图片」占位图：统一用 public 下的静态 PNG（根目录原 暂无图片.png 重命名而来），
 * 用于前台/列表无封面场景。seed 参数保留以兼容旧调用，但占位图固定为同一张。
 */
export const NO_COVER_IMAGE = '/layout-interaction/static/picture/no-cover.png';
export const placeholderCover = (_seed = 0): string => NO_COVER_IMAGE;
const SEED_COVER_COLORS: Record<string, string> = {
  'platform-launch.svg': '#1d4ed8',
  'architecture.svg': '#2563eb',
  'publishing-workflow.svg': '#ed9c13',
  'operations-checklist.svg': '#0eb488',
  'knowledge-asset.svg': '#0e94b4'
};
const seedCoverColor = (url?: string | null) => {
  const match = /\/seed-covers\/([^/?#]+)/.exec(url || '');
  return match ? SEED_COVER_COLORS[match[1]!] : undefined;
};
export const resolveHeroColor = (coverColor: string | null | undefined, realCover: string, fallbackColor: string): string | undefined => {
  if (coverColor) return coverColor;
  if (realCover) return seedCoverColor(realCover);
  return fallbackColor;
};
const accents = ['#409eff', '#10b981', '#f97316', '#7c3aed', '#0891b2', '#ef4444'];
export const slugify = (value?: string | null) => String(value || 'all').trim().toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fa5-]/g, '');
export const normalizeAssetUrl = (url?: string | null, seed = 0) => {
  return normalizeOptionalAssetUrl(url) || fallbackCoverAt(seed).url;
};
export const normalizeOptionalAssetUrl = (url?: string | null) => {
  if (!url) return '';
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('/')) return url;
  return `/${url.replace(/^\/+/, '')}`;
};
const FILE_MANAGER_PUBLIC_PATH = '/api/public/files/';
export const buildCoverUrl = (url: string | null | undefined, width: number) => {
  if (!url || !Number.isFinite(width) || width <= 0) return url || '';
  if (!url.includes(FILE_MANAGER_PUBLIC_PATH)) return url;
  if (/[?&](w|format)=/.test(url)) return url;
  const separator = url.includes('?') ? '&' : '?';
  return `${url}${separator}w=${Math.round(width)}&format=auto`;
};
// 头像走统一文件管理时缩略为小图(默认 200px，覆盖最大 104px 头像的 2x 视网膜)，避免直出原图几百 KB。
export const buildAvatarUrl = (url: string | null | undefined, width = 200) => buildCoverUrl(url, width);
const formatCount = (value: number | string | undefined) => {
  const numeric = Number(value || 0);
  if (numeric >= 100000000) return `${(numeric / 100000000).toFixed(1).replace(/\.0$/, '')}亿+`;
  if (numeric >= 10000) return `${(numeric / 10000).toFixed(1).replace(/\.0$/, '')}W+`;
  if (numeric >= 1000) return `${(numeric / 1000).toFixed(1).replace(/\.0$/, '')}K+`;
  return String(numeric);
};
const splitContent = (content?: string | null, summary?: string) => {
  const source = String(content || summary || '').trim();
  if (!source) return ['暂无正文内容。'];
  return source.split(/\n{2,}/).map(item => item.replace(/^#{1,6}\s*/, '').trim()).filter(Boolean);
};
export const toBlogArticle = (item: ArticleSummary | ArticleDetail, index = 0): Article => {
  const detail = item as ArticleDetail;
  const rawContent = 'content' in item ? detail.content : '';
  const content = splitContent('', item.summary);
  const seed = Number(item.id || index);
  const realCover = normalizeOptionalAssetUrl(item.coverImageUrl);
  const placeholder = placeholderCover(seed);
  return {
    slug: String(item.id),
    title: item.title,
    excerpt: item.summary || '',
    content,
    rawContent,
    seriesId: detail.seriesId, seriesSlug: detail.seriesSlug, seriesName: detail.seriesName, seriesOrder: detail.seriesOrder,
    category: item.categorySlug || slugify(item.categoryName),
    categoryName: item.categoryName || '',
    tags: item.tagNames || [],
    cover: realCover || placeholder,
    heroColor: resolveHeroColor(item.coverColor, realCover, placeholderColorAt(seed)),
    author: item.authorName || siteMeta.name,
    avatar: normalizeOptionalAssetUrl(item.authorAvatarUrl) || siteMeta.avatar,
    date: formatDateTime(item.publishedAt),
    publishedAt: item.publishedAt,
    views: formatCount(item.viewCount),
    comments: Number(item.commentCount || 0),
    likes: Number(item.likeCount || 0),
    favorites: Number(item.favoriteCount || 0),
    sticky: Boolean(item.sticky),
    sourceType: detail.sourceType || 'ORIGINAL',
    sourceUrl: detail.sourceUrl || '',
    location: detail.location || '',
    aiSummary: detail.aiSummary || '',
    commentsEnabled: item.commentsEnabled !== false,
    visibility: detail.visibility,
    locked: detail.locked === true,
    requiresPassword: detail.requiresPassword === true,
    titleHighlight: item.titleHighlight || null,
    summaryHighlight: item.summaryHighlight || null
  };
};
export const toBlogCategory = (item?: CategorySummary | null, index = 0): Category | undefined => {
  if (!item) return undefined;
  const fallback = fallbackCategories.find(category => category.slug === item.slug);
  return {
    slug: item.slug,
    label: item.name,
    description: fallback?.description || `${item.name} 分类下的文章与记录。`,
    accent: fallback?.accent || accents[index % accents.length]!,
    icon: item.iconCode || fallback?.icon
  };
};
export const toBlogComment = (comment: CommentSummary): CommentItem => {
  const isAdmin = String(comment.authorRoleCode || '').toUpperCase() === 'ADMIN';
  const badges = [
    comment.pinned ? '置顶' : '',
    isAdmin ? '博主' : ''
  ].filter(Boolean);
  return {
    id: comment.id,
    author: comment.authorName,
    content: comment.content,
    date: formatRelativeTime(comment.createdAt),
    createdAt: comment.createdAt,
    // 无头像(游客/未设头像)时留空，由评论组件回退为「昵称首字」头像；不再统一套用站点默认头像。
    avatar: normalizeOptionalAssetUrl(comment.authorAvatarUrl),
    level: isAdmin ? 'Lv.MAX' : 'Lv.1',
    badge: badges[0] || undefined,
    verified: isAdmin,
    // IP 归属地（省/市），后端字段名为 region，前台以 location 标签展示。
    location: comment.region || undefined,
    status: comment.status,
    mine: comment.mine === true,
    replyToCommentId: comment.replyToCommentId,
    replyTo: comment.replyTo || undefined,
    replyToContent: comment.replyToContent,
    replyToUnavailable: comment.replyToUnavailable,
    replyCount: comment.replyCount,
    replies: (comment.replies || []).map(toBlogComment)
  };
};
export const toFriendLink = (link: FriendLinkSummary): LinkItem => ({
  title: link.name,
  desc: link.description || '站点链接',
  url: link.url,
  logo: normalizeAssetUrl(link.logoUrl, link.id)
});
