<template>
    <a-tabs v-model:activeKey="activeKey" hide-add type="editable-card" @edit="onEdit" @tabClick="tabClick">
    <a-tab-pane v-for="pane in panes" :key="pane.path" :tab="pane.title" :closable="pane.path !== '/'"></a-tab-pane>
  </a-tabs>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useRouter } from 'vue-router';
import { tabsStore } from '../../../stores/modules/tabs';
const route = useRoute();
const router = useRouter();
const store = tabsStore();
const panes = computed(() => {
  return store.getTabs;
});
const activeKey = ref();
const setActiveKey = () => {
  activeKey.value = route.path;
};
const remove = targetKey => {
  if (targetKey === '/') {
    return;
  }
  let lastIndex = 0;
  store.tabsList.forEach((pane, i) => {
    if (pane.path === targetKey) {
      lastIndex = i - 1;
    }
  });
  store.tabsList = store.tabsList.filter(pane => pane.path !== targetKey);
  if (lastIndex >= 0) {
    activeKey.value = store.tabsList[lastIndex].path;
  } else {
    activeKey.value = store.tabsList[0].path;
  }
  router.push({
    path: activeKey.value
  });
};
watch(() => route.path, () => {
  setActiveKey();
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
const onEdit = targetKey => {
  remove(targetKey);
};
const addTab = () => {
  const {
    path,
    meta
  } = route;
  const tab = {
    path: path,
    title: meta.title
  };
  store.addTab(tab);
};
onMounted(() => {
  beforeRefresh();
  setActiveKey();
  addTab();
});
const tabClick = kye => {
  router.push(kye);
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
