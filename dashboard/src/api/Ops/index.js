import request from '@/utils/request';

export const getSiteHealth = () => request.get('/admin/ops/health');
export const getRuntimeMetrics = () => request.get('/admin/ops/metrics');
