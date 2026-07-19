import request from '@/utils/request';
export function getCategoryList() {
  return request({
    url: '/categories',
    method: 'get'
  });
}
export const getAdminCategoryList = () => {
  return request.get('/admin/categories');
};
export const createCategory = data => {
  return request.post('/admin/categories', data);
};
export const updateCategory = (id, data) => {
  return request.put(`/admin/categories/${id}`, data);
};
export const saveOrUpdateCategory = data => {
  return data.id ? updateCategory(data.id, data) : createCategory(data);
};
export const reorderCategories = data => {
  return request.put('/admin/categories/reorder', data);
};
export const deleteCategory = ids => {
  const targets = Array.isArray(ids) ? ids : [ids];
  return Promise.all(targets.map(id => request.delete(`/admin/categories/${id}`)));
};
