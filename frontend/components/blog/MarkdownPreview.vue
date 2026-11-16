<script setup lang="ts">
import { renderMarkdown } from '~/utils/render-markdown';
import { defineMusicPlayerElement } from '~/utils/define-music-player';
const props = defineProps<{ content?: string }>();
const html = computed(() => renderMarkdown(props.content || ''));
onMounted(() => defineMusicPlayerElement());
</script>
<template><div class="blog-md-preview md-editor-preview" v-html="html" /></template>

<style scoped lang="scss">
.blog-md-preview {
  background: transparent;
}
:deep(.md-editor-preview-wrapper) {
  padding: 0;
}
:deep(.md-editor-preview) {
  font-size: 0.95rem;
  line-height: 1.75;
  color: var(--text-color);
}
/* 文章：最大 560 居中；动态卡片由页面样式 100% 贴合 */
:deep(music-player) {
  display: block;
  width: min(560px, 100%);
  margin: 14px auto;
}
.blog-md-preview--fallback {
  min-height: 1.5em;
  color: var(--text-color);
  line-height: 1.75;
}
</style>
