<script setup lang="ts">
defineProps<{
  title: string;
  meta?: string[];
  hideHeader?: boolean;
}>();
</script>

<template>
  <article class="single-article">
    <header v-if="!hideHeader" class="single-article__header">
      <h1 class="single-article__title">{{ title }}</h1>
      <div v-if="meta?.length" class="single-article__meta">
        <span v-for="item in meta" :key="item">{{ item }}</span>
      </div>
    </header>
    <div class="single-article__content">
      <slot />
    </div>
  </article>
</template>
