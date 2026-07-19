<script setup lang="ts">
import { Search } from '@lucide/vue';
import type { Article, ArticleSort, TagSummary } from '~/types/blog';
import { toBlogArticle } from '~/utils/blog-adapter';

const route = useRoute();
const {
  fetchArticleSummaries,
  fetchTags
} = useApiClient();
definePageMeta({
  key: route => route.path
});
const pageSize = 10;
const keywordInput = ref('');
const keyword = ref('');
const sortKey = ref<ArticleSort>('latest');
const articles = ref<Article[]>([]);
const totalItems = ref(0);
const loadedPage = ref(0);
const loading = ref(false);
const loadingMore = ref(false);
let articleRequestId = 0;
const sortOptions: { value: ArticleSort; label: string }[] = [
  { value: 'latest', label: '发布' },
  { value: 'views', label: '浏览' },
  { value: 'likes', label: '点赞' },
  { value: 'comments', label: '评论' }
];
const { data: tagsData } = await useAsyncData<TagSummary[]>('tags-page-all-tags', async () => {
  try {
    const response = await fetchTags();
    return response.success ? response.data : [];
  } catch {
    return [];
  }
}, {
  default: () => []
});
const allTags = computed(() => tagsData.value || []);
const selectedTagSegment = computed(() => {
  const tagParam = route.params.id;
  const rawSegment = Array.isArray(tagParam) ? tagParam[0] : tagParam;
  return decodeURIComponent(String(rawSegment || 'all')).trim();
});
const isAllTags = computed(() => !selectedTagSegment.value || selectedTagSegment.value === 'all' || selectedTagSegment.value === '全部');
const selectedTag = computed(() => {
  if (isAllTags.value) return null;
  const lower = selectedTagSegment.value.toLowerCase();
  return allTags.value.find(tag => tag.slug?.toLowerCase() === lower || tag.name?.toLowerCase() === lower) || null;
});
const resolvedTagSlug = computed(() => selectedTag.value?.slug || null);
const tagTitle = computed(() => {
  if (isAllTags.value) return '全部标签';
  return selectedTag.value?.name || selectedTagSegment.value;
});
const tagDescription = computed(() => {
  if (isAllTags.value) return '按标签浏览站内公开文章。';
  return `浏览「${tagTitle.value}」标签下的文章。`;
});
const tagFilters = computed(() => {
  const total = allTags.value.reduce((sum, tag) => sum + Number(tag.articleCount || 0), 0);
  return [
    { id: 0, name: '全部', slug: 'all', articleCount: total },
    ...allTags.value
  ];
});
const tagRouteSegment = (tag: TagSummary) => tag.slug || tag.name;
const tagRoutePath = (tag: TagSummary) => `/tags/${encodeURIComponent(tagRouteSegment(tag))}`;
const isSelectedFilter = (tag: TagSummary) => {
  if (tagRouteSegment(tag) === 'all') return isAllTags.value;
  return tag.slug === selectedTag.value?.slug || tag.name === selectedTag.value?.name || tagRouteSegment(tag) === selectedTagSegment.value;
};
const buildFilters = () => {
  const segment = selectedTagSegment.value;
  const hasTagSegment = Boolean(segment) && !isAllTags.value;
  const fallbackKeyword = !resolvedTagSlug.value && hasTagSegment ? segment : '';
  const searchKeyword = keyword.value || fallbackKeyword;
  return {
    ...(resolvedTagSlug.value ? { tagSlug: resolvedTagSlug.value } : {}),
    ...(searchKeyword ? { keyword: searchKeyword } : {}),
    sort: sortKey.value
  };
};
const articleQueryKey = computed(() => JSON.stringify({
  segment: selectedTagSegment.value,
  tagSlug: resolvedTagSlug.value || '',
  keyword: keyword.value,
  sort: sortKey.value
}));
const fetchTagArticles = async (page: number, filters = buildFilters()) => {
  const response = await fetchArticleSummaries(page, pageSize, filters);
  return {
    articles: response.success ? response.data.items.map(toBlogArticle) : [],
    totalItems: response.success ? response.data.totalItems : 0
  };
};
const loadFirstPage = async () => {
  const requestId = ++articleRequestId;
  const requestKey = articleQueryKey.value;
  const filters = buildFilters();
  loading.value = true;
  loadingMore.value = false;
  articles.value = [];
  totalItems.value = 0;
  loadedPage.value = 0;
  try {
    const nextData = await fetchTagArticles(0, filters);
    if (requestId !== articleRequestId || requestKey !== articleQueryKey.value) return;
    articles.value = nextData.articles;
    totalItems.value = nextData.totalItems;
  } catch {
    if (requestId !== articleRequestId || requestKey !== articleQueryKey.value) return;
    articles.value = [];
    totalItems.value = 0;
  } finally {
    if (requestId === articleRequestId) {
      loading.value = false;
    }
  }
};
await loadFirstPage();
watch(selectedTagSegment, (nextSegment, previousSegment) => {
  if (nextSegment === previousSegment) return;
  keywordInput.value = '';
  keyword.value = '';
  sortKey.value = 'latest';
});
watch(articleQueryKey, () => {
  void loadFirstPage();
}, {
  flush: 'post'
});
const hasMoreArticles = computed(() => articles.value.length < totalItems.value);
const applySearch = () => {
  const nextKeyword = keywordInput.value.trim();
  if (keyword.value === nextKeyword) {
    void loadFirstPage();
    return;
  }
  keyword.value = nextKeyword;
};
const changeSort = (value: ArticleSort) => {
  if (sortKey.value === value) return;
  sortKey.value = value;
};
const loadMore = async () => {
  if (loadingMore.value || !hasMoreArticles.value) return;
  const currentKey = articleQueryKey.value;
  const filters = buildFilters();
  const nextPage = loadedPage.value + 1;
  loadingMore.value = true;
  try {
    const nextData = await fetchTagArticles(nextPage, filters);
    if (currentKey !== articleQueryKey.value) return;
    const seen = new Set(articles.value.map(article => article.slug));
    articles.value = [...articles.value, ...nextData.articles.filter(article => !seen.has(article.slug))];
    totalItems.value = nextData.totalItems;
    loadedPage.value = nextPage;
  } finally {
    if (currentKey === articleQueryKey.value) {
      loadingMore.value = false;
    }
  }
};
useHead(() => ({
  title: `${tagTitle.value} - 标签 - 我的博客`
}));
</script>

