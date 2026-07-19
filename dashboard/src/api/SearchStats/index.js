import request from '@/utils/request';

export function getSearchStats(params = {}) {
  return request({
    url: '/admin/search-stats',
    method: 'get',
    params: {
      page: Math.max((params.pageNum || 1) - 1, 0),
      size: params.pageSize || 10,
      keyword: params.keyword || undefined
    }
  });
}
