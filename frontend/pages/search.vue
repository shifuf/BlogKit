<script setup lang="ts">
import { Search } from '@lucide/vue';
import type { Article } from '~/types/blog';
import { toBlogArticle } from '~/utils/blog-adapter';
const route = useRoute();
const {
  fetchArticleSummaries
} = useApiClient();
const pageSize = 10;
const keyword = computed(() => String(route.query.q || '').trim());
const searchValue = ref('');
const results = ref<Article[]>([]);
const totalItems = ref(0);
const loadedPage = ref(0);
const loading = ref(false);
const loadingMore = ref(false);
const hasMoreResults = computed(() => results.value.length < totalItems.value);
const submitSearch = async () => {
  const q = searchValue.value.trim();
  await navigateTo({
    path: '/search',
    query: q ? {
      q
    } : {}
  });
};
const loadResults = async () => {
  searchValue.value = keyword.value;
  if (!keyword.value) {
    results.value = [];
    totalItems.value = 0;
    loadedPage.value = 0;
    return;
  }
  const currentKeyword = keyword.value;
  loading.value = true;
  try {
    const response = await fetchArticleSummaries(0, pageSize, {
      keyword: currentKeyword,
      sort: 'latest'
    });
    if (currentKeyword !== keyword.value) return;
    results.value = response.success ? response.data.items.map(toBlogArticle) : [];
    totalItems.value = response.success ? response.data.totalItems : 0;
    loadedPage.value = 0;
  } catch {
    results.value = [];
    totalItems.value = 0;
    loadedPage.value = 0;
  } finally {
    if (currentKeyword === keyword.value) {
      loading.value = false;
    }
  }
};
const loadMore = async () => {
  if (!keyword.value || loadingMore.value || !hasMoreResults.value) return;
  const currentKeyword = keyword.value;
  const nextPage = loadedPage.value + 1;
  loadingMore.value = true;
  try {
    const response = await fetchArticleSummaries(nextPage, pageSize, {
      keyword: currentKeyword,
      sort: 'latest'
    });
    if (currentKeyword !== keyword.value || !response.success) return;
    results.value = [...results.value, ...response.data.items.map(toBlogArticle)];
    totalItems.value = response.data.totalItems;
    loadedPage.value = nextPage;
  } finally {
    if (currentKeyword === keyword.value) {
      loadingMore.value = false;
    }
  }
};
watch(keyword, loadResults, {
  immediate: true
});
useHead(() => ({
  title: keyword.value ? `${keyword.value} - 搜索 - 我的博客` : '搜索 - 我的博客'
}));
</script>

<template>
  <BlogPageLayout>
    <section class="search-page-panel">
      <form class="search-page-form" role="search" @submit.prevent="submitSearch">
        <Search class="search-page-form__icon" :size="20" aria-hidden="true" />
        <input v-model="searchValue" class="search-page-form__input" type="text" name="s" placeholder="搜索文章..." autocomplete="off">
        <button class="search-page-form__submit" type="submit">搜索</button>
      </form>
      <div class="search-summary">
        <Search :size="20" />
        <span>{{ keyword ? `找到 ${totalItems} 篇相关文章` : '请输入关键词' }}</span>
      </div>
    </section>

    <section class="main-article-list">
      <ElSkeleton v-if="loading && !results.length" :rows="6" animated />
      <div v-else class="article-list article-list--image_text">
        <BlogArticleCard v-for="article in results" :key="article.slug" :article="article" />
      </div>
      <div v-if="totalItems" class="article-load-more">
        <button
          class="load-more-btn"
          :class="{ 'load-more-btn--end': !hasMoreResults }"
          type="button"
          :disabled="!hasMoreResults || loadingMore"
          @click="loadMore"
        >
          <span v-if="hasMoreResults">{{ loadingMore ? '加载中...' : '加载更多' }}</span>
          <span v-else>没有更多了</span>
        </button>
      </div>
      <ElEmpty v-if="!loading && !results.length" description="没有匹配内容" />
    </section>
  </BlogPageLayout>
</template>
