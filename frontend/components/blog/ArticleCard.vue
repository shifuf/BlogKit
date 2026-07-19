<script setup lang="ts">
import type { Article } from '~/types/blog';
import { buildAvatarUrl, buildCoverUrl, placeholderCover } from '~/utils/blog-adapter';
import { formatRecentTime, formatRelativeTime } from '~/utils/date';
const props = defineProps<{
  article: Article;
  showRelativeTime?: boolean;
}>();
const coverSrc = computed(() => buildCoverUrl(props.article.cover, 480));
const avatarSrc = computed(() => buildAvatarUrl(props.article.avatar));
const onCoverError = (event: Event) => {
  const img = event.target as HTMLImageElement;
  const fallback = placeholderCover(Number(props.article.slug) || 0);
  if (img.src !== fallback) img.src = fallback;
};
const relativeTimeSource = computed(() => props.article.publishedAt || props.article.date);
const displayTime = computed(() => {
  if (props.showRelativeTime === false) return props.article.date;
  return props.showRelativeTime === true
    ? formatRecentTime(relativeTimeSource.value)
    : formatRelativeTime(relativeTimeSource.value);
});
const displayText = computed(() => displayTime.value);
const displayTags = computed(() => props.article.tags
  .filter(tag => tag && tag !== props.article.categoryName)
  .slice(0, 3));
// 卡片整体是 <a>，内部分类/标签/评论不能再用嵌套 <a>，改用 .stop.prevent + navigateTo 跳转。
const goCategory = () => {
  const slug = props.article.category || props.article.categoryName;
  if (slug) navigateTo(`/category/${encodeURIComponent(slug)}`);
};
const goTag = (tag: string) => {
  if (tag) navigateTo(`/tags/${encodeURIComponent(tag)}`);
};
// 评论：跳到文章详情的评论区（详情页评论区带 #comments 锚点）。
const goComments = () => {
  navigateTo(`/article/${props.article.slug}#comments`);
};
</script>

<template>
  <NuxtLink :to="`/article/${article.slug}`" class="article-item article-item--link" :class="{ 'article-item--sticky': article.sticky }">
    <span class="article-item__thumb">
      <img :src="coverSrc" :alt="article.title" width="300" height="188" loading="lazy" decoding="async" @error="onCoverError" />
    </span>
    <div class="article-item__content">
      <h3 class="article-item__title">
        <span>
          <span v-if="article.sticky" class="article-item__sticky">置顶</span>
          <span v-if="article.titleHighlight" v-html="article.titleHighlight" />
          <template v-else>{{ article.title }}</template>
        </span>
      </h3>
      <p v-if="article.summaryHighlight" class="article-item__excerpt" v-html="article.summaryHighlight" />
      <p v-else class="article-item__excerpt">{{ article.excerpt }}</p>
      <div v-if="article.categoryName || displayTags.length" class="article-item__cats">
        <span
          v-if="article.categoryName"
          class="article-item__cat article-item__cat--primary article-item__cat--link"
          role="link"
          tabindex="0"
          @click.stop.prevent="goCategory"
          @keydown.enter.stop.prevent="goCategory"
        >{{ article.categoryName }}</span>
        <span
          v-for="tag in displayTags"
          :key="tag"
          class="article-item__cat article-item__cat--tag article-item__cat--link"
          role="link"
          tabindex="0"
          @click.stop.prevent="goTag(tag)"
          @keydown.enter.stop.prevent="goTag(tag)"
        >{{ tag }}</span>
      </div>
      <div class="article-item__meta">
        <div class="article-item__meta-main">
          <span class="article-item__author">
            <img class="article-item__author-avatar" :src="avatarSrc" :alt="article.author" width="22" height="22" loading="lazy" decoding="async" />
            <span>{{ article.author }}</span>
          </span>
          <time :datetime="relativeTimeSource">{{ displayText }}</time>
        </div>
        <div class="article-item__stats">
          <span
            class="article-item__stat article-item__stat--comments article-item__stat--link"
            role="link"
            tabindex="0"
            @click.stop.prevent="goComments"
            @keydown.enter.stop.prevent="goComments"
          >{{ article.comments }} 评论</span>
          <span class="article-item__stat article-item__stat--views">{{ article.views }} 阅读</span>
        </div>
      </div>
    </div>
  </NuxtLink>
</template>

<style scoped lang="scss">
/* 分类/标签/评论在卡片内可点击跳转：给出指针与悬停反馈，区别于纯展示文本。 */
.article-item__cat--link,
.article-item__stat--link {
  cursor: pointer;
  transition: color 0.18s ease, background 0.18s ease, border-color 0.18s ease;
}
.article-item__cat--link:hover,
.article-item__stat--link:hover {
  color: var(--theme-color);
}
.article-item__cat--link:focus-visible,
.article-item__stat--link:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--theme-color) 60%, transparent);
  outline-offset: 2px;
  border-radius: 6px;
}
</style>
