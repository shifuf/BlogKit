const blog = require('../../services/blog');
const { normalizeArticle } = require('../../utils/format');

const BASE_SORT_OPTIONS = [
  { label: '最新', value: 'latest' },
  { label: '阅读', value: 'views' },
  { label: '点赞', value: 'likes' }
];
const COMMENT_SORT_OPTION = { label: '评论', value: 'comments' };

Page({
  data: {
    settings: {},
    commentsEnabled: false,
    categories: [{ id: 0, name: '全部', slug: '' }],
    articles: [],
    keywordInput: '',
    keyword: '',
    selectedCategorySlug: '',
    sort: 'latest',
    sortOptions: BASE_SORT_OPTIONS,
    skeletonItems: [1, 2, 3],
    page: 0,
    size: 10,
    totalPages: 0,
    loading: false,
    finished: false
  },

  onLoad() {
    this.loadSiteSettings();
    this.loadCategories();
    this.refreshArticles();
  },

  onPullDownRefresh() {
    this.refreshArticles().finally(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    this.loadMoreArticles();
  },

  async loadSiteSettings() {
    try {
      const settings = await blog.fetchSiteSettings();
      const siteSettings = settings || {};
      const commentsEnabled = siteSettings.commentsEnabled === true;
      this.setData({
        settings: siteSettings,
        commentsEnabled,
        sortOptions: commentsEnabled ? BASE_SORT_OPTIONS.concat(COMMENT_SORT_OPTION) : BASE_SORT_OPTIONS,
        sort: commentsEnabled || this.data.sort !== 'comments' ? this.data.sort : 'latest'
      });
      wx.setNavigationBarTitle({ title: siteSettings.siteName || '博客' });
    } catch (error) {
      this.showError(error);
    }
  },

  async loadCategories() {
    try {
      const categories = await blog.fetchCategories();
      this.setData({
        categories: [{ id: 0, name: '全部', slug: '' }].concat(categories || [])
      });
    } catch (error) {
      this.showError(error);
    }
  },

  buildFilters() {
    const filters = {
      sort: this.data.commentsEnabled || this.data.sort !== 'comments' ? this.data.sort : 'latest'
    };
    if (this.data.keyword) {
      filters.keyword = this.data.keyword;
    }
    if (this.data.selectedCategorySlug) {
      filters.categorySlug = this.data.selectedCategorySlug;
    }
    return filters;
  },

  async refreshArticles() {
    if (this.data.loading) {
      return;
    }

    this.setData({ loading: true, page: 0, finished: false });
    try {
      const response = await blog.fetchArticles(0, this.data.size, this.buildFilters());
      const items = (response.items || []).map(normalizeArticle);
      this.setData({
        articles: items,
        totalPages: response.totalPages || 0,
        finished: !items.length || response.page + 1 >= response.totalPages
      });
    } catch (error) {
      this.setData({ articles: [], finished: true });
      this.showError(error);
    } finally {
      this.setData({ loading: false });
    }
  },

  async loadMoreArticles() {
    if (this.data.loading || this.data.finished) {
      return;
    }

    const nextPage = this.data.page + 1;
    this.setData({ loading: true });
    try {
      const response = await blog.fetchArticles(nextPage, this.data.size, this.buildFilters());
      const items = (response.items || []).map(normalizeArticle);
      this.setData({
        page: nextPage,
        articles: this.data.articles.concat(items),
        totalPages: response.totalPages || 0,
        finished: !items.length || response.page + 1 >= response.totalPages
      });
    } catch (error) {
      this.showError(error);
    } finally {
      this.setData({ loading: false });
    }
  },

  onKeywordInput(event) {
    this.setData({ keywordInput: event.detail.value });
  },

  submitSearch() {
    const keyword = this.data.keywordInput.trim();
    if (!keyword) {
      this.setData({ keyword: '' });
      this.refreshArticles();
      return;
    }
    wx.navigateTo({ url: `/pages/search/search?keyword=${encodeURIComponent(keyword)}` });
  },

  clearKeyword() {
    if (!this.data.keywordInput && !this.data.keyword) {
      return;
    }
    this.setData({ keywordInput: '', keyword: '' });
    this.refreshArticles();
  },

  selectCategory(event) {
    const slug = event.currentTarget.dataset.slug || '';
    const name = event.currentTarget.dataset.name || '';
    if (!slug) {
      this.setData({ selectedCategorySlug: '' });
      this.refreshArticles();
      return;
    }
    wx.navigateTo({
      url: `/pages/category/category?slug=${encodeURIComponent(slug)}&name=${encodeURIComponent(name || slug)}`
    });
  },

  selectSort(event) {
    const sort = event.currentTarget.dataset.value || 'latest';
    if (sort === this.data.sort) {
      return;
    }
    this.setData({ sort });
    this.refreshArticles();
  },

  handleCoverError(event) {
    const id = Number(event.currentTarget.dataset.id);
    const articles = this.data.articles.map((article) => {
      if (article.id === id) {
        return { ...article, coverUrl: '/images/no-cover.png' };
      }
      return article;
    });
    this.setData({ articles });
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
