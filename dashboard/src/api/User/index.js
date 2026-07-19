import request from '@/utils/request';
export const getUserList = params => {
  return request.get('/admin/users', {
    params: {
      page: params?.page ?? 0,
      size: params?.size ?? 10
    }
  });
};
export const getUserById = id => {
  return request.get(`/admin/users/${id}`);
};
export const createUser = data => {
  return request.post('/admin/users', data);
};
export const updateUser = (id, data) => {
  return request.put(`/admin/users/${id}`, data);
};
export const addOrUpdateUser = data => {
  return data.id ? updateUser(data.id, data) : createUser(data);
};
export const resetUserPassword = (userId, password = '123456') => {
  return request.post(`/admin/users/${userId}/reset-password`, {
    password
  });
};
export const deleteUser = id => {
  return request.delete(`/admin/users/${id}`);
};
export const getCurrentUser = () => {
  return request.get('/auth/me');
};
