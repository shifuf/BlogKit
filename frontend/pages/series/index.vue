<script setup lang="ts">
const api = useApiClient();
const { data, error, refresh } = await useAsyncData('series-list', async () => (await api.fetchSeries()).data);
useSeoMeta({ title: '系列阅读' });
</script>
<template><BlogPageLayout><section class="series-page"><h1>系列阅读</h1><p v-if="error">系列加载失败 <button @click="refresh()">重试</button></p><NuxtLink v-for="item in data" :key="item.id" :to="`/series/${item.slug}`" class="series-card"><h2>{{ item.name }}</h2><p>{{ item.description }}</p><small>{{ item.articleCount }} 篇文章</small></NuxtLink><p v-if="data && !data.length">暂无公开系列</p></section></BlogPageLayout></template>
<style scoped>.series-page{max-width:960px;margin:40px auto;padding:24px}.series-card{display:block;padding:20px;margin:12px 0;border:1px solid var(--border-color);border-radius:12px}</style>
