<template>
  <div class="admin-page">
    <a-card :bordered="false">
      <template #title>用户管理</template>
      <template #extra>
        <a-button type="primary" @click="openCreateModal">新增用户</a-button>
      </template>

      <a-table
        row-key="id"
        :columns="columns"
        :data-source="users"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'avatarUrl'">
            <a-avatar :src="record.avatarUrl || undefined">
              {{ avatarInitial(record) }}
            </a-avatar>
          </template>
          <template v-if="column.dataIndex === 'roleCode'">
            <a-tag :color="record.roleCode === 'ADMIN' ? 'red' : 'blue'">{{ record.roleCode }}</a-tag>
          </template>
          <template v-if="column.dataIndex === 'action'">
            <a-space>
              <a @click="openEditModal(record)">编辑</a>
              <a @click="resetPassword(record)">重置密码</a>
              <a-popconfirm title="确认删除该用户？" @confirm="removeUser(record)">
                <a class="danger-link">删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalOpen" :title="form.id ? '编辑用户' : '新增用户'" @ok="submitForm">
      <a-form layout="vertical" :model="form">
        <a-form-item label="用户名" required>
          <a-input v-model:value="form.username" placeholder="登录用户名" />
        </a-form-item>
        <a-form-item label="显示名称" required>
          <a-input v-model:value="form.displayName" placeholder="前台展示名称" />
        </a-form-item>
        <a-form-item label="密码" :required="!form.id">
          <a-input-password v-model:value="form.password" placeholder="编辑时留空表示不修改" />
        </a-form-item>
        <a-form-item label="头像">
          <div class="avatar-field">
            <a-avatar :size="64" :src="form.avatarUrl || undefined">
              {{ avatarInitial(form) }}
            </a-avatar>
            <a-space wrap>
              <a-upload
                :show-upload-list="false"
                accept="image/jpeg,image/png,image/gif,image/webp"
                :custom-request="uploadAvatar"
                :before-upload="beforeAvatarUpload"
              >
                <a-button :loading="uploadingAvatar">
                  <template #icon><upload-outlined /></template>
                  上传头像
                </a-button>
              </a-upload>
              <a-button @click="avatarPickerOpen = true">从图库选择</a-button>
              <a-button v-if="form.avatarUrl" @click="form.avatarUrl = ''">清除</a-button>
            </a-space>
          </div>
        </a-form-item>
        <a-form-item label="角色" required>
          <a-select v-model:value="form.roleCode">
            <a-select-option value="USER">USER</a-select-option>
            <a-select-option value="ADMIN">ADMIN</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:open="resetOpen" title="设置新密码" @ok="submitReset">
      <a-alert message="请通过安全方式将新密码告知用户。重置后所有设备将退出。" type="info" show-icon />
      <a-input-password v-model:value="resetValue" placeholder="输入 12 至 128 位新密码" autocomplete="new-password" style="margin-top:16px" />
    </a-modal>
    <AttachmentPicker
      v-model:open="avatarPickerOpen"
      :multiple="false"
      :allow-crop="true"
      crop-preset="1:1"
      crop-title="裁剪头像"
      @pick="applyAvatarFromLibrary"
    />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { UploadOutlined } from '@ant-design/icons-vue';
