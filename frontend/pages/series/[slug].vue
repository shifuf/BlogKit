<script setup lang="ts">
const route = useRoute(); const api = useApiClient();
const { data, error } = await useAsyncData(`series-${route.params.slug}`, async () => (await api.fetchSeriesDetail(String(route.params.slug))).data);
if (error.value) throw createError({ statusCode: (error.value as any).statusCode || 503, statusMessage: '系列暂不可用' });
useSeoMeta({ title: () => data.value?.series.name || '系列阅读' });
</script>
<template><BlogPageLayout><section class="series-page"><NuxtLink to="/series">全部系列</NuxtLink><h1>{{ data?.series.name }}</h1><p>{{ data?.series.description }}</p><ol><li v-for="item in data?.articles" :key="item.id"><NuxtLink :to="`/article/${item.id}`">{{ item.title }}</NuxtLink></li></ol><p v-if="!data?.articles.length">暂无已发布文章</p></section></BlogPageLayout></template>
<style scoped>.series-page{max-width:960px;margin:40px auto;padding:24px}.series-page li{padding:16px;border-bottom:1px solid var(--border-color)}</style>
