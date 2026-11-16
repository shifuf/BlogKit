import request from '@/utils/request';
export function login(data) {
  return request({
    url: '/admin/auth/login',
    method: 'post',
    data
  });
}
export const logout = () => {
  return request.post('/auth/logout');
};
export const setupTotp = code => request.post('/admin/auth/totp/setup', { code });
export const recoveryCodes = code => request.post('/admin/auth/totp/recovery-codes', { code });
export const getTotpStatus = () => request.get('/admin/auth/totp/status');
export const confirmTotp = code => request.post('/admin/auth/totp/confirm', { code });
export const disableTotp = code => request.post('/admin/auth/totp/disable', { code });
export const listSessions = () => request.get('/auth/sessions');
export const revokeSession = id => request.post(`/auth/sessions/${id}/revoke`);
export const revokeOtherSessions = () => request.post('/auth/sessions/revoke-others');
