const blog = require('../../services/blog');
const { normalizeArticle, normalizeArticleDetail, normalizeComment } = require('../../utils/format');

Page({
  data: {
    id: null,
    article: null,
    comments: [],
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
    const id = Number(options.id);
    // hasCommented 为「评论可见」隐藏块/下载的游客软门控标记（与前台 localStorage 同思路）。
    this.setData({ id, hasCommented: Boolean(wx.getStorageSync(`bd-commented-${id}`)) });
    this.refreshSession();
    this.loadAll();
  },

  onShow() {
    this.refreshSession();
    if (this.data.id && this.data.isLoggedIn) {
      this.loadEngagement();
    }
  },

  onPullDownRefresh() {
    this.loadAll().finally(() => wx.stopPullDownRefresh());
  },

  refreshSession() {
    const app = getApp();
    this.setData({ isLoggedIn: app.isLoggedIn() });
  },

  async loadAll() {
    if (!this.data.id) {
      return;
    }

    this.setData({ loading: true });
    try {
      await this.loadSiteSettings();
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
      if (this.data.isLoggedIn) {
        await this.loadEngagement();
      }
    } finally {
      this.setData({ loading: false });
    }
  },

  async loadArticle() {
    try {
      const article = await blog.fetchArticleDetail(this.data.id);
      const normalized = normalizeArticleDetail(article);
      this.setData({ article: normalized });
      wx.setNavigationBarTitle({ title: normalized.title || '文章详情' });
    } catch (error) {
      this.setData({ article: null });
      this.showError(error);
    }
  },

  async loadComments() {
    if (!this.data.commentsEnabled) {
      this.setData({ comments: [] });
      return;
    }
    try {
      const response = await blog.fetchComments(this.data.id, 0, 20);
      this.setData({
        comments: (response.items || []).map(normalizeComment)
      });
    } catch (error) {
      this.setData({ comments: [] });
    }
  },

  async loadRelated() {
    try {
      const related = await blog.fetchRelatedArticles(this.data.id, 4);
      this.setData({
        related: (related || []).map(normalizeArticle)
      });
    } catch (error) {
      this.setData({ related: [] });
    }
  },

  async loadSiteSettings() {
    try {
      const settings = await blog.fetchSiteSettings();
      this.setData({ commentsEnabled: Boolean(settings && settings.commentsEnabled === true) });
    } catch (error) {
      this.setData({ commentsEnabled: false });
    }
  },

  async loadEngagement() {
    try {
      const engagement = await blog.fetchFavoriteStatus(this.data.id);
      this.setData({ engagement });
    } catch (error) {
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
    try {
      const article = await blog.unlockArticle(this.data.id, password);
      this.setData({ article: normalizeArticleDetail(article), unlockPassword: '' });
      wx.showToast({ title: '已解锁', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: (error && error.message) || '访问密码错误', icon: 'none' });
    } finally {
      this.setData({ unlocking: false });
    }
  },

  async tapLikeArticle() {
    if (!this.requireLogin() || this.data.likingArticle) {
      return;
    }
    this.setData({ likingArticle: true });
    try {
      const engagement = await blog.likeArticle(this.data.id);
      this.setData({
        engagement,
        'article.likeCount': engagement.likeCount,
        'article.favoriteCount': engagement.favoriteCount
      });
    } catch (error) {
      this.showError(error);
    } finally {
      this.setData({ likingArticle: false });
    }
  },

  async tapFavoriteArticle() {
    if (!this.requireLogin() || this.data.favoritingArticle) {
      return;
    }
    this.setData({ favoritingArticle: true });
    try {
      const engagement = await blog.toggleFavorite(this.data.id);
      this.setData({
        engagement,
        'article.likeCount': engagement.likeCount,
        'article.favoriteCount': engagement.favoriteCount
      });
    } catch (error) {
      this.showError(error);
    } finally {
      this.setData({ favoritingArticle: false });
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
    try {
      const comment = await blog.createComment(this.data.id, content);
      const comments = [normalizeComment(comment)].concat(this.data.comments);
      // 标记已评论：解锁本文「评论后可见」的隐藏块/下载（与前台 localStorage 同思路）。
      wx.setStorageSync(`bd-commented-${this.data.id}`, 1);
      this.setData({
        commentContent: '',
        comments,
        hasCommented: true,
        'article.commentCount': this.data.article.commentCount + 1
      });
      wx.showToast({ title: '评论已发布', icon: 'success' });
    } catch (error) {
      this.showError(error);
    } finally {
      this.setData({ submittingComment: false });
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
    try {
      const patch = normalizeComment(await blog.toggleCommentLike(commentId));
      const comments = this.data.comments.map((comment) => (comment.id === patch.id ? patch : comment));
      this.setData({ comments });
    } catch (error) {
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
    // 再次点同一条已展开的回复框则收起。
    if (this.data.activeReplyId === rootId && this.data.replyTargetAuthor === author) {
      this.closeReply();
      return;
    }
    this.setData({ activeReplyId: rootId, replyTargetAuthor: author, replyContent: '' });
  },

  closeReply() {
    this.setData({ activeReplyId: null, replyTargetAuthor: '', replyContent: '' });
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
    try {
      const reply = normalizeComment(await blog.createComment(this.data.id, content, rootId));
      const comments = this.data.comments.map((comment) => {
        if (comment.id !== rootId) {
          return comment;
        }
        const replies = (comment.replies || []).concat(reply);
        return { ...comment, replies, replyCount: (comment.replyCount || 0) + 1 };
      });
      wx.setStorageSync(`bd-commented-${this.data.id}`, 1);
      this.setData({
        comments,
        hasCommented: true,
        activeReplyId: null,
        replyTargetAuthor: '',
        replyContent: '',
        'article.commentCount': this.data.article.commentCount + 1
      });
      wx.showToast({ title: '回复已发布', icon: 'success' });
    } catch (error) {
      this.showError(error);
    } finally {
      this.setData({ submittingReply: false });
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
