import request from '@/utils/request';
export function getArticleList(params) {
  return request({
    url: '/admin/articles',
    method: 'get',
    params: {
      page: params?.page ?? 0,
      size: params?.size ?? 10,
      status: params?.status || undefined,
      deleted: params?.deleted || false,
      keyword: params?.keyword || undefined,
      categoryId: params?.categoryId || undefined,
      tagId: params?.tagId || undefined,
      visibility: params?.visibility || undefined,
      sticky: params?.sticky
    }
  });
}
export function batchUpdateArticleFlags(ids, data) {
  return request({
    url: '/admin/articles/batch-flags',
    method: 'patch',
    data: { ids, ...data }
  });
}
export function getArticleDetail(id) {
  return request({
    url: `/admin/articles/${id}`,
    method: 'get'
  });
}
export function saveOrUpdateArticle(data) {
  if (data.id) {
    return updateArticle(data.id, data);
  }
  return createArticle(data);
}
export function createArticle(data) {
  return request({
    url: '/admin/articles',
    method: 'post',
    data
  });
}
export function updateArticle(id, data) {
  return request({
    url: `/admin/articles/${id}`,
    method: 'put',
    data
  });
}
// 列表内联快捷修改状态/可见性/评论开关/列表展示/置顶
export function updateArticleFlags(id, data) {
  return request({
    url: `/admin/articles/${id}/flags`,
    method: 'patch',
    data
  });
}
export function exportArticles(ids) {
  return request({
    url: '/admin/articles/export',
    method: 'post',
    data: { ids }
  });
}
export function scanArticleLinks(ids) {
  return request({
    url: '/admin/articles/scan-links',
    method: 'post',
    data: { ids }
  });
}
// 置顶文章批量排序（拖拽后保存，data=[{id, stickyOrder}]）
export function reorderStickyArticles(data) {
  return request({
    url: '/admin/articles/reorder-sticky',
    method: 'put',
    data
  });
}
export function deleteArticle(id) {
  return request({
    url: `/admin/articles/${id}`,
    method: 'delete'
  });
}
export function restoreArticle(id) {
  return request({
    url: `/admin/articles/${id}/restore`,
    method: 'post'
  });
}
export function purgeArticle(id) {
  return request({
    url: `/admin/articles/${id}/purge`,
    method: 'delete'
  });
}
export function listArticleVersions(id) {
  return request({
    url: `/admin/articles/${id}/versions`,
    method: 'get'
  });
}
export function restoreArticleVersion(id, versionId) {
  return request({
    url: `/admin/articles/${id}/versions/${versionId}/restore`,
    method: 'post'
  });
}
export function deleteArticleVersion(id, versionId) {
  return request({
    url: `/admin/articles/${id}/versions/${versionId}`,
    method: 'delete'
  });
}
export function createArticlePreviewLink(id) {
  return request({
    url: `/admin/articles/${id}/preview-link`,
    method: 'post'
  });
}
// 生成/润色 AI 摘要（不落库，返回文本由编辑器接收后随文章保存）。
// data: { title, content, mode: 'generate'|'polish', currentSummary }
export function generateArticleAiSummary(data) {
  return request({
    url: '/admin/articles/ai-summary',
    method: 'post',
    data
  });
}
/** AI 辅助：task=title|tags|seo|summary */
export function generateArticleAiAssist(data) {
  return request({
    url: '/admin/articles/ai-assist',
    method: 'post',
    data
  });
}
export async function batchDeleteArticle(ids) {
  await Promise.all(ids.map(id => deleteArticle(id)));
}
export async function batchPurgeArticle(ids) {
  await Promise.all(ids.map(id => purgeArticle(id)));
}
