interface PublicArticleSummary {
  id: number;
  title: string;
  summary: string;
  categorySlug?: string;
  publishedAt?: string;
}
export interface SeoArticle {
  id: number;
  title: string;
  summary: string;
  categorySlug?: string;
  publishedAt?: string;
}
export async function fetchPublishedArticles(): Promise<SeoArticle[]> {
  const config = useRuntimeConfig();
  const base = String(config.backendOrigin || '').replace(/\/$/, '');
  const out: SeoArticle[] = [];
  const size = 200;
  for (let page = 0; page < 50; page += 1) {
    let res: {
      data?: {
        items?: PublicArticleSummary[];
        totalItems?: number;
      };
    } | null = null;
    try {
      res = await $fetch(`${base}/api/v1/articles`, {
        query: {
          page,
          size,
          sort: 'latest'
        }
      });
    } catch {
      break;
    }
    const items = res?.data?.items || [];
    for (const item of items) {
      out.push({
        id: item.id,
        title: item.title,
        summary: item.summary,
        categorySlug: item.categorySlug,
        publishedAt: item.publishedAt
      });
    }
    const total = res?.data?.totalItems ?? out.length;
    if (items.length === 0 || out.length >= total) {
      break;
    }
  }
  return out;
}
export function escapeXml(value: string): string {
  return String(value || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;');
}
