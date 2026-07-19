<script setup lang="ts">
import type { Article } from '~/types/blog';
import { toBlogArticle } from '~/utils/blog-adapter';
const {
  fetchArticleSummaries
} = useApiClient();
const {
  data: articles,
  pending: loading
} = await useAsyncData<Article[]>('all-articles-page', async () => {
  try {
    const response = await fetchArticleSummaries(0, 50, {
      sort: 'latest'
    });
    return response.success ? response.data.items.map(toBlogArticle) : [];
  } catch {
    return [];
  }
}, {
  default: () => []
});
useHead({
  title: '全部文章 - 我的博客'
});
</script>

<template>
  <BlogPageLayout>
    <section class="category-hero" style="--accent: #409eff;">
      <small>Category</small>
      <h1>全部文章</h1>
      <p>按最新发布顺序浏览站内文章。</p>
    </section>

    <section class="main-article-list">
      <ElSkeleton v-if="loading && !articles.length" :rows="6" animated />
      <div v-else class="article-list article-list--image_text">
        <BlogArticleCard v-for="article in articles" :key="article.slug" :article="article" />
      </div>
      <ElEmpty v-if="!loading && !articles.length" description="暂无文章" />
    </section>
  </BlogPageLayout>
</template>
