<template>
  <div class="logo">
    <img v-show="!collapsed" src="/logo.png" alt="我的博客">
  </div>
</template>

<script setup>

import { ref, reactive, computed, watch } from 'vue';
import { collapseStore } from '../../../stores//modules/collapse.js';
const store = collapseStore();
const collapsed = computed(() => {
  return store.getCollapse;
});
const show = ref(true);
watch(() => collapsed.value, collapsed => {
  if (!collapsed) {
    setTimeout(() => {
      show.value = !collapsed;
    }, 250);
  } else {
    show.value = !collapsed;
  }
});
</script>
<style lang="scss" scoped>
.logo {
  background-color: #fff;
  height: 50px;
  border: none;
  line-height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;

  img {
    max-width: 100%;
    max-height: 40px;
    object-fit: contain;
    transition: all 0.3s;
  }

  .logo-full {
    height: 32px;
  }

  .logo-icon {
    height: 38px;
  }

  .transition-box {
    height: 50px;
    background-color: #fff;
    text-align: center;
    color: #fff;
    box-sizing: border-box;
    line-height: 50px;
    font-size: 20px;
    vertical-align: middle;
  }
}
</style>
