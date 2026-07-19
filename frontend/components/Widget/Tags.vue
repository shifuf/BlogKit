<template>
  <div class="tag-filter-bar">
    <div class="tag-filter-bar__list">
      <NuxtLink
        v-for="tag in tagSummaries"
        :id="tagRouteSegment(tag)"
        :key="tag.id"
        :to="tagRoutePath(tag)"
        :style="{ order: isSelectedTag(tag) ? -1 : '' }"
        :class="{ 'tag-filter-bar__link--active': isSelectedTag(tag) }"
        class="tag-filter-bar__link"
      >
        <span class="tag-filter-bar__icon"></span>
        <span>{{ tag.name }}</span>
        <span class="tag-filter-bar__count">{{ tag.articleCount }}</span>
      </NuxtLink>
    </div>
    <el-button type="primary" @click="openDrawer">更多</el-button>
  </div>

  <Drawer ref="drawerRef">
    <div class="tag-filter-drawer">
      <NuxtLink
        v-for="tag in tagSummaries"
        :id="`drawer-${tagRouteSegment(tag)}`"
        :key="tag.id"
        :to="tagRoutePath(tag)"
        :style="{ order: isSelectedTag(tag) ? -1 : '' }"
        :class="{ 'tag-filter-bar__link--active': isSelectedTag(tag) }"
        class="tag-filter-bar__link"
      >
        <span class="tag-filter-bar__icon"></span>
        <span>{{ tag.name }}</span>
        <span class="tag-filter-bar__count">{{ tag.articleCount }}</span>
      </NuxtLink>
    </div>
  </Drawer>
</template>

<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue';
import type { TagSummary } from '~/types/blog';
const route = useRoute();
const {
  fetchTags
} = useApiClient();
const drawerRef = ref<(ComponentPublicInstance & {
  open: () => void;
}) | null>(null);
const fallbackTagSummaries: TagSummary[] = [{
  id: 0,
  name: '全部',
  slug: 'all',
  articleCount: 0
}];
const tagSummaries = ref<TagSummary[]>(fallbackTagSummaries);
const selectedTagSegment = computed(() => decodeURIComponent(String(route.path.split('/').pop() || 'all')));
const tagRouteSegment = (tag: TagSummary) => tag.slug || tag.name;
const tagRoutePath = (tag: TagSummary) => `/tags/${encodeURIComponent(tagRouteSegment(tag))}`;
const isSelectedTag = (tag: TagSummary) => {
  const segment = selectedTagSegment.value;
  return segment === tagRouteSegment(tag) || segment === tag.name || segment === '全部' && tagRouteSegment(tag) === 'all';
};
const openDrawer = () => {
  drawerRef.value?.open();
};
const buildTagList = (tags: TagSummary[]) => {
  const totalArticles = tags.reduce((total, tag) => total + tag.articleCount, 0);
  return [{
    id: 0,
    name: '全部',
    slug: 'all',
    articleCount: totalArticles
  }, ...tags];
};
onMounted(async () => {
  try {
    const response = await fetchTags();
    if (response.success) {
      tagSummaries.value = buildTagList(response.data);
    }
  } catch {
    tagSummaries.value = fallbackTagSummaries;
  }
});
</script>

<style lang="scss" scoped>
.tag-filter-bar {
  width: 100%;
  padding: 0.4rem 1rem 0.4rem 0.7rem;
  background: #fff;
  border-radius: 12px;
  display: flex;
  white-space: nowrap;
  overflow: hidden;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  border: 1px solid #e3e8f7;
  transition: 0.3s;
}

.tag-filter-bar__list,
.tag-filter-drawer {
  white-space: nowrap;
  overflow-x: hidden;
  overflow-y: hidden;
  display: flex;
  border-radius: 8px;
  align-items: center;
}

.tag-filter-drawer {
  flex-wrap: wrap;
  justify-content: center;
  padding: 1rem;
}

.tag-filter-bar__link {
  padding: 0.1rem 0.5rem;
  margin: 0.25rem;
  line-height: 1.6;
  border-radius: 8px;
  color: #363636;
  border: 1px solid #e3e8f7;
  background: #fff;

  &--active {
    background: #2583e8;
    color: #fff;
  }
}

.tag-filter-bar__icon::before {
  content: '\e71a';
  font-family: 'iconfont' !important;
  opacity: 0.4;
  margin-right: 2px;
}

.tag-filter-bar__count {
  padding: 2px;
  background: #fff;
  border: 1px solid #e3e8f7;
  min-width: 22.5px;
  display: inline-block;
  border-radius: 4px;
  text-align: center;
  font-size: 0.7rem;
  color: #000;
  margin-left: 4px;
}
</style>
