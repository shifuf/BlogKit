<script setup lang="ts">
import { ArrowUp } from '@lucide/vue';

const SHOW_OFFSET = 40;
const visible = ref(false);
let scrollRaf = 0;
let lastScrollElement: Element | null = null;

const getDocumentScrollTop = () => Math.max(
  window.scrollY || 0,
  document.documentElement.scrollTop || 0,
  document.body.scrollTop || 0
);

const getScrollTop = () => Math.max(
  getDocumentScrollTop(),
  lastScrollElement instanceof HTMLElement ? lastScrollElement.scrollTop || 0 : 0
);

const sync = () => {
  if (!import.meta.client) return;
  visible.value = getScrollTop() > SHOW_OFFSET;
};

const onScroll = (event?: Event) => {
  const target = event?.target;
  if (target instanceof Element && target !== document.documentElement && target !== document.body) {
    lastScrollElement = target;
  }
  if (scrollRaf) return;
  scrollRaf = window.requestAnimationFrame(() => {
    scrollRaf = 0;
    sync();
  });
};

const scrollToTop = () => {
  window.scrollTo({
    top: 0,
    behavior: 'smooth'
  });
  if (lastScrollElement instanceof HTMLElement) {
    lastScrollElement.scrollTo({
      top: 0,
      behavior: 'smooth'
    });
  }
};

onMounted(() => {
  sync();
  document.addEventListener('scroll', onScroll, { passive: true, capture: true });
  window.addEventListener('resize', onScroll, { passive: true });
});

onUnmounted(() => {
  if (import.meta.client) {
    document.removeEventListener('scroll', onScroll, { capture: true });
    window.removeEventListener('resize', onScroll);
    if (scrollRaf) window.cancelAnimationFrame(scrollRaf);
  }
});
</script>

<template>
  <Transition name="back-to-top-fade">
    <button v-show="visible" class="back-to-top" type="button" aria-label="返回顶部" @click="scrollToTop">
      <ArrowUp :size="22" aria-hidden="true" />
    </button>
  </Transition>
</template>
