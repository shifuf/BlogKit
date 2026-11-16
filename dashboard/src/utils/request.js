import axios from 'axios';
import { message } from 'ant-design-vue';
import { useUserStore } from '@/stores/modules/user';
import router from '@/router';
const getErrorMessage = error => {
  const data = error.response?.data;
  if (data && typeof data === 'object') {
    return data.message || data.msg || `请求失败：HTTP ${error.response.status}`;
  }
  if (typeof data === 'string' && data.trim()) {
    return data.trim();
  }
  if (error.response?.status) {
    return `请求失败：HTTP ${error.response.status}，请查看后端日志`;
  }
  return error.message || '网络连接失败，请检查后端服务';
};
const service = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
});
service.interceptors.request.use(config => {
  const userStore = useUserStore();
  if (userStore.token) {
    config.headers.Authorization = `${userStore.tokenName || 'Bearer'} ${userStore.token}`;
  }
  return config;
}, error => Promise.reject(error));
service.interceptors.response.use(response => {
  // blob/arraybuffer 等二进制直接返回，供图库代理裁剪使用
  const rt = response.config?.responseType;
  if (rt === 'blob' || rt === 'arraybuffer') {
    return response.data;
  }
  const payload = response.data;
  if (payload && typeof payload.success === 'boolean') {
    if (!payload.success) {
      message.error(payload.message || '请求失败');
      return Promise.reject(new Error(payload.message || '请求失败'));
    }
    return payload.data;
  }
  return payload;
}, error => {
  const status = error.response?.status;
  const errorMsg = getErrorMessage(error);
  if (status === 428 && errorMsg === 'TOTP_REQUIRED') {
    const requiredError = new Error(errorMsg);
    requiredError.code = 'TOTP_REQUIRED';
    return Promise.reject(requiredError);
  }
  if (status === 401 || status === 403) {
    const userStore = useUserStore();
    if (userStore.getIsLoggedIn || status === 401) {
      userStore.logout();
    }
    message.error(status === 401 ? '登录已过期，请重新登录' : errorMsg);
    if (router.currentRoute.value.path !== '/login') {
      router.push('/login');
    }
    return Promise.reject(new Error(errorMsg));
  }
  if (status !== 409) message.error(errorMsg);
  const failure = new Error(errorMsg);
  failure.status = status;
  failure.response = error.response;
  return Promise.reject(failure);
});
export default service;
