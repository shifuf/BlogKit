const blog = require('../../services/blog');
const { normalizeArticle, normalizeArticleDetail, normalizeComment } = require('../../utils/format');

Page({
  data: {
    id: null,
    article: null,
    comments: [],
    commentsPage: 0,
    commentsTotal: 0,
    loadingComments: false,
    related: [],
    engagement: {
      liked: false,
      favorited: false
    },
    commentsEnabled: false,
    isLoggedIn: false,
    hasCommented: false,
    commentContent: '',
    // 回复态：activeReplyId 为正在回复的顶级评论 id（null 表示未展开任何回复框）。
    activeReplyId: null,
    replyTargetId: null,
    replyContent: '',
    replyTargetAuthor: '',
    submittingReply: false,
    unlockPassword: '',
    unlocking: false,
    loading: false,
    submittingComment: false,
    likingArticle: false,
    favoritingArticle: false
  },

  onLoad(options) {
    this._unloaded = false;
    this._contextGeneration = (this._contextGeneration || 0) + 1;
    const id = Number(options.id);
    // hasCommented 为「评论可见」隐藏块/下载的游客软门控标记（与前台 localStorage 同思路）。
    this.setData({ id, article: null, comments: [], hasCommented: Boolean(wx.getStorageSync(`bd-commented-${id}`)) });
    this.refreshSession();
    this.loadAll();
  },

  async onShow() {
    const changed = this.refreshSession();
    if (changed && this.data.id) {
      await Promise.all([this.loadArticle(), this.data.commentsEnabled ? this.loadComments() : Promise.resolve()]);
    }
    if (this.data.id && this.data.isLoggedIn) {
      this.loadEngagement();
    }
  },

  onUnload() {
    this._unloaded = true;
    this._contextGeneration = (this._contextGeneration || 0) + 1;
  },

  onPullDownRefresh() {
    this.loadAll().finally(() => wx.stopPullDownRefresh());
  },

  refreshSession() {
    const app = getApp();
    const session = app.getSession() || {};
    const key = `${session.tokenName || 'Authorization'}:${session.tokenValue || ''}`;
    const changed = this._sessionKey !== undefined && this._sessionKey !== key;
    this._sessionKey = key;
    if (changed) {
      this._contextGeneration = (this._contextGeneration || 0) + 1;
      this.setData({ article: null, comments: [], commentsPage: 0, commentsTotal: 0, loadingComments: false,
        loading: false, unlocking: false, unlockPassword: '', activeReplyId: null, replyTargetId: null,
        likingArticle: false, favoritingArticle: false, submittingComment: false, submittingReply: false,
        engagement: { liked: false, favorited: false } });
    }
    this.setData({ isLoggedIn: app.isLoggedIn() });
    return changed;
  },

  requestContext() {
    const session = getApp().getSession() || {};
    return { id: this.data.id, generation: this._contextGeneration || 0,
      token: session.tokenValue || '', tokenName: session.tokenName || 'Authorization' };
  },

  contextIsCurrent(context) {
    const current = this.requestContext();
    return !this._unloaded && context.id === current.id && context.generation === current.generation
      && context.token === current.token && context.tokenName === current.tokenName;
  },

  beginDetailRequest() {
    this._detailGeneration = (this._detailGeneration || 0) + 1;
    return { ...this.requestContext(), detailGeneration: this._detailGeneration };
  },

  detailRequestIsCurrent(context) {
    return context.detailGeneration === this._detailGeneration && this.contextIsCurrent(context);
  },

  async loadAll() {
    if (!this.data.id) {
      return;
    }

    this.setData({ loading: true });
    const context = this.requestContext();
    try {
      await this.loadSiteSettings();
      if (!this.contextIsCurrent(context)) return;
      const tasks = [
        this.loadArticle(),
        this.loadRelated()
      ];
      if (this.data.commentsEnabled) {
        tasks.push(this.loadComments());
      } else {
        this.setData({ comments: [] });
      }
      await Promise.all(tasks);
      if (this.contextIsCurrent(context) && this.data.isLoggedIn) {
        await this.loadEngagement();
      }
    } finally {
      if (this.contextIsCurrent(context)) this.setData({ loading: false });
    }
  },

  async loadArticle() {
    const context = this.beginDetailRequest();
    try {
      const article = await blog.fetchArticleDetail(context.id);
      if (!this.detailRequestIsCurrent(context)) return;
      const normalized = normalizeArticleDetail(article);
      this.setData({ article: normalized });
      wx.setNavigationBarTitle({ title: normalized.title || '文章详情' });
    } catch (error) {
      if (!this.detailRequestIsCurrent(context)) return;
      this.setData({ article: null });
      this.showError(error);
    }
  },

  async loadComments(append = false) {
    if (this.data.loadingComments) return;
    this.setData({ loadingComments: true });
    const context = this.requestContext();
    if (!this.data.commentsEnabled) {
      this.setData({ comments: [], loadingComments: false });
      return;
    }
    try {
      const page = append ? this.data.commentsPage : 0;
      const response = await blog.fetchComments(context.id, page, 20);
      if (!this.contextIsCurrent(context)) return;
      const items = (response.items || []).map(normalizeComment);
      this.setData({
        comments: append ? this.data.comments.concat(items) : items,
        commentsPage: page + 1, commentsTotal: response.totalItems
      });
    } catch (error) {
      if (!this.contextIsCurrent(context)) return;
      if (!append) this.setData({ comments: [] });
      this.showError(error);
    } finally { if (this.contextIsCurrent(context)) this.setData({ loadingComments: false }); }
  },

  loadMoreComments() { return this.loadComments(true); },

  async loadRelated() {
    const context = this.requestContext();
    try {
      const related = await blog.fetchRelatedArticles(context.id, 4);
      if (!this.contextIsCurrent(context)) return;
      this.setData({
        related: (related || []).map(normalizeArticle)
      });
    } catch (error) {
      if (!this.contextIsCurrent(context)) return;
      this.setData({ related: [] });
    }
  },

  async loadSiteSettings() {
    const context = this.requestContext();
    try {
      const settings = await blog.fetchSiteSettings();
      if (!this.contextIsCurrent(context)) return;
      this.setData({ commentsEnabled: Boolean(settings && settings.commentsEnabled === true) });
    } catch (error) {
      if (!this.contextIsCurrent(context)) return;
      this.setData({ commentsEnabled: false });
    }
  },

  async loadEngagement() {
    const context = this.requestContext();
    try {
      const engagement = await blog.fetchFavoriteStatus(context.id);
      if (!this.contextIsCurrent(context)) return;
      this.setData({ engagement });
    } catch (error) {
      if (!this.contextIsCurrent(context)) return;
      this.setData({ engagement: { liked: false, favorited: false } });
    }
  },

  handleCoverError() {
    this.setData({ 'article.coverUrl': '/images/no-cover.png' });
  },

  previewContentImage(event) {
    const current = event.currentTarget.dataset.src;
    const urls = (this.data.article && this.data.article.contentImages) || [];
    if (!current || !urls.length) {
      return;
    }
    wx.previewImage({ current, urls });
  },

  // 图库块：按 block 内 images 原图预览，避免 data 传数组丢失
  previewGalleryImage(event) {
    const bi = Number(event.currentTarget.dataset.bi);
    const index = Number(event.currentTarget.dataset.index);
    const block = this.blockAt(bi);
    if (!block || block.type !== 'gallery' || !Array.isArray(block.images) || !block.images.length) {
      return;
    }
    const urls = block.images;
    const current = urls[index] || urls[0];
    wx.previewImage({ current, urls });
  },

  onCommentInput(event) {
    this.setData({ commentContent: event.detail.value });
  },

  requireLogin() {
    if (this.data.isLoggedIn) {
      return true;
    }
    const redirect = encodeURIComponent(`/pages/detail/detail?id=${this.data.id}`);
    wx.navigateTo({ url: `/pages/login/login?redirect=${redirect}` });
    return false;
  },

  // 访问受限门控
  onUnlockInput(event) {
    this.setData({ unlockPassword: event.detail.value });
  },

  goLoginForArticle() {
    this.requireLogin();
  },

  async submitUnlock() {
    const password = (this.data.unlockPassword || '').trim();
    if (!password || this.data.unlocking) {
      if (!password) {
        wx.showToast({ title: '请输入访问密码', icon: 'none' });
      }
      return;
    }
    this.setData({ unlocking: true });
    this._unlockAttempt = (this._unlockAttempt || 0) + 1;
    const attempt = this._unlockAttempt;
    const context = this.beginDetailRequest();
    try {
      const article = await blog.unlockArticle(context.id, password);
      if (!this.detailRequestIsCurrent(context)) return;
      this.setData({ article: normalizeArticleDetail(article), unlockPassword: '' });
      wx.showToast({ title: '已解锁', icon: 'success' });
    } catch (error) {
      if (!this.detailRequestIsCurrent(context)) return;
      wx.showToast({ title: (error && error.message) || '访问密码错误', icon: 'none' });
    } finally {
      if (attempt === this._unlockAttempt && this.contextIsCurrent(context)) this.setData({ unlocking: false });
    }
  },

  async tapLikeArticle() {
    if (!this.requireLogin() || this.data.likingArticle) {
      return;
    }
    this.setData({ likingArticle: true });
    const context = this.requestContext();
    try {
      const engagement = await blog.likeArticle(context.id);
      if (!this.contextIsCurrent(context)) return;
      this.setData({
        engagement,
        'article.likeCount': engagement.likeCount,
        'article.favoriteCount': engagement.favoriteCount
      });
    } catch (error) {
      if (!this.contextIsCurrent(context)) return;
      this.showError(error);
    } finally {
      if (this.contextIsCurrent(context)) this.setData({ likingArticle: false });
    }
  },

  async tapFavoriteArticle() {
    if (!this.requireLogin() || this.data.favoritingArticle) {
      return;
    }
    this.setData({ favoritingArticle: true });
    const context = this.requestContext();
    try {
      const engagement = await blog.toggleFavorite(context.id);
      if (!this.contextIsCurrent(context)) return;
      this.setData({
        engagement,
        'article.likeCount': engagement.likeCount,
        'article.favoriteCount': engagement.favoriteCount
      });
    } catch (error) {
      if (!this.contextIsCurrent(context)) return;
      this.showError(error);
    } finally {
      if (this.contextIsCurrent(context)) this.setData({ favoritingArticle: false });
    }
  },

  async submitComment() {
    if (!this.data.commentsEnabled) {
      wx.showToast({ title: '评论功能暂未开放', icon: 'none' });
      return;
    }
    if (!this.requireLogin()) {
      return;
    }

    const content = this.data.commentContent.trim();
    if (!content) {
      wx.showToast({ title: '请输入评论内容', icon: 'none' });
      return;
    }

    this.setData({ submittingComment: true });
    const context = this.requestContext();
    try {
      const comment = await blog.createComment(context.id, content);
      if (!this.contextIsCurrent(context)) return;
      const approved = comment.status === 'APPROVED';
      const comments = [normalizeComment(comment)].concat(this.data.comments);
      // 标记已评论：解锁本文「评论后可见」的隐藏块/下载（与前台 localStorage 同思路）。
      if (approved) wx.setStorageSync(`bd-commented-${this.data.id}`, 1);
      this.setData({
        commentContent: '',
        comments,
        hasCommented: approved || this.data.hasCommented,
        commentsTotal: this.data.commentsTotal + (approved ? 1 : 0),
        'article.commentCount': this.data.article.commentCount + (approved ? 1 : 0)
      });
      wx.showToast({ title: approved ? '评论已发布' : '评论已提交，等待审核', icon: 'success' });
      if (approved) await this.loadArticle();
    } catch (error) {
      if (!this.contextIsCurrent(context)) return;
      this.showError(error);
    } finally {
      if (this.contextIsCurrent(context)) this.setData({ submittingComment: false });
    }
  },

  async tapCommentLike(event) {
    if (!this.data.commentsEnabled) {
      return;
    }
    if (!this.requireLogin()) {
      return;
    }
    const commentId = Number(event.currentTarget.dataset.id);
    const context = this.requestContext();
    try {
      const patch = normalizeComment(await blog.toggleCommentLike(commentId));
      if (!this.contextIsCurrent(context)) return;
      const comments = this.data.comments.map((comment) => (comment.id === patch.id ? patch : comment));
      this.setData({ comments });
    } catch (error) {
      if (!this.contextIsCurrent(context)) return;
      this.showError(error);
    }
  },

  // ===== 评论回复（两级扁平：无论回复顶级还是某条回复，都挂到顶级评论 rootId）=====
  // dataset：root 为所在顶级评论 id，author 为被回复者昵称。
  openReply(event) {
    if (!this.data.commentsEnabled || !this.requireLogin()) {
      return;
    }
    const rootId = Number(event.currentTarget.dataset.root);
    const author = event.currentTarget.dataset.author || '';
    const targetId = Number(event.currentTarget.dataset.target || rootId);
    // 再次点同一条已展开的回复框则收起。
    if (this.data.activeReplyId === rootId && this.data.replyTargetId === targetId) {
      this.closeReply();
      return;
    }
    this.setData({ activeReplyId: rootId, replyTargetId: targetId, replyTargetAuthor: author, replyContent: '' });
  },

  closeReply() {
    this.setData({ activeReplyId: null, replyTargetId: null, replyTargetAuthor: '', replyContent: '' });
  },

  onReplyInput(event) {
    this.setData({ replyContent: event.detail.value });
  },

  async submitReply() {
    if (!this.data.commentsEnabled || !this.requireLogin()) {
      return;
    }
    const rootId = this.data.activeReplyId;
    const content = (this.data.replyContent || '').trim();
    if (!rootId) {
      return;
    }
    if (!content) {
      wx.showToast({ title: '请输入回复内容', icon: 'none' });
      return;
    }

    this.setData({ submittingReply: true });
    const context = this.requestContext();
    try {
      const reply = normalizeComment(await blog.createComment(context.id, content, this.data.replyTargetId || rootId));
      if (!this.contextIsCurrent(context)) return;
      const approved = reply.status === 'APPROVED';
      const comments = this.data.comments.map((comment) => {
        if (comment.id !== rootId) {
          return comment;
        }
        const replies = (comment.replies || []).concat(reply);
        return { ...comment, replies, replyCount: (comment.replyCount || 0) + (approved ? 1 : 0) };
      });
      if (approved) wx.setStorageSync(`bd-commented-${this.data.id}`, 1);
      this.setData({
        comments,
        hasCommented: approved || this.data.hasCommented,
        activeReplyId: null,
        replyTargetId: null,
        replyTargetAuthor: '',
        replyContent: '',
        'article.commentCount': this.data.article.commentCount + (approved ? 1 : 0)
      });
      wx.showToast({ title: approved ? '回复已发布' : '回复已提交，等待审核', icon: 'success' });
      if (approved) await this.loadArticle();
    } catch (error) {
      if (!this.contextIsCurrent(context)) return;
      this.showError(error);
    } finally {
      if (this.contextIsCurrent(context)) this.setData({ submittingReply: false });
    }
  },

  async loadMoreReplies(event) {
    const rootId = Number(event.currentTarget.dataset.root);
    const root = this.data.comments.find(c => c.id === rootId);
    if (!root || root.repliesLoading) return;
    const page = root.replyPage || 0;
    const context = this.requestContext();
    this.setData({ comments: this.data.comments.map(c => c.id === rootId ? { ...c, repliesLoading: true } : c) });
    try {
      const result = await blog.fetchCommentReplies(rootId, page, 10);
      if (!this.contextIsCurrent(context)) return;
      const incoming = result.items.map(normalizeComment);
      const existing = page === 0 ? [] : root.replies;
      this.setData({ comments: this.data.comments.map(c => c.id === rootId ? {
        ...c, replies: existing.concat(incoming.filter(r => !existing.some(e => e.id === r.id))),
        replyCount: result.totalItems, replyPage: page + 1
      } : c) });
    } catch (error) { if (this.contextIsCurrent(context)) this.showError(error); }
    finally {
      if (this.contextIsCurrent(context)) this.setData({ comments: this.data.comments.map(c => c.id === rootId ? { ...c, repliesLoading: false } : c) });
    }
  },

  // ===== 自定义区块（下载/按钮/隐藏）交互 =====
  blockAt(index) {
    const blocks = (this.data.article && this.data.article.contentBlocks) || [];
    return blocks[index];
  },

  async tapDownload(event) {
    const bi = Number(event.currentTarget.dataset.bi);
    const di = Number(event.currentTarget.dataset.di);
    const block = this.blockAt(bi);
    if (!block || block.type !== 'download') {
      return;
    }
    // login/comment 门控都需要登录（评论也需先登录）；none 直接放行，最终由后端再校验一次。
    if ((block.gate === 'login' || block.gate === 'comment') && !this.requireLogin()) {
      return;
    }
    try {
      const res = await blog.openDownload(block.token);
      const item = (res.items || [])[di];
      if (!item || !item.url) {
        wx.showToast({ title: '该资源暂无有效链接', icon: 'none' });
        return;
      }
      wx.setClipboardData({
        data: item.url,
        success: () => {
          wx.showModal({
            title: item.name || (block.items[di] && block.items[di].label) || '下载链接',
            content: item.code
              ? `链接已复制，请在浏览器打开。\n提取码：${item.code}`
              : '链接已复制，请在浏览器打开。',
            showCancel: false,
            confirmText: '知道了'
          });
        }
      });
    } catch (error) {
      wx.showToast({
        title: error.message || (block.gate === 'comment' ? '评论后可查看' : '获取下载地址失败'),
        icon: 'none'
      });
    }
  },

  async tapCustomButton(event) {
    const bi = Number(event.currentTarget.dataset.bi);
    const block = this.blockAt(bi);
    if (!block || block.type !== 'button') {
      return;
    }
    if ((block.gate === 'login' || block.gate === 'comment') && !this.requireLogin()) {
      return;
    }
    try {
      const res = await blog.openDownload(block.token);
      const url = res.button && res.button.url;
      if (!url) {
        wx.showToast({ title: '暂无链接', icon: 'none' });
        return;
      }
      wx.setClipboardData({
        data: url,
        success: () => {
          wx.showModal({ title: block.text || '链接', content: '链接已复制，请在浏览器打开。', showCancel: false, confirmText: '知道了' });
        }
      });
    } catch (error) {
      wx.showToast({ title: error.message || '获取失败', icon: 'none' });
    }
  },

  tapUnlock(event) {
    const gate = event.currentTarget.dataset.gate;
    if (gate === 'login') {
      this.requireLogin();
      return;
    }
    if (gate === 'comment') {
      if (!this.requireLogin()) {
        return;
      }
      wx.showToast({ title: '评论后即可查看', icon: 'none' });
    }
  },

  goRelated(event) {
    const id = event.currentTarget.dataset.id;
    wx.redirectTo({ url: `/pages/detail/detail?id=${id}` });
  },

  showError(error) {
    wx.showToast({
      title: error.message || '请求失败',
      icon: 'none'
    });
  }
});
