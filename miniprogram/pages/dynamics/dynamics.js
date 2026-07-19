const blog = require('../../services/blog');
const { normalizeDynamic, normalizeDynamicComment, normalizeSiteSettings } = require('../../utils/format');
const { getGuestKey } = require('../../utils/guest');

const PAGE_SIZE = 5;
const COMMENT_SIZE = 10;

Page({
  data: {
    all: [],
    dynamics: [],
    shown: 0,
    loading: false,
    finished: false,
    empty: false,
    profileAvatar: '/images/default-avatar.png',
    profileName: '站点管理员',
    profileBio: '',
    dynamicsBackground: '',
    dynamicsSignature: '',
    commentModalVisible: false,
    activeId: null,
    comments: [],
    commentPage: 0,
    commentTotalPages: 0,
    commentTotal: 0,
    commentLoading: false,
    commentContent: '',
    submittingComment: false,
    replyRootId: null,
    replyTargetAuthor: '',
    guestName: '',
    hasSession: false
  },

  onLoad() {
    this.guestKey = getGuestKey();
    this._likeQueue = Promise.resolve();
    this.loadSiteSettings();
    this.loadDynamics();
  },

  onShow() {
    const app = getApp();
    this.setData({ hasSession: app.isLoggedIn() });
  },

  onPullDownRefresh() {
    Promise.all([this.loadSiteSettings(), this.loadDynamics()])
      .finally(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    this.showMore();
  },

  async loadSiteSettings() {
    try {
      const raw = await blog.fetchSiteSettings();
      const s = normalizeSiteSettings(raw);
      this.setData({
        profileAvatar: s.profileAvatar,
        profileName: s.profileName,
        profileBio: s.profileBio,
        dynamicsBackground: s.dynamicsBackground,
        dynamicsSignature: s.dynamicsSignature || s.profileBio || ''
      });
      if (s.siteName) {
        wx.setNavigationBarTitle({ title: s.siteName });
      }
    } catch (error) {
      // 保持默认
    }
  },

  async loadDynamics() {
    if (this.data.loading) {
      return;
    }
    this.setData({ loading: true });
    try {
      const list = await blog.fetchDynamics();
      const all = (list || []).map(normalizeDynamic);
      const shown = Math.min(PAGE_SIZE, all.length);
      this.setData({
        all,
        dynamics: all.slice(0, shown),
        shown,
        finished: shown >= all.length,
        empty: !all.length
      });
      this.hydrateLikes(this.data.dynamics);
    } catch (error) {
      this.setData({ all: [], dynamics: [], empty: true, finished: true });
      wx.showToast({ title: (error && error.message) || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  showMore() {
    if (this.data.finished || this.data.loading) {
      return;
    }
    const shown = Math.min(this.data.shown + PAGE_SIZE, this.data.all.length);
    const nextItems = this.data.all.slice(this.data.dynamics.length, shown);
    this.setData({
      dynamics: this.data.dynamics.concat(nextItems),
      shown,
      finished: shown >= this.data.all.length
    });
    this.hydrateLikes(nextItems);
  },

  // 串行补点赞状态，避免并发打爆连接导致 timeout
  hydrateLikes(items) {
    (items || []).forEach((item) => {
      this._likeQueue = this._likeQueue
        .then(() => blog.fetchDynamicLikeStatus(item.id, this.guestKey))
        .then((res) => {
          if (!res) {
            return;
          }
          const idx = this.data.dynamics.findIndex((d) => d.id === item.id);
          if (idx < 0) {
            return;
          }
          this.setData({
            [`dynamics[${idx}].liked`]: Boolean(res.liked),
            [`dynamics[${idx}].likeCount`]: Number(res.likeCount || 0)
          });
        })
        .catch(() => {});
    });
  },

  // 通过 data-id + data-index 取原图数组，避免 data-urls 数组传参丢失导致预览空白
  previewImage(event) {
    const id = Number(event.currentTarget.dataset.id);
    const index = Number(event.currentTarget.dataset.index);
    const item = this.data.dynamics.find((d) => d.id === id);
    if (!item || !Array.isArray(item.images) || !item.images.length) {
      return;
    }
    const urls = item.images;
    const current = urls[index] || urls[0];
    wx.previewImage({ current, urls });
  },

  async tapLike(event) {
    const id = Number(event.currentTarget.dataset.id);
    const idx = this.data.dynamics.findIndex((d) => d.id === id);
    if (idx < 0) {
      return;
    }
    try {
      const res = await blog.toggleDynamicLike(id, this.guestKey);
      this.setData({
        [`dynamics[${idx}].liked`]: Boolean(res.liked),
        [`dynamics[${idx}].likeCount`]: Number(res.likeCount || 0)
      });
    } catch (error) {
      wx.showToast({ title: (error && error.message) || '操作失败', icon: 'none' });
    }
  },

  openComments(event) {
    const id = Number(event.currentTarget.dataset.id);
    this.setData({
      commentModalVisible: true,
      activeId: id,
      comments: [],
      commentPage: 0,
      commentTotalPages: 0,
      commentTotal: 0,
      commentContent: '',
      replyRootId: null,
      replyTargetAuthor: ''
    });
    this.loadComments(true);
  },

  closeComments() {
    this.setData({ commentModalVisible: false, activeId: null });
  },

  noop() {},

  async loadComments(reset) {
    if (this.data.commentLoading || !this.data.activeId) {
      return;
    }
    const page = reset ? 0 : this.data.commentPage + 1;
    this.setData({ commentLoading: true });
    try {
      const res = await blog.fetchDynamicComments(this.data.activeId, page, COMMENT_SIZE);
      const items = (res.items || []).map(normalizeDynamicComment);
      this.setData({
        comments: reset ? items : this.data.comments.concat(items),
        commentPage: page,
        commentTotalPages: res.totalPages || 0,
        commentTotal: res.totalItems || 0
      });
    } catch (error) {
      if (reset) {
        this.setData({ comments: [] });
      }
    } finally {
      this.setData({ commentLoading: false });
    }
  },

  loadMoreComments() {
    if (this.data.commentPage + 1 < this.data.commentTotalPages) {
      this.loadComments(false);
    }
  },

  onCommentInput(event) {
    this.setData({ commentContent: event.detail.value });
  },

  onGuestNameInput(event) {
    this.setData({ guestName: event.detail.value });
  },

  tapReply(event) {
    const { root, author } = event.currentTarget.dataset;
    const rootId = Number(root);
    if (this.data.replyRootId === rootId && this.data.replyTargetAuthor === author) {
      this.setData({ replyRootId: null, replyTargetAuthor: '' });
      return;
    }
    this.setData({ replyRootId: rootId, replyTargetAuthor: author || '' });
  },

  cancelReply() {
    this.setData({ replyRootId: null, replyTargetAuthor: '' });
  },

  async submitComment() {
    const content = (this.data.commentContent || '').trim();
    if (!content) {
      wx.showToast({ title: '请输入内容', icon: 'none' });
      return;
    }
    if (this.data.submittingComment) {
      return;
    }
    this.setData({ submittingComment: true });
    const app = getApp();
    const session = app.getSession() || {};
    const payload = {
      content,
      guestKey: this.guestKey,
      authorName: session.displayName || session.username || (this.data.guestName || '').trim() || ''
    };
    if (this.data.replyRootId) {
      payload.parentId = this.data.replyRootId;
    }
    try {
      const created = normalizeDynamicComment(await blog.postDynamicComment(this.data.activeId, payload));
      if (this.data.replyRootId) {
        const comments = this.data.comments.map((c) => {
          if (c.id !== this.data.replyRootId) {
            return c;
          }
          const replies = (c.replies || []).concat(created);
          return { ...c, replies, replyCount: (c.replyCount || 0) + 1 };
        });
        this.setData({ comments });
      } else {
        this.setData({ comments: [{ ...created, replies: [] }].concat(this.data.comments) });
      }
      const idx = this.data.dynamics.findIndex((d) => d.id === this.data.activeId);
      if (idx >= 0) {
        this.setData({ [`dynamics[${idx}].commentCount`]: this.data.dynamics[idx].commentCount + 1 });
      }
      this.setData({
        commentContent: '',
        commentTotal: this.data.commentTotal + 1,
        replyRootId: null,
        replyTargetAuthor: ''
      });
      wx.showToast({ title: '已发布', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: (error && error.message) || '发布失败', icon: 'none' });
    } finally {
      this.setData({ submittingComment: false });
    }
  }
});
