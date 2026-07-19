<script setup lang="ts">
import BlogTocWidget from './TocWidget.vue';
const props = withDefaults(defineProps<{
  toc?: boolean;
  tocRefreshKey?: string | number;
  sidebar?: boolean;
}>(), {
  sidebar: true
});
</script>

<template>
  <div class="page-container">
    <div class="layout-container">
      <div class="layout-grid">
        <div class="layout-grid__main">
          <slot />
        </div>
        <div class="layout-grid__sidebar">
          <BlogTocWidget v-if="props.toc" :refresh-key="props.tocRefreshKey" />
          <slot name="sidebar" />
          <BlogSidebar v-if="props.sidebar" />
        </div>
      </div>
    </div>
    <!-- BackToTop 已升级为全局组件，挂载在 layouts/default.vue 顶层。 -->
  </div>
</template>
