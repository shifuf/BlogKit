<template>
  <div class="admin-page">
    <a-card :bordered="false" class="exchange-card">
      <template #title>友链页 · 本站信息</template>
      <template #extra>
        <a-button type="primary" :loading="exchangeSaving" @click="saveExchange">保存本站信息</a-button>
      </template>
      <a-alert
        type="info"
        show-icon
        class="exchange-tip"
        message="展示在前台 /friends 页，供申请者复制互换。名称/简介/链接/图标可单独配置，不改动站点基础设置。"
      />
      <a-form layout="vertical" class="exchange-form">
        <a-row :gutter="16">
          <a-col :xs="24" :md="12">
            <a-form-item label="本站名称">
              <a-input v-model:value="exchange.friendExchangeName" placeholder="默认用站点名称" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="12">
            <a-form-item label="本站链接">
              <a-input v-model:value="exchange.friendExchangeUrl" placeholder="https://blog.example.com" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="12">
            <a-form-item label="本站图标 URL">
              <a-input v-model:value="exchange.friendExchangeLogo" placeholder="Logo / 头像地址" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="12">
            <a-form-item label="友链页说明文案">
              <a-input v-model:value="exchange.friendPageIntro" placeholder="顶部介绍" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="本站简介">
              <a-textarea v-model:value="exchange.friendExchangeBio" :rows="3" :maxlength="300" show-count />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <a-card :bordered="false">
      <template #title>友链管理</template>
      <template #extra>
        <a-button type="primary" @click="openCreateModal">新增友链</a-button>
      </template>

      <a-table row-key="id" :columns="columns" :data-source="items" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'logo'">
            <div class="logo-cell">
              <img v-if="record.logoUrl" class="logo-img" :src="record.logoUrl" :alt="record.name" />
              <span v-else class="logo-fallback">{{ initialOf(record.name) }}</span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'name'">
            <strong>{{ record.name }}</strong>
            <div class="muted">
              <a :href="record.url" target="_blank" rel="noopener noreferrer">{{ record.url }}</a>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'display'">
            <a-space size="small" wrap>
              <a-tag v-if="record.showInPage !== false" color="blue">友链页</a-tag>
              <a-tag v-if="record.showInFooter" color="cyan">页脚</a-tag>
              <a-tag v-if="record.visible" color="green">可见</a-tag>
              <a-tag v-else>隐藏</a-tag>
            </a-space>
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-space wrap>
              <a v-if="record.status === 'PENDING'" @click="reviewItem(record, true)">通过</a>
              <a v-if="record.status === 'PENDING'" class="danger-link" @click="reviewItem(record, false)">驳回</a>
              <a v-if="record.status !== 'APPROVED'" @click="recheckItem(record)">
                {{ recheckingId === record.id ? '检测中…' : '复检回链' }}
              </a>
              <a @click="openEditModal(record)">编辑</a>
              <a-popconfirm title="确认删除该友链？" @confirm="removeItem(record)">
                <a class="danger-link">删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalOpen" :title="form.id ? '编辑友链' : '新增友链'" @ok="submitForm">
      <a-form layout="vertical" :model="form">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="链接" required>
          <a-input v-model:value="form.url" placeholder="https://..." />
        </a-form-item>
        <a-form-item label="Logo URL">
          <a-input v-model:value="form.logoUrl" placeholder="留空则按名称首字生成" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="3" />
        </a-form-item>
        <a-form-item label="申请人邮箱">
          <a-input v-model:value="form.applicantEmail" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.displayOrder" style="width: 100%" />
        </a-form-item>
        <a-form-item label="展示位置">
          <a-space wrap>
            <a-checkbox v-model:checked="form.showInPage">友链页</a-checkbox>
            <a-checkbox v-model:checked="form.showInFooter">页脚</a-checkbox>
          </a-space>
          <div class="form-help">可同时勾选；申请默认只在友链页。</div>
        </a-form-item>
        <a-form-item label="前台可见">
          <a-switch v-model:checked="form.visible" />
        </a-form-item>
        <a-form-item label="审核邮件通知">
          <a-switch v-model:checked="form.notifyOnReview" />
          <div class="form-help">开启后通过/驳回时向申请人邮箱发送结果（需消息平台就绪）。</div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { createFriendLink, deleteFriendLink, listFriendLinks, recheckFriendLink, reviewFriendLink, updateFriendLink } from '@/api/FriendLink';
import { listSettings, saveSettingGroup } from '@/api/Settings';

const loading = ref(false);
const recheckingId = ref(null);
const exchangeSaving = ref(false);
const items = ref([]);
const modalOpen = ref(false);
const exchange = reactive({
  friendExchangeName: '',
  friendExchangeBio: '',
  friendExchangeUrl: '',
  friendExchangeLogo: '',
  friendPageIntro: ''
});
const form = reactive({
  id: null,
  name: '',
  url: '',
  logoUrl: '',
  description: '',
  applicantEmail: '',
  visible: true,
  showInPage: true,
  showInFooter: false,
  notifyOnReview: false,
  displayOrder: 0,
  status: 'APPROVED'
});

