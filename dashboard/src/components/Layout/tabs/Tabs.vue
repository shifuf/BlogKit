<template>
    <a-tabs :active-key="activeKey" hide-add type="editable-card" @edit="onEdit" @tabClick="tabClick">
    <a-tab-pane v-for="pane in panes" :key="pane.path" :tab="pane.title" :closable="pane.path !== '/'"></a-tab-pane>
    <template #rightExtra>
      <a-dropdown v-if="panes.length > 1" v-model:open="overflowOpen" :trigger="['click']" placement="bottomRight">
        <a-button type="text" class="tabs-overflow-trigger" aria-label="更多标签页" aria-haspopup="menu" :aria-expanded="overflowOpen">
          <ellipsis-outlined />
        </a-button>
        <template #overlay>
          <a-menu :selected-keys="[activeKey]" class="tabs-overflow-menu" @click="selectOverflow">
            <a-menu-item v-for="pane in panes" :key="pane.path">
              <span class="tabs-overflow-title">{{ pane.title }}</span>
              <a-button v-if="pane.path !== '/'" type="text" size="small" :aria-label="`关闭${pane.title}`" @click.stop="remove(pane.path)">
                <close-outlined />
              </a-button>
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </template>
  </a-tabs>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useRouter } from 'vue-router';
import { CloseOutlined, EllipsisOutlined } from '@ant-design/icons-vue';
import { tabsStore } from '../../../stores/modules/tabs';
const route = useRoute();
const router = useRouter();
const store = tabsStore();
const panes = computed(() => {
  return store.getTabs;
});
const activeKey = computed(() => route.path);
const overflowOpen = ref(false);
const remove = targetKey => {
  if (targetKey === '/') return;
  const index = store.tabsList.findIndex(pane => pane.path === targetKey);
  if (index < 0) return;
  store.tabsList.splice(index, 1);
  if (targetKey !== route.path) return;
  overflowOpen.value = false;
  const next = store.tabsList[Math.max(0, index - 1)];
  router.push(next ? next.fullPath || next.path : '/');
};
watch(() => route.fullPath, () => {
  overflowOpen.value = false;
  addTab();
});
const beforeRefresh = () => {
  if (route.path != '/login') {
    window.addEventListener('beforeunload', () => {
      sessionStorage.setItem('tabs', JSON.stringify(store.tabsList));
    });
    let tabSesson = sessionStorage.getItem('tabs');
    if (tabSesson) {
      let oldTabs = JSON.parse(tabSesson);
      if (oldTabs.length > 0) {
        store.tabsList = oldTabs;
      }
    }
  }
};
const onEdit = (targetKey, action) => {
  if (action === 'remove') remove(targetKey);
};
const addTab = () => {
  const {
    path,
    meta
  } = route;
  const tab = {
    path: path,
    fullPath: route.fullPath,
    title: meta.title
  };
  store.addTab(tab);
};
onMounted(() => {
  beforeRefresh();
  addTab();
});
const tabClick = key => {
  if (key === route.path) return;
  const pane = store.tabsList.find(tab => tab.path === key);
  router.push(pane?.fullPath || key);
};
const selectOverflow = ({ key }) => {
  overflowOpen.value = false;
  tabClick(key);
};
</script>
<style scoped lang="scss">
/* 顶部多页签：胶囊卡片、激活态主题色、hover 过渡，弱化原生 editable-card 边框。 */
:deep(.ant-tabs) {
  width: 100%;
  height: 100%;
}
:deep(.ant-tabs-nav) {
  margin: 0;
  height: 100%;
}
:deep(.ant-tabs-nav::before) {
  border: 0 !important;
}
:deep(.ant-tabs-nav-wrap) {
  align-items: center;
}
/* Ant 4's built-in overflow is hover-only; use its native click Dropdown above. */
:deep(.ant-tabs-nav-operations) {
  display: none !important;
}
.tabs-overflow-trigger {
  min-width: 40px;
}
.tabs-overflow-menu {
  max-height: min(60vh, 400px);
  max-width: calc(100vw - 24px);
  overflow-y: auto;
}
.tabs-overflow-menu :deep(.ant-dropdown-menu-title-content) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.tabs-overflow-title {
  overflow-wrap: anywhere;
}
:deep(.ant-tabs-tab) {
  height: 32px;
  margin: 0 6px 0 0 !important;
  padding: 0 14px !important;
  border: 1px solid #e6e8ef !important;
  border-radius: 8px !important;
  background: #f6f7fb !important;
  color: #5a6072 !important;
  font-size: 13px;
  transition: color 0.2s ease, background 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}
:deep(.ant-tabs-tab:hover) {
  color: #2f54eb !important;
  border-color: #b7c4f5 !important;
}
:deep(.ant-tabs-tab.ant-tabs-tab-active) {
  background: #2f54eb !important;
  border-color: #2f54eb !important;
  box-shadow: 0 4px 12px rgba(47, 84, 235, 0.28);
}
:deep(.ant-tabs-tab.ant-tabs-tab-active .ant-tabs-tab-btn) {
  color: #fff !important;
  text-shadow: none;
}
:deep(.ant-tabs-tab .ant-tabs-tab-remove) {
  color: inherit !important;
  margin-left: 6px;
}
:deep(.ant-tabs-tab.ant-tabs-tab-active .ant-tabs-tab-remove) {
  color: rgba(255, 255, 255, 0.85) !important;
}
:deep(.ant-tabs-ink-bar) {
  display: none !important;
}
</style>
