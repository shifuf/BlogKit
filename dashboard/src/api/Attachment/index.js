import request from '@/utils/request';
export const getAttachmentList = (params = {}) => {
  return request.get('/admin/attachments', {
    params: {
      page: Math.max((params.pageNum || 1) - 1, 0),
      size: params.pageSize || 10,
      keyword: params.keyword || undefined
    }
  });
};
export const deleteAttachment = id => {
  return request.delete(`/admin/attachments/${id}`);
};
export const batchDeleteAttachments = ids => {
  return request.post('/admin/attachments/batch-delete', { ids });
};
