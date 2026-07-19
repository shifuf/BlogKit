<template>
  <div class="navigation-drawer" :class="{ 'navigation-drawer--open': isDrawerOpen }">
    <div class="navigation-drawer__panel" @click.stop>
      <slot />
    </div>
    <div class="navigation-drawer__backdrop" @click="closeDrawer"></div>
  </div>
</template>

<script setup lang="ts">
const isDrawerOpen = ref(false);
const closeDrawer = () => {
  isDrawerOpen.value = false;
  document.body.style.overflow = '';
};
const open = () => {
  isDrawerOpen.value = true;
  document.body.style.overflow = 'hidden';
};
defineExpose({
  open
});
</script>

<style scoped>
.navigation-drawer {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: row-reverse;
  pointer-events: none;
  opacity: 0;
  z-index: 999;
  transition: opacity 0.22s ease;
  will-change: opacity;
}

.navigation-drawer--open {
  opacity: 1;
  pointer-events: auto;
}

.navigation-drawer__panel {
  z-index: 1000;
  width: min(300px, 86vw);
  height: 100%;
  overflow: auto;
  overflow-x: hidden;
  background: var(--bg-color);
  transform: translate3d(100%, 0, 0);
  transition: transform 0.26s cubic-bezier(0.32, 0.72, 0, 1);
  will-change: transform;
  backface-visibility: hidden;
  contain: layout paint style;
}

.navigation-drawer--open .navigation-drawer__panel {
  transform: translate3d(0, 0, 0);
}

.navigation-drawer__backdrop {
  z-index: 0;
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.45);
  transform: translateZ(0);
}
</style>