const initialOf = name => {
  const text = String(name || '友').trim();
  return text ? text.charAt(0).toUpperCase() : '友';
};
const statusLabel = status => ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[status] || status || '已通过');
const statusColor = status => ({ PENDING: 'orange', APPROVED: 'green', REJECTED: 'red' }[status] || 'default');

const columns = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: 'Logo', dataIndex: 'logo', width: 72 },
  { title: '名称 / 链接', dataIndex: 'name', ellipsis: true },
  { title: '审核', dataIndex: 'status', width: 90 },
  { title: '展示', dataIndex: 'display', width: 180 },
  { title: '邮箱', dataIndex: 'applicantEmail', width: 150, ellipsis: true },
  { title: '排序', dataIndex: 'displayOrder', width: 80 },
  { title: '操作', dataIndex: 'action', width: 220, fixed: 'right' }
];

const resetForm = () => Object.assign(form, {
  id: null,
  name: '',
  url: '',
  logoUrl: '',
  description: '',
  applicantEmail: '',
  visible: true,
  showInPage: true,
  showInFooter: false,
  notifyOnReview: false,
  displayOrder: 0,
  status: 'APPROVED'
});

const loadItems = async () => {
  loading.value = true;
  try {
    items.value = await listFriendLinks();
  } finally {
    loading.value = false;
  }
};

const loadExchange = async () => {
  try {
    const rows = await listSettings('basic');
    const map = Object.fromEntries((rows || []).map(item => [item.settingKey, item.settingValue]));
    exchange.friendExchangeName = map.friendExchangeName || map.siteName || '';
    exchange.friendExchangeBio = map.friendExchangeBio || map.siteDescription || '';
    exchange.friendExchangeUrl = map.friendExchangeUrl || '';
    exchange.friendExchangeLogo = map.friendExchangeLogo || map.profileAvatarUrl || '';
    exchange.friendPageIntro = map.friendPageIntro || '志同道合的站点。欢迎互换友链，申请后将自动检测回链。';
  } catch {
    // ignore
  }
};

const saveExchange = async () => {
  exchangeSaving.value = true;
  try {
    await saveSettingGroup('basic', {
      friendExchangeName: exchange.friendExchangeName || '',
      friendExchangeBio: exchange.friendExchangeBio || '',
      friendExchangeUrl: exchange.friendExchangeUrl || '',
      friendExchangeLogo: exchange.friendExchangeLogo || '',
      friendPageIntro: exchange.friendPageIntro || ''
    });
    message.success('本站友链信息已保存');
  } finally {
    exchangeSaving.value = false;
  }
};

const openCreateModal = () => {
  resetForm();
  modalOpen.value = true;
};

const openEditModal = record => {
  Object.assign(form, {
    ...record,
    showInPage: record.showInPage !== false,
    showInFooter: !!record.showInFooter,
    notifyOnReview: !!record.notifyOnReview,
    applicantEmail: record.applicantEmail || ''
  });
  modalOpen.value = true;
};

const submitForm = async () => {
  if (!form.name.trim() || !form.url.trim()) {
    message.warning('请填写名称和链接');
    return;
  }
  if (!form.showInPage && !form.showInFooter) {
    message.warning('请至少选择一个展示位置');
    return;
  }
  const payload = { ...form };
  if (form.id) {
    await updateFriendLink(form.id, payload);
    message.success('友链已更新');
  } else {
    await createFriendLink(payload);
    message.success('友链已创建');
  }
  modalOpen.value = false;
  await loadItems();
};

const reviewItem = async (record, approve) => {
  await reviewFriendLink(record.id, approve);
  message.success(approve ? '已通过' : '已驳回');
  await loadItems();
};

const recheckItem = async record => {
  recheckingId.value = record.id;
  try {
    const result = await recheckFriendLink(record.id);
    const approved = result?.status === 'APPROVED' && result?.visible;
    message[approved ? 'success' : 'warning'](
      approved
        ? '检测到回链，已自动通过'
        : '仍未检测到回链。若对方是 Vue/React 且友链只在接口/运行时渲染，请人工通过。'
    );
    await loadItems();
  } finally {
    recheckingId.value = null;
  }
};

const removeItem = async record => {
  await deleteFriendLink(record.id);
  message.success('友链已删除');
  await loadItems();
};

onMounted(() => {
  loadItems();
  loadExchange();
});
</script>

<style lang="scss" scoped>
.admin-page { padding: 16px; }
.exchange-card { margin-bottom: 16px; }
.exchange-tip { margin-bottom: 16px; }
.exchange-form { max-width: 960px; }
.danger-link { color: #ff4d4f; }
.muted { color: #8c8c8c; font-size: 12px; margin-top: 2px; word-break: break-all; }
.logo-cell { display: flex; align-items: center; justify-content: center; }
.logo-img {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  object-fit: cover;
  background: #f5f5f5;
}
.logo-fallback {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: #64748b;
  background: #eef2f7;
}
.form-help { margin-top: 6px; color: #8c8c8c; font-size: 12px; }
</style>
