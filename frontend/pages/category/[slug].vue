<script setup lang="ts">
import type { Article, Category } from '~/types/blog';
import { toBlogArticle, toBlogCategory } from '~/utils/blog-adapter';
const route = useRoute();
const {
  fetchArticleSummaries
} = useApiClient();
definePageMeta({
  key: route => route.path
});
const {
  data: publicCategories
} = await usePublicCategories();
const pageSize = 10;
const slug = computed(() => String(route.params.slug || 'all'));
const articles = ref<Article[]>([]);
const totalItems = ref(0);
const loadedPage = ref(0);
const loading = ref(false);
const loadingMore = ref(false);
let articleRequestId = 0;
const category = computed(() => {
  const list = (publicCategories.value || []).map(item => toBlogCategory(item)).filter(Boolean) as Category[];
  return list.find(item => item.slug === slug.value);
});
const visibleArticles = computed(() => articles.value);
const hasMoreArticles = computed(() => visibleArticles.value.length < totalItems.value);
type SortKey = 'latest' | 'views' | 'comments';
const sortOptions: { value: SortKey; label: string }[] = [
  { value: 'latest', label: '发布' },
  { value: 'views', label: '浏览' },
  { value: 'comments', label: '评论' }
];
const sortKey = ref<SortKey>('latest');
const categoryQueryKey = computed(() => JSON.stringify({
  slug: slug.value,
  sort: sortKey.value
}));
const buildFilters = () => ({
  categorySlug: slug.value,
  sort: sortKey.value
});
const fetchCategoryArticles = async (page: number, filters = buildFilters()) => {
  const response = await fetchArticleSummaries(page, pageSize, filters);
  return {
    articles: response.success ? response.data.items.map(toBlogArticle) : [],
    totalItems: response.success ? response.data.totalItems : 0
  };
};
const loadFirstPage = async () => {
  const requestId = ++articleRequestId;
  const requestKey = categoryQueryKey.value;
  const filters = buildFilters();
  loading.value = true;
  loadingMore.value = false;
  articles.value = [];
  totalItems.value = 0;
  loadedPage.value = 0;
  try {
    const nextData = await fetchCategoryArticles(0, filters);
    if (requestId !== articleRequestId || requestKey !== categoryQueryKey.value) return;
    articles.value = nextData.articles;
    totalItems.value = nextData.totalItems;
  } catch {
    if (requestId !== articleRequestId || requestKey !== categoryQueryKey.value) return;
    articles.value = [];
    totalItems.value = 0;
  } finally {
    if (requestId === articleRequestId) {
      loading.value = false;
    }
  }
};
await loadFirstPage();
const changeSort = (key: SortKey) => {
  if (sortKey.value === key) return;
  sortKey.value = key;
};
watch(slug, (nextSlug, previousSlug) => {
  if (nextSlug === previousSlug) return;
  sortKey.value = 'latest';
});
watch(categoryQueryKey, () => {
  void loadFirstPage();
}, {
  flush: 'post'
});
const loadMore = async () => {
  if (loadingMore.value || !hasMoreArticles.value) return;
  const currentKey = categoryQueryKey.value;
  const filters = buildFilters();
  const nextPage = loadedPage.value + 1;
  loadingMore.value = true;
  try {
    const nextData = await fetchCategoryArticles(nextPage, filters);
    if (currentKey !== categoryQueryKey.value) return;
    const seen = new Set(articles.value.map(article => article.slug));
    articles.value = [...articles.value, ...nextData.articles.filter(article => !seen.has(article.slug))];
    totalItems.value = nextData.totalItems;
    loadedPage.value = nextPage;
  } finally {
    if (currentKey === categoryQueryKey.value) {
      loadingMore.value = false;
    }
  }
};
useHead(() => ({
  title: category.value ? `${category.value.label} - 我的博客` : '分类 - 我的博客'
}));
</script>

<template>
  <BlogPageLayout>
    <section class="category-head">
      <div class="category-head__title">
        <h1>{{ category?.label || slug }}</h1>
        <span class="category-head__count">共 {{ totalItems }} 篇</span>
      </div>
      <p v-if="category?.description" class="category-head__desc">{{ category.description }}</p>
    </section>

    <div class="category-sortbar">
      <span class="category-sortbar__label">排序</span>
      <span class="category-sortbar__divider">|</span>
      <button
        v-for="opt in sortOptions"
        :key="opt.value"
        class="category-sort-btn"
        :class="{ active: sortKey === opt.value }"
        type="button"
        @click="changeSort(opt.value)"
      >{{ opt.label }}</button>
    </div>

    <section class="main-article-list">
      <ElSkeleton v-if="loading && !visibleArticles.length" :rows="6" animated />
      <div v-else class="article-list article-list--image_text">
        <BlogArticleCard v-for="article in visibleArticles" :key="article.slug" :article="article" />
      </div>
      <div v-if="totalItems" class="article-load-more">
        <button
          class="load-more-btn"
          :class="{ 'load-more-btn--end': !hasMoreArticles }"
          type="button"
          :disabled="!hasMoreArticles || loadingMore"
          @click="loadMore"
        >
          <span v-if="hasMoreArticles">{{ loadingMore ? '加载中...' : '加载更多' }}</span>
          <span v-else>没有更多了</span>
        </button>
      </div>
      <ElEmpty v-if="!loading && !visibleArticles.length" description="当前分类暂无文章" />
    </section>
  </BlogPageLayout>
</template>

<style scoped lang="scss">
.category-head {
  margin-bottom: 12px;
  padding: 20px 24px;
  border-radius: var(--radius);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
}
.category-head__title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}
.category-head__title h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--text-primary);
}
.category-head__count {
  font-size: 13px;
  color: var(--text-muted);
}
.category-head__desc {
  margin: 8px 0 0;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--text-secondary);
}
.category-sortbar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 0 4px 16px;
  font-size: 13px;
  color: var(--text-muted);
}
.category-sortbar__label {
  font-weight: 700;
  color: var(--text-secondary);
}
.category-sortbar__divider {
  color: var(--border-color);
}
.category-sort-btn {
  padding: 2px;
  border: 0;
  background: none;
  color: var(--text-muted);
  cursor: pointer;
  transition: color 0.2s ease;
}
.category-sort-btn:hover,
.category-sort-btn.active {
  color: var(--theme-color);
}
</style>
