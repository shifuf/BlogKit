const useLocal = false;
const productionOrigin = 'https://example.com';
const localBackendOrigin = 'http://127.0.0.1:8080';
const localWebOrigin = 'http://127.0.0.1:3256';
const ssoOrigin = 'https://sso.example.com';

const backendOrigin = useLocal ? localBackendOrigin : productionOrigin;
const webOrigin = useLocal ? localWebOrigin : productionOrigin;

const config = {
  backendOrigin,
  webOrigin,
  apiBaseUrl: `${backendOrigin}/api/v1`,
  requestTimeout: 30000,
  ssoOrigin,
  ssoConfirmUrl: `${ssoOrigin}/api/auth/wechat-mini/login-confirm`
};

module.exports = {
  config
};
