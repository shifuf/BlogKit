import request from '@/utils/request';
export function getMessageList(params = {}) {
  return request({
    url: '/admin/comments',
    method: 'get',
    params: {
      page: Math.max((params.pageNum || 1) - 1, 0),
      size: params.pageSize || 10,
      articleId: params.articleId || undefined,
      parentId: params.parentId || undefined,
      status: params.status || undefined,
      reported: params.reported,
      keyword: params.keyword || undefined
    }
  });
}
export function getCommentSummary() {
  return request({
    url: '/admin/comments/summary',
    method: 'get'
  });
}
export function approveMessage(id) {
  return request({
    url: `/admin/comments/${id}/approve`,
    method: 'post'
  });
}
export function rejectMessage(id) {
  return request({
    url: `/admin/comments/${id}/reject`,
    method: 'post'
  });
}
export function markMessageSpam(id) {
  return request({
    url: `/admin/comments/${id}/spam`,
    method: 'post'
  });
}
export function pinMessage(id) {
  return request({
    url: `/admin/comments/${id}/pin`,
    method: 'post'
  });
}
export function unpinMessage(id) {
  return request({
    url: `/admin/comments/${id}/unpin`,
    method: 'post'
  });
}
export function deleteMessage(id) {
  return request({
    url: `/admin/comments/${id}`,
    method: 'delete'
  });
}
export function batchDeleteMessages(ids) {
  return request({
    url: '/admin/comments/batch-delete',
    method: 'post',
    data: { ids }
  });
}
export function batchUpdateMessageStatus(ids, status) {
  return request({
    url: '/admin/comments/batch-status',
    method: 'post',
    data: { ids, status }
  });
}
export function replyMessage(id, content) {
  return request({
    url: `/admin/comments/${id}/reply`,
    method: 'post',
    data: { content }
  });
}
// 动态评论管理
export function getDynamicCommentList(params = {}) {
  return request({
    url: '/admin/dynamic-comments',
    method: 'get',
    params: {
      page: Math.max((params.pageNum || 1) - 1, 0),
      size: params.pageSize || 10,
      keyword: params.keyword || undefined
    }
  });
}
export function deleteDynamicComment(id) {
  return request({
    url: `/admin/dynamic-comments/${id}`,
    method: 'delete'
  });
}
export function batchDeleteDynamicComments(ids) {
  return request({
    url: '/admin/dynamic-comments/batch-delete',
    method: 'post',
    data: { ids }
  });
}
