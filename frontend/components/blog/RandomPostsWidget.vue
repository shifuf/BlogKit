<script setup lang="ts">
import type { Article } from '~/types/blog';
import { buildCoverUrl, toBlogArticle } from '~/utils/blog-adapter';
const {
  fetchArticleSummaries
} = useApiClient();
const {
  data: randomPosts
} = await useAsyncData<Article[]>('random-posts-widget', async () => {
  try {
    const response = await fetchArticleSummaries(0, 5, {
      sort: 'views'
    });
    return response.success ? response.data.items.map(toBlogArticle) : [];
  } catch {
    return [];
  }
}, {
  default: () => [],
  lazy: true
});
const firstRandomPost = computed(() => randomPosts.value[0]);
const articleRoute = (post: Article) => `/article/${post.slug}`;
const coverSrc = (post: Article) => buildCoverUrl(post.cover, 320);
// 排行角标固定红黄蓝灰：第1红 / 第2黄 / 第3蓝 / 其余灰，不随封面取色。
const rankColor = (rank: number) => rank === 1 ? '#ef4444' : rank === 2 ? '#eab308' : rank === 3 ? '#3b82f6' : '#9ca3af';
</script>

<template>
  <section v-if="firstRandomPost" class="widget widget_aerocore_post_list">
    <div class="widget-title widget-title-mac">热门阅读</div>
    <div class="aerocore-widget-post-list">
      <NuxtLink class="widget-post-item widget-post-item-first" :to="articleRoute(firstRandomPost)">
        <span class="widget-post-item-index" :style="{ background: rankColor(1) }">1</span>
        <img :src="coverSrc(firstRandomPost)" :alt="firstRandomPost.title" width="320" height="160" loading="lazy" decoding="async">
        <div class="widget-post-title-warp">
          <span>{{ firstRandomPost.title }}</span>
        </div>
      </NuxtLink>

      <NuxtLink v-for="(post, index) in randomPosts.slice(1)" :key="post.slug" class="widget-post-item" :to="articleRoute(post)">
        <span class="widget-post-item-index" :style="{ background: rankColor(index + 2) }">{{ index + 2 }}</span>
        <div class="post-thumbnail">
          <img :src="coverSrc(post)" :alt="post.title" width="96" height="56" loading="lazy" decoding="async">
        </div>
        <div class="post-main">
          <span class="post-main__title">{{ post.title }}</span>
        </div>
      </NuxtLink>
    </div>
  </section>
</template>

<style scoped>
/* 角标色由内联 rankColor 固定，不随封面取色；hover 时图片略放大。 */
.widget-post-item-first,
.post-thumbnail {
  overflow: hidden;
}
.widget-post-item img,
.post-thumbnail img {
  transition: transform 0.3s ease;
}
.widget-post-item:hover img {
  transform: scale(1.08);
}
.widget-post-item-index {
  color: #fff;
}
</style>
