<script setup lang="ts">
import type { Article, ArticleSort, CategorySummary } from '~/types/blog';
import { toBlogArticle } from '~/utils/blog-adapter';
const {
  fetchArticleSummaries
} = useApiClient();
const { siteSettings, loadSiteSettings } = useSiteSettings();
const {
  data: publicCategories
} = await usePublicCategories();
await loadSiteSettings();
const pageSize = 5;
// Tab 类型：value=latest/hot 走对应排序，其它视为分类 slug；icon 用于 NavSvgIcon。
type HomeTab = { label: string; value: string; icon: string; sort?: ArticleSort };
// 默认首个可用 Tab 作为初始激活项（最新/最热都关闭时，会落到第一个分类）。
const resolveDefaultTab = () => {
  if (siteSettings.value?.homeTabLatestEnabled !== false) return 'latest';
  if (siteSettings.value?.homeTabHotEnabled === true) return 'hot';
  return 'latest';
};
const activeTab = ref(resolveDefaultTab());
const loadedPages = ref(1);
const loading = ref(false);
let articleRequestController: AbortController | null = null;
// 首页文章板块：
// - home.tabLatestEnabled：是否显示「最新文章」Tab（默认开启）
// - home.tabHotEnabled：是否显示「最热文章」Tab（按访问量，默认关闭）
// - home.tabCategorySlugs：自定义分类栏目（逗号分隔的分类 slug），留空则自动取前两个一级导航分类
const buildTabs = (categories: CategorySummary[]): HomeTab[] => {
  const base: HomeTab[] = [];
  if (siteSettings.value?.homeTabLatestEnabled !== false) {
    base.push({ label: '最新文章', value: 'latest', icon: 'TrendingUp', sort: 'latest' });
  }
  if (siteSettings.value?.homeTabHotEnabled === true) {
    base.push({ label: '最热文章', value: 'hot', icon: 'Whatshot', sort: 'views' });
  }
  const configured = String(siteSettings.value?.homeTabCategorySlugs || '')
    .split(',')
    .map(slug => slug.trim())
    .filter(Boolean);
  const picked = configured.length
    ? configured
        .map(slug => categories.find(category => category.slug === slug))
        .filter((category): category is CategorySummary => Boolean(category))
    : categories.filter(category => category.navVisible !== false && !category.parentId).slice(0, 2);
  return [
    ...base,
    ...picked.map(category => ({
      label: category.name,
      value: category.slug,
      icon: category.iconCode || 'Inventory2'
    }))
  ];
};
const currentTabSort = (): ArticleSort => {
  const tab = tabs.value.find(item => item.value === activeTab.value);
  return tab?.sort || 'latest';
};
const isPresetTab = (value: string) => value === 'latest' || value === 'hot';
const {
  data: initialHome
} = await useAsyncData('home-page-articles', async () => {
  const articleResponse = await fetchArticleSummaries(0, pageSize, {
    sort: 'latest'
  });
  return {
    articles: articleResponse.success ? articleResponse.data.items.map(toBlogArticle) : [],
    totalItems: articleResponse.success ? articleResponse.data.totalItems : 0
  };
}, {
  default: () => ({
    articles: [] as Article[],
    totalItems: 0
  })
});
const tabs = computed(() => buildTabs(publicCategories.value || []));
const articleItems = ref<Article[]>(initialHome.value.articles);
const totalItems = ref(initialHome.value.totalItems);
const heroArticles = computed(() => articleItems.value);
const visibleArticles = computed(() => articleItems.value);
const listTotal = computed(() => totalItems.value);
const showLoadMore = computed(() => listTotal.value > pageSize);
const hasMoreArticles = computed(() => articleItems.value.length < listTotal.value);
const buildFilters = () => ({
  ...(isPresetTab(activeTab.value) ? {} : { categorySlug: activeTab.value }),
  sort: currentTabSort()
});
const reloadArticles = async () => {
  articleRequestController?.abort();
  articleRequestController = new AbortController();
  const requestController = articleRequestController;
  loading.value = true;
  loadedPages.value = 1;
  try {
    const response = await fetchArticleSummaries(0, pageSize, buildFilters(), requestController.signal);
    articleItems.value = response.success ? response.data.items.map(toBlogArticle) : [];
    totalItems.value = response.success ? response.data.totalItems : 0;
  } catch (error) {
    if ((error as { name?: string })?.name === 'AbortError') return;
    articleItems.value = [];
    totalItems.value = 0;
  } finally {
    if (articleRequestController === requestController) loading.value = false;
  }
};
const loadMore = async () => {
  if (loading.value || !hasMoreArticles.value) return;
  loading.value = true;
  try {
    const nextPage = loadedPages.value;
    const response = await fetchArticleSummaries(nextPage, pageSize, buildFilters());
    if (response.success) {
      const seen = new Set(articleItems.value.map(article => article.slug));
      const appended = response.data.items.map(toBlogArticle).filter(article => !seen.has(article.slug));
      articleItems.value = [...articleItems.value, ...appended];
      totalItems.value = response.data.totalItems;
      loadedPages.value = nextPage + 1;
    }
  } finally {
    loading.value = false;
  }
};
watch(activeTab, async () => {
  await reloadArticles();
});
// tabs 变化时，若当前激活 Tab 已被移除，落到第一个可用 Tab。
watch(tabs, items => {
  if (!items.length) return;
  if (!items.some(item => item.value === activeTab.value)) {
    activeTab.value = items[0]!.value;
  }
});

</script>

<template>
  <BlogPageLayout>
    <BlogHeroShowcase :articles="heroArticles" />

    <section class="main-article-list main-article-list--cards">
      <div v-if="siteSettings.homeArticleListEnabled !== false" class="article-tabs article-tabs--right">
        <button v-for="tab in tabs" :key="tab.value" class="article-tab" :class="{ active: activeTab === tab.value }" type="button" @click="activeTab = tab.value">
          {{ tab.label }}
        </button>
      </div>

      <ElSkeleton v-if="loading && !visibleArticles.length" :rows="6" animated />

      <ElEmpty v-else-if="!visibleArticles.length" description="暂无文章" />

      <div v-else class="article-list article-list--image_text article-list--cards">
        <BlogArticleCard v-for="article in visibleArticles" :key="article.slug" :article="article" :show-relative-time="true" />
      </div>

      <div v-if="visibleArticles.length && showLoadMore" class="article-load-more">
        <button
          class="load-more-btn"
          :class="{ 'load-more-btn--end': !hasMoreArticles }"
          type="button"
          :disabled="!hasMoreArticles || loading"
          @click="loadMore"
        >
          <span v-if="hasMoreArticles">{{ loading ? '加载中...' : '加载更多' }}</span>
          <span v-else>没有更多了</span>
        </button>
      </div>
    </section>
  </BlogPageLayout>
</template>
