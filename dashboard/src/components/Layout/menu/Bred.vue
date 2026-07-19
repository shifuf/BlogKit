<template>
  <a-breadcrumb class="bred">
    <a-breadcrumb-item v-for="(item, index) in tabs" :key="item.path || index" class="item">
      <HomeOutlined v-if="index === 0" class="bred-home-icon" />
      {{ item.meta.title }}
    </a-breadcrumb-item>
  </a-breadcrumb>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { HomeOutlined } from '@ant-design/icons-vue';
const route = useRouter();
const tabs = ref([]);
const getBred = () => {
  const matched = route.currentRoute.value.matched;
  let mached = matched.filter(item => item.meta && item.meta.title);
  const first = mached[0];
  // dashboard 的 path 是空字符串，需一并判定，否则首页会出现「工作台 / 工作台」重复。
  if (first && first.path !== '/' && first.path !== '' && first.name !== 'dashboard') {
    mached = [{
      path: '/',
      meta: {
        title: '工作台'
      }
    }, ...mached];
  }
  tabs.value = mached;
};
onMounted(() => {
  getBred();
  route.afterEach(() => {
    getBred();
  });
});
</script>
<style lang="scss" scoped>
.bred {
  margin-left: 16px;
  font-size: 14px;
  display: flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.12);
  :deep(.ant-breadcrumb-link) {
    color: rgba(255, 255, 255, 0.75);
    display: inline-flex;
    align-items: center;
  }
  .item:last-child :deep(.ant-breadcrumb-link) {
    color: #fff;
    font-weight: 600;
  }
}
.bred-home-icon {
  margin-right: 4px;
  font-size: 13px;
}
:deep(.ant-breadcrumb-separator) {
  color: rgba(255, 255, 255, 0.5);
  margin: 0 8px;
}
@media (max-width: 768px) {
  .bred {
    margin-left: 8px;
    padding: 3px 8px;
    background: transparent;
    /* 移动端面包屑在头部垂直居中：撑满头部高度并居中内容，避免靠上 */
    height: 100%;
    align-self: center;
  }
  .bred :deep(.ant-breadcrumb) {
    display: flex;
    align-items: center;
  }
  .bred :deep(.ant-breadcrumb ol) {
    display: flex;
    align-items: center;
    flex-wrap: nowrap;
    margin: 0;
  }
}
</style>

