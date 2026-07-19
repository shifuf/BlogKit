const { config } = require('./config');

function getSession() {
  return wx.getStorageSync('blog_session') || null;
}

function createHeaders(auth) {
  const headers = {
    'content-type': 'application/json'
  };

  if (auth) {
    const session = getSession();
    if (session && session.tokenValue) {
      headers[session.tokenName || 'Authorization'] = `Bearer ${session.tokenValue}`;
    }
  }

  return headers;
}

function toUrl(path) {
  if (/^https?:\/\//i.test(path)) {
    return path;
  }
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${config.apiBaseUrl}${normalizedPath}`;
}

function isRetriableFail(error) {
  const errMsg = (error && error.errMsg) || '';
  return /timeout|fail|abort|ERR_|ssl|handshake/i.test(errMsg);
}

function request(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const auth = Boolean(options.auth);
  const data = options.data || {};
  // GET 幂等，超时/网络失败时自动重试一次。
  // 首连 TLS 较慢：第一跳 30s，重试 15s。
  const isGet = method === 'GET';
  const baseTimeout = options.timeout || config.requestTimeout || 30000;
  const maxAttempts = isGet ? 2 : 1;

  const attempt = (n, resolve, reject) => {
    wx.request({
      url: toUrl(path),
      method,
      data,
      timeout: n === 0 ? baseTimeout : Math.min(baseTimeout, 15000),
      header: createHeaders(auth),
      success(response) {
        const body = response.data || {};
        const ok = response.statusCode >= 200 && response.statusCode < 300 && body.success !== false;

        if (ok) {
          resolve(options.raw ? body : body.data);
          return;
        }

        if (response.statusCode === 401) {
          wx.removeStorageSync('blog_session');
          const app = getApp();
          if (app && app.globalData) {
            app.globalData.session = null;
          }
        }

        reject({
          statusCode: response.statusCode,
          message: body.message || body.msg || (typeof body === 'string' ? body : '') || `请求失败：${response.statusCode}`
        });
      },
      fail(error) {
        if (n + 1 < maxAttempts && isRetriableFail(error)) {
          attempt(n + 1, resolve, reject);
          return;
        }
        const errMsg = (error && error.errMsg) || '';
        const timeoutMessage = '网络超时，请下拉刷新重试';
        reject({
          statusCode: 0,
          message: /timeout/i.test(errMsg) ? timeoutMessage : errMsg || '网络连接失败'
        });
      }
    });
  };

  return new Promise((resolve, reject) => attempt(0, resolve, reject));
}

module.exports = {
  request
};
