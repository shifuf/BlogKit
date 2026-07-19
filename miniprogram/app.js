App({
  globalData: {
    session: null,
    settings: null
  },

  onLaunch() {
    this.globalData.session = wx.getStorageSync('blog_session') || null;
  },

  getSession() {
    if (!this.globalData.session) {
      this.globalData.session = wx.getStorageSync('blog_session') || null;
    }
    return this.globalData.session;
  },

  setSession(session) {
    this.globalData.session = session;
    wx.setStorageSync('blog_session', session);
  },

  clearSession() {
    this.globalData.session = null;
    wx.removeStorageSync('blog_session');
  },

  isLoggedIn() {
    const session = this.getSession();
    return Boolean(session && session.tokenValue);
  }
});