import { uploadImageWithCrop } from '@/utils/imageCrop';
import AttachmentPicker from '@/components/AttachmentPicker.vue';
import { createUser, deleteUser, getUserList, resetUserPassword, updateUser } from '@/api/User';
import { formatDateTime } from '@/utils/date';
const loading = ref(false);
const uploadingAvatar = ref(false);
const avatarPickerOpen = ref(false);
const users = ref([]);
const modalOpen = ref(false);
const form = reactive({
  id: null,
  username: '',
  password: '',
  displayName: '',
  avatarUrl: '',
  roleCode: 'USER'
});
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
  title: '头像',
  dataIndex: 'avatarUrl',
  width: 90,
  align: 'center'
}, {
  title: '用户名',
  dataIndex: 'username'
}, {
  title: '显示名称',
  dataIndex: 'displayName'
}, {
  title: '角色',
  dataIndex: 'roleCode',
  width: 120
}, {
  title: '创建时间',
  dataIndex: 'createdAt',
  width: 220,
  customRender: ({
    text
  }) => formatDateTime(text)
}, {
  title: '操作',
  dataIndex: 'action',
  width: 220,
  fixed: 'right'
}];
const resetForm = () => {
  Object.assign(form, {
    id: null,
    username: '',
    password: '',
    displayName: '',
    avatarUrl: '',
    roleCode: 'USER'
  });
};
const loadUsers = async () => {
  loading.value = true;
  try {
    const page = await getUserList({
      page: pagination.current - 1,
      size: pagination.pageSize
    });
    users.value = page.items;
    pagination.total = page.totalItems;
  } finally {
    loading.value = false;
  }
};
const handleTableChange = page => {
  pagination.current = page.current;
  pagination.pageSize = page.pageSize;
  loadUsers();
};
const openCreateModal = () => {
  resetForm();
  modalOpen.value = true;
};
const openEditModal = record => {
  Object.assign(form, {
    ...record,
    password: ''
  });
  modalOpen.value = true;
};
const avatarInitial = record => {
  const value = record.displayName || record.username || 'U';
  return value.slice(0, 1).toUpperCase();
};
const beforeAvatarUpload = file => {
  if (!file.type?.startsWith('image/')) {
    message.warning('请选择图片文件');
    return false;
  }
  if (file.size > 5 * 1024 * 1024) {
    message.warning('头像大小不能超过 5MB');
    return false;
  }
  return true;
};
const applyAvatarFromLibrary = urls => {
  const url = (urls || []).filter(Boolean)[0];
  if (!url) return;
  form.avatarUrl = url;
  message.success('已选择头像');
};
const uploadAvatar = async ({
  file,
  onSuccess,
  onError
}) => {
  uploadingAvatar.value = true;
  try {
    const result = await uploadImageWithCrop(file, { publicAccess: true, defaultPreset: '1:1', title: '裁剪头像' });
    if (!result) {
      onError?.(new Error('cancelled'));
      return;
    }
    form.avatarUrl = result.url;
    message.success('头像已上传');
    onSuccess?.(result);
  } catch (error) {
    onError?.(error);
  } finally {
    uploadingAvatar.value = false;
  }
};
const submitForm = async () => {
  if (!form.username.trim() || !form.displayName.trim() || !form.id && !form.password.trim()) {
    message.warning('请填写用户名、显示名称和密码');
    return;
  }
  const payload = {
    username: form.username.trim(),
    password: form.password || undefined,
    displayName: form.displayName.trim(),
    avatarUrl: form.avatarUrl?.trim(),
    roleCode: form.roleCode
  };
  if (form.id) {
    await updateUser(form.id, payload);
    message.success('用户已更新');
  } else {
    await createUser(payload);
    message.success('用户已创建');
  }
  modalOpen.value = false;
  await loadUsers();
};
const resetOpen = ref(false), resetValue = ref(''), resetTarget = ref(null);
const resetPassword = record => { resetTarget.value = record; resetValue.value = ''; resetOpen.value = true; };
const submitReset = async () => {
  if (resetValue.value.length < 12 || resetValue.value.length > 128) { message.warning('请输入 12 至 128 位新密码'); return; }
  await resetUserPassword(resetTarget.value.id, resetValue.value);
  resetValue.value = ''; resetOpen.value = false; message.success('密码已重置，所有设备已退出');
};
const removeUser = async record => {
  await deleteUser(record.id);
  message.success('用户已删除');
  await loadUsers();
};
onMounted(loadUsers);
</script>

<style lang="scss" scoped>
.admin-page {
  padding: 16px;
}

.danger-link {
  color: #ff4d4f;
}

.avatar-field {
  display: flex;
  align-items: center;
  gap: 16px;
}
</style>
