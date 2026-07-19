import request from '@/utils/request';
export function getTagList() {
  return request({
    url: '/tags',
    method: 'get'
  });
}
export const getAdminTagList = () => {
  return request.get('/admin/tags');
};
export const createTag = data => {
  return request.post('/admin/tags', data);
};
export const updateTag = (id, data) => {
  return request.put(`/admin/tags/${id}`, data);
};
export const saveOrUpdateTag = data => {
  return data.id ? updateTag(data.id, data) : createTag(data);
};
export const deleteTag = ids => {
  const targets = Array.isArray(ids) ? ids : [ids];
  return Promise.all(targets.map(id => request.delete(`/admin/tags/${id}`)));
};
