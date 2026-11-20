<template>
  
  <div>
    <a-menu
      v-model:openKeys="state.openKeys"
      v-model:selectedKeys="state.selectedKeys"
      mode="inline"
      theme="light"
      :inline-collapsed="false"
      :items="items"
      @click="handleSelect"
    >
    </a-menu>
  </div>
</template>
<script setup>
import { reactive, h, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { CloudUploadOutlined, DashboardOutlined, FileSyncOutlined, FolderOutlined, FormOutlined, LinkOutlined, MailOutlined, MessageOutlined, ReadOutlined, SafetyCertificateOutlined, ScheduleOutlined, SearchOutlined, SettingOutlined, StopOutlined, TagsOutlined, TeamOutlined, UnorderedListOutlined } from '@ant-design/icons-vue';
const route = useRouter();
const emit = defineEmits(['navigate']);
const state = reactive({
  collapsed: false,
  selectedKeys: [],
  openKeys: [],
  preOpenKeys: []
});
const items = reactive([{
  key: 'dashboard',
  icon: () => h(DashboardOutlined),
  label: '工作台',
  title: '工作台'
}, {
  key: 'users',
  icon: () => h(TeamOutlined),
  label: '用户管理',
  title: '用户管理'
}, {
  key: 'articleGroup',
  icon: () => h(ReadOutlined),
  label: '文章管理',
  title: '文章管理',
  children: [{
    key: 'articles',
    icon: () => h(ReadOutlined),
    label: '文章列表',
    title: '文章列表'
  }, {
    key: 'articleSchedule',
    icon: () => h(ScheduleOutlined),
    label: '定时发布',
    title: '定时发布'
  }, {
    key: 'articleEdit',
    icon: () => h(FormOutlined),
    label: '文章编辑',
    title: '文章编辑'
  }]
}, {
  key: 'categories',
  icon: () => h(FolderOutlined),
  label: '分类管理',
  title: '分类管理'
}, {
  key: 'tags',
  icon: () => h(TagsOutlined),
  label: '标签管理',
  title: '标签管理'
}, {
  key: 'comments',
  icon: () => h(MessageOutlined),
  label: '评论管理',
  title: '评论管理'
}, {
  key: 'attachments',
  icon: () => h(CloudUploadOutlined),
  label: '附件管理',
  title: '附件管理'
}, {
  key: 'auditLogs',
  icon: () => h(FileSyncOutlined),
  label: '操作日志',
  title: '操作日志'
}, {
  key: 'searchStats',
  icon: () => h(SearchOutlined),
  label: '搜索统计',
  title: '搜索统计'
}, {
  key: 'opsHealth',
  icon: () => h(SafetyCertificateOutlined),
  label: '站点运维',
  title: '站点运维'
}, {
  key: 'subscriptions',
  icon: () => h(MailOutlined),
  label: '邮件订阅',
  title: '邮件订阅'
}, {
  key: 'blacklist',
  icon: () => h(StopOutlined),
  label: '访问黑名单',
  title: '访问黑名单'
}, {
  key: 'dynamics',
  icon: () => h(UnorderedListOutlined),
  label: '动态管理',
  title: '动态管理'
}, {
  key: 'friendLinks',
  icon: () => h(LinkOutlined),
  label: '友链管理',
  title: '友链管理'
}, {
  key: 'settings',
  icon: () => h(SettingOutlined),
  label: '系统配置',
  title: '系统配置'
}, {
  key: 'settings-import-export',
  icon: () => h(FileSyncOutlined),
  label: '导入导出',
  title: '导入导出'
}]);
const handleSelect = ({
  key
}) => {
  emit('navigate');
  // Keep the editor's query (and unsaved page instance) when its menu is tapped again.
  if (route.currentRoute.value.name === key) return;
  route.push({
    name: key
  });
};
// 子项 key -> 所属分组 key，用于「当前路由属于某分组时才展开该分组」。
const parentByChild = {};
items.forEach(item => {
  if (item.children) {
    item.children.forEach(child => {
      parentByChild[child.key] = item.key;
    });
  }
});
// 默认收起所有分组；仅当当前路由是某分组的子项时展开该分组。
const openKeysForRoute = name => {
  const parent = parentByChild[name];
  return parent ? [parent] : [];
};
const currentKey = route.currentRoute.value.name;
state.selectedKeys = [currentKey];
state.openKeys = openKeysForRoute(currentKey);
const stopRouteSync = route.afterEach(to => {
  state.selectedKeys = [to.name];
  // 切到分组子项时自动展开其分组；切到普通菜单时不强制改变展开态，避免「点其他菜单分组自动弹出」。
  const parent = parentByChild[to.name];
  if (parent && !state.openKeys.includes(parent)) {
    state.openKeys = [parent];
  }
});
onBeforeUnmount(stopRouteSync);
</script>

<style scoped lang="scss">
/* 侧边菜单微调：选中项圆角主题底色 + 间距，更贴合整体蓝色调。 */
:deep(.ant-menu-inline) {
  border-inline-end: 0 !important;
}
:deep(.ant-menu-item),
:deep(.ant-menu-submenu-title) {
  border-radius: 8px !important;
  margin-inline: 8px !important;
  width: calc(100% - 16px) !important;
}
:deep(.ant-menu-item-selected) {
  background: rgba(47, 84, 235, 0.1) !important;
  color: #2f54eb !important;
  font-weight: 600;
}
:deep(.ant-menu-item:hover),
:deep(.ant-menu-submenu-title:hover) {
  color: #2f54eb !important;
}
</style>
