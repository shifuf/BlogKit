<template>
  <div class="admin-page">
    <a-card :bordered="false">
      <template #title>邮件订阅</template>
      <template #extra>
        <a-space>
          <a-input-search
            v-model:value="keyword"
            allow-clear
            placeholder="搜索邮箱"
            style="width: 240px"
            @search="load"
          />
          <a-button @click="load">刷新</a-button>
        </a-space>
      </template>
      <a-alert
        type="info"
        show-icon
        class="tip"
        message="前台弹窗订阅的邮箱列表。启用中的邮箱会在新文章发布时收到通知（需消息平台就绪）。"
      />
      <a-table
        row-key="id"
        :loading="loading"
        :columns="columns"
        :data-source="filtered"
        :pagination="{ pageSize: 20, showTotal: t => `共 ${t} 条` }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '已退订' }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-space>
              <a-popconfirm v-if="record.enabled" title="确认停用该订阅？" @confirm="disable(record)">
                <a>停用</a>
              </a-popconfirm>
              <a-popconfirm title="确认删除？" @confirm="remove(record)">
                <a class="danger">删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import request from '@/utils/request';
import { formatDateTime } from '@/utils/date';

const loading = ref(false);
const keyword = ref('');
const rows = ref([]);

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '邮箱', dataIndex: 'email', ellipsis: true },
  { title: '状态', dataIndex: 'enabled', width: 100 },
  {
    title: '订阅时间',
    dataIndex: 'createdAt',
    width: 180,
    customRender: ({ text }) => formatDateTime(text)
  },
  {
    title: '退订时间',
    dataIndex: 'unsubscribedAt',
    width: 180,
    customRender: ({ text }) => (text ? formatDateTime(text) : '—')
  },
  { title: '操作', dataIndex: 'action', width: 140 }
];

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase();
  if (!q) return rows.value;
  return rows.value.filter(item => String(item.email || '').toLowerCase().includes(q));
});

const load = async () => {
  loading.value = true;
  try {
    rows.value = (await request.get('/admin/subscriptions/email')) || [];
  } finally {
    loading.value = false;
  }
};

const disable = async record => {
  await request.put(`/admin/subscriptions/email/${record.id}/disable`);
  message.success('已停用');
  await load();
};

const remove = async record => {
  await request.delete(`/admin/subscriptions/email/${record.id}`);
  message.success('已删除');
  await load();
};

onMounted(load);
</script>

<style scoped>
.admin-page { padding: 16px; }
.tip { margin-bottom: 16px; }
.danger { color: #ff4d4f; }
</style>
