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

  async loadDynamics(append = false) {
    if (this.data.loading) return;
    this.setData({ loading: true });
    try {
      const page = append ? (this.nextPage || 0) : 0;
      const result = await blog.fetchDynamics(page, PAGE_SIZE, this.guestKey);
      const incoming = (result.items || []).map(normalizeDynamic);
      const items = append ? this.data.dynamics.concat(incoming) : incoming;
      this.nextPage = page + 1;
      this.setData({ dynamics: items, all: items, shown: items.length,
        finished: items.length >= result.totalItems, empty: !items.length });
    } catch (error) {
      wx.showToast({ title: (error && error.message) || '加载失败', icon: 'none' });
    } finally { this.setData({ loading: false }); }
  },

  showMore() {
    if (!this.data.finished) return this.loadDynamics(true);
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

  async loadMoreReplies(event) {
    const rootId = Number(event.currentTarget.dataset.root);
    const root = this.data.comments.find(c => c.id === rootId);
    if (!root || root.repliesLoading) return;
    this.setData({ comments: this.data.comments.map(c => c.id === rootId ? { ...c, repliesLoading: true } : c) });
    try {
      const page = root.replyPage || 0;
      const result = await blog.fetchDynamicReplies(this.data.activeId, rootId, page, 10);
      this.setData({ comments: this.data.comments.map(c => c.id === rootId ? { ...c,
        replies: page ? (c.replies || []).concat(result.items) : result.items,
        replyPage: page + 1, replyCount: result.totalItems } : c) });
    } catch (error) { wx.showToast({ title: error.message || '回复加载失败', icon: 'none' }); }
    finally { this.setData({ comments: this.data.comments.map(c => c.id === rootId ? { ...c, repliesLoading: false } : c) }); }
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
