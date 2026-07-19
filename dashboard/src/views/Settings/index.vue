<template>
  <div class="admin-page settings-page">
    <a-tabs v-model:activeKey="activeGroup" type="card" class="settings-tabs">
      <a-tab-pane v-for="tab in tabs" :key="tab.key" :tab="tab.label">
        <Group :group="tab.key" :title="tab.label" />
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Group from './Group.vue';
const route = useRoute();
const router = useRouter();
const tabs = [{
  key: 'basic',
  label: '基本配置'
}, {
  key: 'home',
  label: '首页配置'
}, {
  key: 'blog',
  label: '博客配置'
}, {
  key: 'notification',
  label: '通知配置'
}, {
  key: 'ai',
  label: 'AI 配置'
}, {
  key: 'oauth',
  label: 'OAuth 配置'
}, {
  key: 'upload',
  label: '上传配置'
}];
const activeGroup = ref(route.query.tab || 'basic');
watch(activeGroup, next => {
  router.replace({
    query: {
      ...route.query,
      tab: next
    }
  });
});
</script>

<style lang="scss" scoped>
.admin-page {
  padding: 4px 4px 16px;
}

.settings-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 16px;
}
.settings-tabs :deep(.ant-tabs-nav::before) {
  border: 0 !important;
}
/* 系统设置子页签：与顶部页签统一的胶囊风格、激活态主题色，移动端可横向滚动。 */
.settings-tabs :deep(.ant-tabs-nav-wrap) {
  overflow-x: auto;
}
.settings-tabs :deep(.ant-tabs-nav-list) {
  gap: 0;
}
.settings-tabs :deep(.ant-tabs-tab) {
  margin: 0 8px 8px 0 !important;
  padding: 7px 16px !important;
  border: 1px solid #e6e8ef !important;
  border-radius: 999px !important;
  background: #f6f7fb !important;
  color: #5a6072 !important;
  transition: color 0.2s ease, background 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}
.settings-tabs :deep(.ant-tabs-tab:hover) {
  color: #2f54eb !important;
  border-color: #b7c4f5 !important;
}
.settings-tabs :deep(.ant-tabs-tab.ant-tabs-tab-active) {
  background: #2f54eb !important;
  border-color: #2f54eb !important;
  box-shadow: 0 4px 12px rgba(47, 84, 235, 0.26);
}
.settings-tabs :deep(.ant-tabs-tab.ant-tabs-tab-active .ant-tabs-tab-btn) {
  color: #fff !important;
}
.settings-tabs :deep(.ant-tabs-ink-bar) {
  display: none !important;
}
.settings-tabs :deep(.ant-tabs-content-holder) {
  background: transparent;
}
</style>
