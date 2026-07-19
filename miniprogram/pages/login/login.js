const blog = require('../../services/blog');
const { config } = require('../../utils/config');

Page({
  data: {
    mode: 'login',
    username: '',
    password: '',
    displayName: '',
    redirect: '',
    allowRegister: true,
    wechatLoading: false,
    loading: false,
    ssoMode: false,
    ssoState: '',
    ssoLoading: false,
    ssoConfirmed: false,
    ssoError: ''
  },

  onLoad(options) {
    const ssoState = this.resolveSsoState(options || {});
    if (ssoState) {
      this.setData({
        ssoMode: true,
        ssoState,
        redirect: '',
        ssoError: ''
      });
      wx.setNavigationBarTitle({ title: '确认授权' });
      return;
    }

    this.setData({ redirect: decodeURIComponent(options.redirect || '') });
    this.loadSiteSettings();
  },

  safeDecode(value) {
    if (!value) {
      return '';
    }
    try {
      return decodeURIComponent(value);
    } catch (error) {
      return value;
    }
  },

  resolveSsoState(options) {
    const state = this.safeDecode(options.state);
    if (state) {
      return state;
    }

    const scene = this.safeDecode(options.scene);
    if (!scene) {
      return '';
    }

    const params = scene.split('&').reduce((result, pair) => {
      const index = pair.indexOf('=');
      if (index <= 0) {
        return result;
      }
      result[pair.slice(0, index)] = this.safeDecode(pair.slice(index + 1));
      return result;
    }, {});

    return params.s || params.state || scene;
  },

  async loadSiteSettings() {
    try {
      const settings = await blog.fetchSiteSettings();
      this.setData({ allowRegister: settings.allowRegister !== false });
    } catch (error) {
      this.setData({ allowRegister: true });
    }
  },

  switchMode(event) {
    const mode = event.currentTarget.dataset.mode;
    if (mode === 'register' && !this.data.allowRegister) {
      wx.showToast({ title: '当前站点不允许注册', icon: 'none' });
      return;
    }
    this.setData({ mode });
    wx.setNavigationBarTitle({ title: mode === 'login' ? '登录' : '注册' });
  },

  onUsernameInput(event) {
    this.setData({ username: event.detail.value });
  },

  onPasswordInput(event) {
    this.setData({ password: event.detail.value });
  },

  onDisplayNameInput(event) {
    this.setData({ displayName: event.detail.value });
  },

  validate() {
    if (!this.data.username.trim()) {
      wx.showToast({ title: '请输入用户名', icon: 'none' });
      return false;
    }
    if (!this.data.password) {
      wx.showToast({ title: '请输入密码', icon: 'none' });
      return false;
    }
    if (this.data.mode === 'register' && !this.data.displayName.trim()) {
      wx.showToast({ title: '请输入昵称', icon: 'none' });
      return false;
    }
    return true;
  },

  async submit() {
    if (!this.validate() || this.data.loading) {
      return;
    }

    this.setData({ loading: true });
    try {
      const payload = {
        username: this.data.username.trim(),
        password: this.data.password
      };
      const session = this.data.mode === 'login'
        ? await blog.login(payload)
        : await blog.register({ ...payload, displayName: this.data.displayName.trim() });

      getApp().setSession(session);
      wx.showToast({ title: '已登录', icon: 'success' });
      setTimeout(() => this.afterLogin(), 350);
    } catch (error) {
      wx.showToast({
        title: error.message || '登录失败',
        icon: 'none'
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  async loginWithWechat() {
    if (this.data.wechatLoading) {
      return;
    }

    this.setData({ wechatLoading: true });
    try {
      const profile = await this.tryGetWechatProfile();
      const loginResult = await this.wxLogin();
      const session = await blog.loginWithWechatMiniProgram({
        code: loginResult.code,
        nickname: profile.nickname,
        avatarUrl: profile.avatarUrl
      });
      getApp().setSession(session);
      wx.showToast({ title: '已登录', icon: 'success' });
      setTimeout(() => this.afterLogin(), 350);
    } catch (error) {
      wx.showToast({
        title: error.message || '微信登录失败',
        icon: 'none'
      });
    } finally {
      this.setData({ wechatLoading: false });
    }
  },

  tryGetWechatProfile() {
    return new Promise((resolve) => {
      if (!wx.getUserProfile) {
        resolve({});
        return;
      }
      wx.getUserProfile({
        desc: '用于完善博客账号资料',
        success: (result) => {
          const userInfo = result.userInfo || {};
          resolve({
            nickname: userInfo.nickName || '',
            avatarUrl: userInfo.avatarUrl || ''
          });
        },
        fail: () => resolve({})
      });
    });
  },

  wxLogin() {
    return new Promise((resolve, reject) => {
      wx.login({
        success: (result) => {
          if (result.code) {
            resolve(result);
            return;
          }
          reject(new Error('微信未返回登录凭证'));
        },
        fail: () => reject(new Error('无法调用微信登录'))
      });
    });
  },

  confirmSsoLogin() {
    if (this.data.ssoLoading || this.data.ssoConfirmed) {
      return;
    }

    if (!this.data.ssoState) {
      wx.showToast({ title: '缺少登录参数，请重新扫码', icon: 'none' });
      return;
    }

    this.setData({ ssoLoading: true, ssoError: '' });
    this.wxLogin()
      .then((loginResult) => this.postSsoConfirm(loginResult.code))
      .then(() => {
        this.setData({ ssoConfirmed: true });
        wx.showToast({ title: '已确认授权', icon: 'success' });
      })
      .catch((error) => {
        const message = error.message || '确认登录失败，请重试';
        this.setData({ ssoError: message });
        wx.showToast({ title: message, icon: 'none' });
      })
      .finally(() => {
        this.setData({ ssoLoading: false });
      });
  },

  postSsoConfirm(jsCode) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: config.ssoConfirmUrl,
        method: 'POST',
        timeout: config.requestTimeout || 12000,
        data: {
          state: this.data.ssoState,
          jsCode
        },
        header: {
          'content-type': 'application/json'
        },
        success(response) {
          const body = response.data || {};
          const ok = response.statusCode >= 200 && response.statusCode < 300 && body.success !== false;
          if (ok) {
            resolve(body.data || body);
            return;
          }
          reject(new Error(body.message || body.msg || `确认失败：${response.statusCode}`));
        },
        fail(error) {
          const errMsg = error.errMsg || '';
          reject(new Error(/timeout/i.test(errMsg) ? '确认登录超时，请检查认证中心地址' : errMsg || '无法连接认证中心'));
        }
      });
    });
  },

  afterLogin() {
    const redirect = this.data.redirect;
    if (redirect && redirect.includes('/pages/me/me')) {
      wx.switchTab({ url: '/pages/me/me' });
      return;
    }
    if (redirect) {
      wx.redirectTo({ url: redirect });
      return;
    }
    wx.switchTab({ url: '/pages/me/me' });
  }
});
