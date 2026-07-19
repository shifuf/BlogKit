<template>
        <component @click="clickBtn" :is="isCollapse? MenuUnfoldOutlined :MenuFoldOutlined " class="icons" />
         
  </template>
  
  <script setup>
    import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons-vue';
import { ref, reactive, computed } from 'vue';
import { collapseStore } from '../../../stores/modules/collapse.js';
const store = collapseStore();
const isCollapse = computed(() => {
  return store.getCollapse;
});
const clickBtn = () => {
  store.setCollapse();
};
  </script>
  <style scoped>
.icons{
    font-size: 21px;
}
</style>
  