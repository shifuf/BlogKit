import request from '@/utils/request';
export const listFriendLinks = () => request.get('/admin/friend-links');
export const createFriendLink = data => request.post('/admin/friend-links', data);
export const updateFriendLink = (id, data) => request.put(`/admin/friend-links/${id}`, data);
export const deleteFriendLink = id => request.delete(`/admin/friend-links/${id}`);
export const reviewFriendLink = (id, approve) => request.post(`/admin/friend-links/${id}/review`, { approve });
export const recheckFriendLink = id => request.post(`/admin/friend-links/${id}/recheck`);
