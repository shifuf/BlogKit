<script setup lang="ts">
import type { NavItem } from '~/types/blog';
const props = defineProps<{
  item: NavItem;
}>();
const route = useRoute();
const isActive = (item: NavItem): boolean => {
  if (item.external) return false;
  // 首页只在精确「/」时激活，否则会因 startsWith 在所有路由下常亮。
  if (item.to === '/') return route.path === '/';
  return route.path === item.to || route.path.startsWith(`${item.to}/`);
};
const hasActiveChild = (item: NavItem): boolean => {
  return item.children?.some(child => isActive(child) || hasActiveChild(child)) ?? false;
};
const menuItemClass = (item: NavItem) => {
  const active = isActive(item) || hasActiveChild(item);
  return {
    active,
    'current-menu-item': active,
    'current_page_item': active,
    'menu-item-has-children': Boolean(item.children?.length)
  };
};
</script>

<template>
  <li class="menu-item" :class="menuItemClass(item)">
    <a v-if="item.external" :href="item.to" target="_blank" rel="noopener noreferrer">
      <span>{{ item.label }}</span>
    </a>
    <NuxtLink v-else :to="item.to">
      <span>{{ item.label }}</span>
    </NuxtLink>

    <ul v-if="item.children?.length" class="sub-menu">
      <BlogSiteNavItem
        v-for="child in item.children"
        :key="`${child.label}-${child.to}`"
        :item="child"
      />
    </ul>
  </li>
</template>
