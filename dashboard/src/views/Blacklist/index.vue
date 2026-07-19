<template>
  <div class="admin-page">
    <a-card :bordered="false" class="stats-card">
      <template #title>IP 访问统计</template>
      <template #extra>
        <a-space wrap>
          <a-input-search
            v-model:value="statKeyword"
            allow-clear
            placeholder="搜索 IP"
            style="width: 220px"
            @search="loadStats"
          />
          <a-button @click="loadStats">刷新</a-button>
        </a-space>
      </template>
      <a-alert
        type="info"
        show-icon
        class="tip"
        message="统计公开 API 请求次数（异步累计）。可按访问热度一键拉黑 IP。"
      />
      <a-table
        row-key="id"
        :loading="statsLoading"
        :columns="statColumns"
        :data-source="statRows"
        :pagination="statPagination"
        size="middle"
        @change="onStatTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'blacklisted'">
            <a-tag :color="record.blacklisted ? 'red' : 'default'">{{ record.blacklisted ? '已拉黑' : '正常' }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a v-if="!record.blacklisted" @click="blockIp(record)">拉黑</a>
            <span v-else class="muted">-</span>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-card :bordered="false">
      <template #title>访问黑名单</template>
      <template #extra>
        <a-space wrap>
          <a-input-search
            v-model:value="keyword"
            allow-clear
            placeholder="搜索类型 / 目标值"
            style="width: 240px"
            @search="load"
          />
          <a-button type="primary" @click="openCreate">添加拉黑</a-button>
        </a-space>
      </template>

      <a-alert
        type="info"
        show-icon
        class="tip"
        message="支持拉黑用户 ID、IP、游客 guestKey。IP 行会显示累计访问次数；拉黑后拦截评论等互动写入。"
      />

      <a-table
        row-key="id"
        :loading="loading"
        :columns="columns"
        :data-source="rows"
        :pagination="pagination"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'targetType'">
            <a-tag :color="typeColor(record.targetType)">{{ typeLabel(record.targetType) }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'visitCount'">
            <span>{{ record.targetType === 'IP' ? (record.visitCount || 0) : '-' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'enabled'">
            <a-switch :checked="record.enabled" @change="checked => toggle(record, checked)" />
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-popconfirm title="确认删除该黑名单？" @confirm="remove(record)">
              <a class="danger">删除</a>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalOpen" title="添加黑名单" ok-text="保存" :confirm-loading="saving" @ok="submit">
      <a-form layout="vertical">
        <a-form-item label="类型" required>
          <a-select v-model:value="form.targetType">
            <a-select-option value="IP">IP 地址</a-select-option>
            <a-select-option value="USER">用户 ID</a-select-option>
            <a-select-option value="GUEST">游客 guestKey</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="目标值" required>
          <a-input v-model:value="form.targetValue" :placeholder="placeholder" />
        </a-form-item>
        <a-form-item label="原因">
          <a-textarea v-model:value="form.reason" :rows="3" :maxlength="500" show-count />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import request from '@/utils/request';
import { formatDateTime } from '@/utils/date';

const loading = ref(false);
const statsLoading = ref(false);
const saving = ref(false);
const modalOpen = ref(false);
const keyword = ref('');
const statKeyword = ref('');
const rows = ref([]);
const statRows = ref([]);
const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showTotal: total => `共 ${total} 条`
});
const statPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: total => `共 ${total} 条`
});
const form = reactive({ targetType: 'IP', targetValue: '', reason: '' });

const columns = [
  { title: '类型', dataIndex: 'targetType', width: 100 },
  { title: '目标值', dataIndex: 'targetValue', ellipsis: true },
  { title: '访问次数', dataIndex: 'visitCount', width: 100 },
  { title: '原因', dataIndex: 'reason', ellipsis: true },
  { title: '启用', dataIndex: 'enabled', width: 90 },
  {
    title: '创建时间',
    dataIndex: 'createdAt',
    width: 170,
    customRender: ({ text }) => formatDateTime(text)
  },
  { title: '操作', dataIndex: 'action', width: 90 }
];