<template>
  <BlogPageLayout>
    <section class="tag-head">
      <div class="tag-head__title">
        <h1>{{ tagTitle }}</h1>
        <span class="tag-head__count">共 {{ totalItems }} 篇</span>
      </div>
      <p>{{ tagDescription }}</p>
      <div v-if="tagFilters.length" class="tag-filter-list" aria-label="标签筛选">
        <NuxtLink
          v-for="tag in tagFilters"
          :key="tag.id || tagRouteSegment(tag)"
          class="tag-filter-chip"
          :class="{ active: isSelectedFilter(tag) }"
          :to="tagRoutePath(tag)"
        >
          <span>{{ tag.name }}</span>
          <small>{{ tag.articleCount }}</small>
        </NuxtLink>
      </div>
    </section>

    <section class="search-page-panel tag-search-panel">
      <form class="search-page-form" role="search" @submit.prevent="applySearch">
        <Search class="search-page-form__icon" :size="20" aria-hidden="true" />
        <input v-model="keywordInput" class="search-page-form__input" type="text" name="s" placeholder="在当前标签下搜索..." autocomplete="off">
        <button class="search-page-form__submit" type="submit">搜索</button>
      </form>
      <button v-if="keyword" class="tag-search-panel__clear" type="button" @click="keywordInput = ''; applySearch()">清除筛选</button>
    </section>

    <div class="tag-sortbar">
      <span class="tag-sortbar__label">排序</span>
      <span class="tag-sortbar__divider">|</span>
      <button
        v-for="opt in sortOptions"
        :key="opt.value"
        class="tag-sort-btn"
        :class="{ active: sortKey === opt.value }"
        type="button"
        @click="changeSort(opt.value)"
      >{{ opt.label }}</button>
    </div>

    <section class="main-article-list">
      <ElSkeleton v-if="loading && !articles.length" :rows="6" animated />
      <div v-else class="article-list article-list--image_text">
        <BlogArticleCard v-for="article in articles" :key="article.slug" :article="article" />
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
      <ElEmpty v-if="!loading && !articles.length" description="当前标签暂无文章" />
    </section>
  </BlogPageLayout>
</template>

<style scoped lang="scss">
.tag-head {
  margin-bottom: 12px;
  padding: 20px 24px;
  border-radius: 5px;
  background: var(--bg-card);
}

.tag-head__title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}

.tag-head__title h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--text-primary);
}

.tag-head__count {
  font-size: 13px;
  color: var(--text-muted);
}

.tag-head p {
  margin: 8px 0 0;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.tag-filter-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
  max-height: 94px;
  overflow-y: auto;
}

.tag-filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 13px;
  text-decoration: none;
  transition: color 0.2s ease, background 0.2s ease;
}

.tag-filter-chip small {
  color: var(--text-muted);
}

.tag-filter-chip:hover,
.tag-filter-chip.active {
  background: color-mix(in srgb, var(--theme-color) 12%, transparent);
  color: var(--theme-color);
}

.tag-search-panel {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tag-search-panel .search-page-form {
  flex: 1;
}

.tag-search-panel__clear {
  flex: 0 0 auto;
  height: 40px;
  padding: 0 14px;
  border: 0;
  border-radius: 999px;
  background: var(--bg-secondary);
  color: var(--text-secondary);
  cursor: pointer;
}

.tag-search-panel__clear:hover {
  color: var(--theme-color);
}

.tag-sortbar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 0 4px 16px;
  font-size: 13px;
  color: var(--text-muted);
}

.tag-sortbar__label {
  font-weight: 700;
  color: var(--text-secondary);
}

.tag-sortbar__divider {
  color: var(--border-color);
}

.tag-sort-btn {
  padding: 2px;
  border: 0;
  background: none;
  color: var(--text-muted);
  cursor: pointer;
  transition: color 0.2s ease;
}

.tag-sort-btn:hover,
.tag-sort-btn.active {
  color: var(--theme-color);
}

@media screen and (max-width: 680px) {
  .tag-head {
    padding: 16px;
  }

  .tag-search-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .tag-sortbar {
    flex-wrap: wrap;
  }
}
</style>
