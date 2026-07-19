<template>
  <div class="admin-page">
    <a-card :bordered="false">
      <template #title>操作日志</template>
      <template #extra>
        <a-space wrap>
          <a-select v-model:value="moduleFilter" allow-clear placeholder="模块" style="width: 150px" @change="reload">
            <a-select-option v-for="item in moduleOptions" :key="item" :value="item">{{ item }}</a-select-option>
          </a-select>
          <a-input-search
            v-model:value="keywordInput"
            allow-clear
            placeholder="搜索账号、动作、说明"
            style="width: 260px"
            @search="searchLogs"
          />
          <a-button @click="resetFilters">重置</a-button>
          <a-popconfirm
            title="确定清理 30 天前的操作日志吗？"
            ok-text="清理"
            cancel-text="取消"
            @confirm="() => clearLogs(30)"
          >
            <a-button :loading="clearing">清理 30 天前</a-button>
          </a-popconfirm>
          <a-popconfirm
            title="确定清空全部操作日志吗？该操作不可恢复。"
            ok-text="清空"
            cancel-text="取消"
            @confirm="() => clearLogs()"
          >
            <a-button danger :loading="clearing">清空日志</a-button>
          </a-popconfirm>
        </a-space>
      </template>

      <a-table
        row-key="id"
        :columns="columns"
        :data-source="logs"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'module'">
            <a-tag color="blue">{{ record.module }}</a-tag>
          </template>
          <template v-if="column.dataIndex === 'action'">
            <a-tag :color="actionColor(record.action)">{{ record.action }}</a-tag>
          </template>
          <template v-if="column.dataIndex === 'target'">
            <span>{{ record.targetType || '-' }}</span>
            <div v-if="record.targetId" class="muted">ID: {{ record.targetId }}</div>
          </template>
          <template v-if="column.dataIndex === 'description'">
            <span class="description">{{ record.description || '-' }}</span>
          </template>
          <template v-if="column.dataIndex === 'username'">
            <strong>{{ record.username }}</strong>
            <div class="muted" v-if="record.userId">ID: {{ record.userId }}</div>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { clearAuditLogs, getAuditLogList } from '@/api/AuditLog';
import { formatDateTime } from '@/utils/date';
const loading = ref(false);
const clearing = ref(false);
const logs = ref([]);
const moduleFilter = ref(undefined);
const keywordInput = ref('');
const keyword = ref('');
const moduleOptions = ['后台登录', '文章管理', '分类管理', '标签管理', '评论管理', '用户管理', '附件管理', '动态管理', '友链管理', '系统配置', '菜单管理'];
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: total => `共 ${total} 条`
});
const columns = [{
  title: 'ID',
  dataIndex: 'id',
  width: 80
}, {
  title: '模块',
  dataIndex: 'module',
  width: 120
}, {
  title: '动作',
  dataIndex: 'action',
  width: 120
}, {
  title: '目标',
  dataIndex: 'target',
  width: 150
}, {
  title: '说明',
  dataIndex: 'description',
  ellipsis: true
}, {
  title: '管理员',
  dataIndex: 'username',
  width: 140
}, {
  title: '时间',
  dataIndex: 'createdAt',
  width: 180,
  customRender: ({
    text
  }) => formatDateTime(text)
}];
const loadLogs = async () => {
  loading.value = true;
  try {
    const page = await getAuditLogList({
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      module: moduleFilter.value,
      keyword: keyword.value
    });
    logs.value = page.items;
    pagination.total = page.totalItems;
  } finally {
    loading.value = false;
  }
};
const reload = () => {
  pagination.current = 1;
  loadLogs();
};
const searchLogs = value => {
  keywordInput.value = (value ?? keywordInput.value).trim();
  keyword.value = keywordInput.value;
  reload();
};
const resetFilters = () => {
  moduleFilter.value = undefined;
  keywordInput.value = '';
  keyword.value = '';
  reload();
};
const clearLogs = async days => {
  clearing.value = true;
  try {
    const result = await clearAuditLogs(days);
    message.success(`已清理 ${result?.deleted || 0} 条日志`);
    reload();
  } finally {
    clearing.value = false;
  }
};
const handleTableChange = page => {
  pagination.current = page.current;
  pagination.pageSize = page.pageSize;
  loadLogs();
};
const actionColor = action => {
  if (!action) return 'default';
  if (action.includes('删除')) return 'red';
  if (action.includes('创建') || action.includes('上传')) return 'green';
  if (action.includes('审核') || action.includes('导入')) return 'orange';
  if (action.includes('登录')) return 'purple';
  return 'blue';
};
onMounted(loadLogs);
</script>

<style lang="scss" scoped>
.admin-page {
  padding: 16px;
}

.description {
  word-break: break-word;
}

.muted {
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
}
</style>
