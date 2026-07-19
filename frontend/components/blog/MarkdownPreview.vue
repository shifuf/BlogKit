<script setup lang="ts">
import { applyBlogContainer } from '~/utils/blog-markdown';
import { defineMusicPlayerElement } from '~/utils/define-music-player';
import { sanitizeArticleHtml } from '~/utils/sanitize-html';
// 与文章详情/后台编辑器同一套 md-editor-v3 渲染，保证动态正文与后台所见一致。
const props = defineProps<{
  content?: string;
}>();
const { isDark } = useTheme();
const ready = ref(false);
const previewId = `bmd-${useId()}`;
const sanitizeContent = (html: string) => sanitizeArticleHtml(html);
const MdPreview = defineAsyncComponent(async () => {
  // 确保在首次渲染 Markdown 前自定义元素已注册。
  defineMusicPlayerElement();
  const [module] = await Promise.all([
    import('md-editor-v3'),
    import('md-editor-v3/lib/preview.css')
  ]);
  module.config({
    editorExtensions: {
      highlight: {
        js: '/vendor/highlightjs/highlight.min.js',
        css: {
          atom: {
            light: '/vendor/highlightjs/styles/atom-one-light.min.css',
            dark: '/vendor/highlightjs/styles/atom-one-dark.min.css'
          }
        }
      }
    },
    markdownItConfig(md: unknown) {
      applyBlogContainer(md as never);
    }
  });
  return module.MdPreview;
});

onMounted(() => {
  defineMusicPlayerElement();
  ready.value = true;
});
</script>

<template>
  <ClientOnly>
    <MdPreview
      v-if="ready && content"
      :id="previewId"
      class="blog-md-preview"
      :model-value="content"
      :sanitize="sanitizeContent"
      :theme="isDark ? 'dark' : 'light'"
      preview-theme="github"
      code-theme="atom"
      :no-katex="true"
      :no-mermaid="true"
      :no-echarts="true"
      :no-prettier="true"
    />
    <div v-else-if="content" class="blog-md-preview blog-md-preview--fallback" />
    <template #fallback>
      <div class="blog-md-preview blog-md-preview--fallback" />
    </template>
  </ClientOnly>
</template>

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
