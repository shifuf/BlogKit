import request from '@/utils/request';
export const getAuditLogList = (params = {}) => {
  return request.get('/admin/audit-logs', {
    params: {
      page: Math.max((params.pageNum || 1) - 1, 0),
      size: params.pageSize || 10,
      module: params.module || undefined,
      keyword: params.keyword || undefined
    }
  });
};
export const clearAuditLogs = days => {
  return request.delete('/admin/audit-logs', {
    params: days ? {
      days
    } : {}
  });
};
