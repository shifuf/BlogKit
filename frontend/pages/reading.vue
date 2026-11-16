<script setup lang="ts">
const store = useStore.user(); const api = useApiClient();
if (!store.isAuthenticated) await navigateTo('/');
const items = ref<Array<{ articleId: number; title: string; progress: number; completed: boolean; later: boolean; updatedAt: string }>>([]);
const page = ref(0); const total = ref(0); const loading = ref(false); const error = ref(''); const filter = ref('all');
const shown = computed(() => items.value);
const load = async () => { if (loading.value) return; loading.value = true; error.value = ''; try { const r = await api.fetchReadingHistory(page.value, 20, filter.value); if (r.success) { items.value.push(...r.data.items); total.value = r.data.totalItems; page.value++; } } catch { error.value = '阅读记录加载失败'; } finally { loading.value = false; } };
watch(filter, () => { items.value = []; page.value = 0; total.value = 0; void load(); });
await load(); useSeoMeta({ title: '继续阅读' });
</script>
<template><BlogPageLayout><section class="reading-page"><h1>继续阅读</h1><select v-model="filter"><option value="all">全部</option><option value="reading">未读完</option><option value="completed">已读</option><option value="later">稍后读</option></select><p v-if="error">{{ error }} <button @click="load">重试</button></p><NuxtLink v-for="item in shown" :key="item.articleId" :to="`/article/${item.articleId}`" class="reading-item"><strong>{{ item.title }}</strong><progress :value="item.progress" max="100" />{{ item.completed ? '已读完' : `${Math.round(item.progress)}%` }}</NuxtLink><p v-if="!items.length">暂无阅读记录</p><button v-if="items.length < total" :disabled="loading" @click="load">加载更多</button></section></BlogPageLayout></template>
<style scoped>.reading-page{max-width:900px;margin:40px auto;padding:24px}.reading-item{display:flex;gap:20px;align-items:center;padding:20px;border-bottom:1px solid var(--border-color)}.reading-item strong{flex:1}</style>
