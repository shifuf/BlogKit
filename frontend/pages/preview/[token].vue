<script setup lang="ts">
import { Eye } from '@lucide/vue';
import { toBlogArticle, toBlogCategory } from '~/utils/blog-adapter';
import type { Article, Category } from '~/types/blog';

const route = useRoute();
const { fetchArticlePreview } = useApiClient();
const { data: publicCategories } = await usePublicCategories();
const token = computed(() => String(route.params.token || ''));

const { data, pending } = await useAsyncData(`article-preview-${token.value}`, async () => {
  if (!token.value) {
    return { article: null, categories: [] as Category[] };
  }
  const response = await fetchArticlePreview(token.value);
  return {
    article: response.success ? toBlogArticle(response.data) : null,
    categories: (publicCategories.value || []).map(toBlogCategory).filter(Boolean) as Category[]
  };
}, {
  watch: [token],
  default: () => ({ article: null as Article | null, categories: [] as Category[] })
});

const article = computed(() => data.value.article);
const category = computed(() => {
  const current = article.value;
  if (!current) return undefined;
  return data.value.categories.find(item => item.slug === current.category || item.label === current.category);
});

useHead(() => ({
  title: article.value ? `${article.value.title} - 草稿预览 - 我的博客` : '草稿预览 - 我的博客',
  meta: [
    { name: 'robots', content: 'noindex,nofollow' }
  ]
}));
</script>

<template>
  <BlogArticleDetailSkeleton v-if="pending && !article" />

  <template v-else-if="article">
    <BlogArticleDetailHero :article="article" :category="category" />
    <BlogPageLayout class="article-detail-layout" :sidebar="false">
      <BlogSingleArticle :title="article.title" hide-header>
        <div class="preview-banner">
          <Eye :size="18" aria-hidden="true" />
          <span>草稿预览</span>
        </div>
        <BlogMarkdownPreview :content="article.rawContent || article.content.join('\n\n')" />
      </BlogSingleArticle>
    </BlogPageLayout>
  </template>

  <div v-else class="page-container page-container--narrow">
    <ElEmpty description="预览链接不存在或已过期" />
  </div>
</template>

<style scoped lang="scss">
.preview-banner {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--theme-color);
  background: color-mix(in srgb, var(--theme-color) 8%, transparent);
  font-size: 14px;
  font-weight: 600;
}
</style>
