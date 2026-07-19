const blog = require('../../services/blog');
const { normalizeArticle } = require('../../utils/format');

Page({
  data: {
    keywordInput: '',
    keyword: '',
    articles: [],
    page: 0,
    size: 10,
    totalPages: 0,
    loading: false,
    finished: false
  },

  onLoad(options) {
    const keyword = decodeURIComponent(options.keyword || options.q || '');
    if (keyword) {
      this.setData({ keywordInput: keyword, keyword });
      this.refresh();
    }
  },

  onKeywordInput(e) {
    this.setData({ keywordInput: e.detail.value });
  },

  clearKeyword() {
    this.setData({ keywordInput: '', keyword: '', articles: [], finished: false });
  },

  submitSearch() {
    const keyword = (this.data.keywordInput || '').trim();
    this.setData({ keyword });
    this.refresh();
  },

  async refresh() {
    if (!this.data.keyword) {
      wx.showToast({ title: '请输入关键词', icon: 'none' });
      return;
    }
    this.setData({ page: 0, articles: [], finished: false });
    await this.loadMore();
  },

  async loadMore() {
    if (this.data.loading || this.data.finished) return;
    this.setData({ loading: true });
    try {
      const page = this.data.page;
      const result = await blog.fetchArticles(page, this.data.size, { keyword: this.data.keyword, sort: 'latest' });
      const items = (result?.items || result?.content || []).map(normalizeArticle);
      const totalPages = Number(result?.totalPages || 0);
      this.setData({
        articles: page === 0 ? items : this.data.articles.concat(items),
        page: page + 1,
        totalPages,
        finished: page + 1 >= totalPages || !items.length
      });
    } catch (error) {
      wx.showToast({ title: (error && error.message) || '搜索失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  onReachBottom() {
    this.loadMore();
  },

  openArticle(e) {
    const id = e.currentTarget.dataset.id;
    if (!id) return;
    wx.navigateTo({ url: `/pages/detail/detail?id=${id}` });
  }
});
