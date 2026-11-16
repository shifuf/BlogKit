const blog = require('../../services/blog');
const { normalizeArticle } = require('../../utils/format');

Page({
  data: {
    slug: '',
    name: '',
    categories: [],
    articles: [],
    page: 0,
    size: 10,
    totalPages: 0,
    loading: false,
    finished: false
  },

  onLoad(options) {
    const slug = options.slug || '';
    const name = decodeURIComponent(options.name || '');
    this.setData({ slug, name });
    if (name) wx.setNavigationBarTitle({ title: name });
    this.loadCategories();
    this.refresh();
  },

  async loadCategories() {
    try {
      const categories = await blog.fetchCategories();
      this.setData({ categories: categories || [] });
    } catch (_) {}
  },

  selectCategory(e) {
    const slug = e.currentTarget.dataset.slug || '';
    const name = e.currentTarget.dataset.name || '';
    this.setData({ slug, name });
    if (name) wx.setNavigationBarTitle({ title: name });
    this.refresh();
  },

  async refresh() {
    this.requestGeneration = (this.requestGeneration || 0) + 1;
    this.setData({ loading: false });
    this.setData({ page: 0, articles: [], finished: false });
    await this.loadMore();
  },

  async loadMore() {
    if (this.data.loading || this.data.finished) return;
    this.setData({ loading: true });
    const generation = this.requestGeneration || 0;
    try {
      const page = this.data.page;
      const filters = { sort: 'latest' };
      if (this.data.slug) filters.categorySlug = this.data.slug;
      const result = await blog.fetchArticles(page, this.data.size, filters);
      if (generation !== (this.requestGeneration || 0)) return;
      const items = (result?.items || result?.content || []).map(normalizeArticle);
      const totalPages = Number(result?.totalPages || 0);
      this.setData({
        articles: page === 0 ? items : this.data.articles.concat(items),
        page: page + 1,
        totalPages,
        finished: page + 1 >= totalPages || !items.length
      });
    } catch (error) {
      if (generation !== (this.requestGeneration || 0)) return;
      wx.showToast({ title: (error && error.message) || '加载失败', icon: 'none' });
    } finally {
      if (generation === (this.requestGeneration || 0)) this.setData({ loading: false });
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
