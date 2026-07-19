const blog = require('../../services/blog');
const { formatDate, normalizeArticle, resolveAvatar, normalizeSiteSettings } = require('../../utils/format');

Page({
  data: {
    isLoggedIn: false,
    session: {},
    profile: {},
    avatarUrl: '/images/default-avatar.png',
    favorites: [],
    comments: [],
    commentsEnabled: false,
    loading: false,
    siteName: '博客',
    profileName: '站点管理员',
    profileAvatar: '/images/default-avatar.png',
    profileBio: ''
  },

  onShow() {
    this.refreshSession();
    this.loadSiteSettings();
    if (this.data.isLoggedIn) {
      this.loadDashboard();
    }
  },

  onPullDownRefresh() {
    const tasks = [this.loadSiteSettings()];
    if (this.data.isLoggedIn) {
      tasks.push(this.loadDashboard());
    }
    Promise.all(tasks).finally(() => wx.stopPullDownRefresh());
  },

  refreshSession() {
    const app = getApp();
    const session = app.getSession() || {};
    this.setData({
      isLoggedIn: app.isLoggedIn(),
      session,
      avatarUrl: resolveAvatar(session.avatarUrl || session.avatar)
    });
  },

  async loadSiteSettings() {
    try {
      const raw = await blog.fetchSiteSettings();
      const s = normalizeSiteSettings(raw);
      this.setData({
        siteName: s.siteName || '博客',
        profileName: s.profileName,
        profileAvatar: s.profileAvatar,
        profileBio: s.profileBio,
        commentsEnabled: Boolean(s.commentsEnabled === true)
      });
    } catch (error) {
      this.setData({ commentsEnabled: false });
    }
  },

  async loadDashboard() {
    this.setData({ loading: true });
    try {
      const tasks = [this.loadProfile(), this.loadFavorites()];
      if (this.data.commentsEnabled) {
        tasks.push(this.loadComments());
      } else {
        this.setData({ comments: [] });
      }
      await Promise.all(tasks);
    } finally {
      this.setData({ loading: false });
    }
  },

  async loadProfile() {
    try {
      const profile = await blog.fetchProfile();
      this.setData({
        profile: profile || {},
        avatarUrl: resolveAvatar((profile && profile.avatarUrl) || this.data.session.avatarUrl || this.data.session.avatar)
      });
    } catch (error) {
      this.showError(error);
    }
  },

  async loadFavorites() {
    try {
      const response = await blog.fetchMyFavoriteArticles(0, 8);
      this.setData({
        favorites: (response.items || []).map(normalizeArticle)
      });
    } catch (error) {
      this.setData({ favorites: [] });
    }
  },

  async loadComments() {
    if (!this.data.commentsEnabled) {
      this.setData({ comments: [] });
      return;
    }
    try {
      const response = await blog.fetchMyComments(0, 5);
      const comments = (response.items || []).map((item) => ({
        ...item,
        displayTime: formatDate(item.createdAt)
      }));
      this.setData({ comments });
    } catch (error) {
      this.setData({ comments: [] });
    }
  },

  goLogin() {
    const redirect = encodeURIComponent('/pages/me/me');
    wx.navigateTo({ url: `/pages/login/login?redirect=${redirect}` });
  },

  goArticles() {
    wx.switchTab({ url: '/pages/index/index' });
  },

  goDynamics() {
    wx.switchTab({ url: '/pages/dynamics/dynamics' });
  },

  confirmLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定退出当前账号吗？',
      confirmText: '退出',
      confirmColor: '#dc2626',
      success: async (result) => {
        if (!result.confirm) {
          return;
        }
        await this.logout();
      }
    });
  },

  async logout() {
    try {
      await blog.logout();
    } catch (error) {
      // 本地登录态必须清理
    }
    getApp().clearSession();
    this.setData({
      isLoggedIn: false,
      session: {},
      profile: {},
      avatarUrl: '/images/default-avatar.png',
      favorites: [],
      comments: []
    });
  },

  goDetail(event) {
    const id = event.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/detail/detail?id=${id}` });
  },

  showError(error) {
    wx.showToast({
      title: error.message || '请求失败',
      icon: 'none'
    });
  }
});
