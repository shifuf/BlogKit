import request from '@/utils/request';
export const listDynamics = () => request.get('/admin/dynamics');
export const createDynamic = data => request.post('/admin/dynamics', data);
export const updateDynamic = (id, data) => request.put(`/admin/dynamics/${id}`, data);
export const deleteDynamic = id => request.delete(`/admin/dynamics/${id}`);
export const getServerTime = () => request.get('/admin/server-time');
