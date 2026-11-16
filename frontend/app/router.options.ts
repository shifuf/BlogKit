import type { RouterConfig } from '@nuxt/schema';
export default {
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition && to.meta?.keepScroll) return savedPosition;
    return { left: 0, top: 0 };
  }
} satisfies RouterConfig;