const statColumns = [
  { title: 'IP', dataIndex: 'ipAddress', ellipsis: true },
  { title: '访问次数', dataIndex: 'visitCount', width: 100 },
  { title: '归属地', dataIndex: 'region', width: 140, ellipsis: true, customRender: ({ text }) => text || '—' },
  {
    title: '首次访问',
    dataIndex: 'firstSeenAt',
    width: 160,
    customRender: ({ text }) => formatDateTime(text)
  },
  {
    title: '最近访问',
    dataIndex: 'lastSeenAt',
    width: 160,
    customRender: ({ text }) => formatDateTime(text)
  },
  { title: '状态', dataIndex: 'blacklisted', width: 90 },
  { title: '操作', dataIndex: 'action', width: 90 }
];

const placeholder = computed(() => {
  if (form.targetType === 'USER') return '填写用户数字 ID';
  if (form.targetType === 'GUEST') return '填写游客 guestKey';
  return '填写 IP，如 203.0.113.10';
});

const typeLabel = type => ({ IP: 'IP', USER: '用户', GUEST: '游客' }[type] || type);
const typeColor = type => ({ IP: 'orange', USER: 'red', GUEST: 'purple' }[type] || 'default');

const load = async () => {
  loading.value = true;
  try {
    const page = await request.get('/admin/blacklist', {
      params: {
        page: pagination.current - 1,
        size: pagination.pageSize,
        keyword: keyword.value.trim() || undefined
      }
    });
    rows.value = page?.items || page?.content || [];
    pagination.total = page?.totalItems ?? page?.totalElements ?? 0;
  } finally {
    loading.value = false;
  }
};

const loadStats = async () => {
  statsLoading.value = true;
  try {
    const page = await request.get('/admin/blacklist/ip-stats', {
      params: {
        page: statPagination.current - 1,
        size: statPagination.pageSize,
        keyword: statKeyword.value.trim() || undefined
      }
    });
    statRows.value = page?.items || page?.content || [];
    statPagination.total = page?.totalItems ?? page?.totalElements ?? 0;
  } finally {
    statsLoading.value = false;
  }
};

const onTableChange = page => {
  pagination.current = page.current;
  pagination.pageSize = page.pageSize;
  load();
};

const onStatTableChange = page => {
  statPagination.current = page.current;
  statPagination.pageSize = page.pageSize;
  loadStats();
};

const openCreate = () => {
  form.targetType = 'IP';
  form.targetValue = '';
  form.reason = '';
  modalOpen.value = true;
};

const submit = async () => {
  if (!form.targetValue.trim()) {
    message.warning('请填写目标值');
    return;
  }
  saving.value = true;
  try {
    await request.post('/admin/blacklist', {
      targetType: form.targetType,
      targetValue: form.targetValue.trim(),
      reason: form.reason.trim()
    });
    message.success('已加入黑名单');
    modalOpen.value = false;
    pagination.current = 1;
    await Promise.all([load(), loadStats()]);
  } finally {
    saving.value = false;
  }
};

const blockIp = async record => {
  await request.post('/admin/blacklist', {
    targetType: 'IP',
    targetValue: record.ipAddress,
    reason: `访问次数 ${record.visitCount}`
  });
  message.success('已拉黑该 IP');
  await Promise.all([load(), loadStats()]);
};

const toggle = async (record, enabled) => {
  await request.put(`/admin/blacklist/${record.id}/enabled`, { enabled });
  record.enabled = enabled;
  message.success(enabled ? '已启用' : '已停用');
};

const remove = async record => {
  await request.delete(`/admin/blacklist/${record.id}`);
  message.success('已删除');
  await Promise.all([load(), loadStats()]);
};

onMounted(() => {
  load();
  loadStats();
});
</script>

<style scoped>
.admin-page { padding: 16px; }
.stats-card { margin-bottom: 16px; }
.tip { margin-bottom: 16px; }
.danger { color: #ff4d4f; }
.muted { color: #8c8c8c; }
</style>
