<template>
  <DropdownToolbar :visible="visible" title="隐藏内容" @onChange="onChange">
    <template #default>
      <EyeInvisibleOutlined />
    </template>
    <template #overlay>
      <div class="bd-secret-menu">
        <div class="bd-secret-menu__item" @click="choose('login')">
          <UserOutlined />
          <span>登录可见</span>
        </div>
        <div class="bd-secret-menu__item" @click="choose('comment')">
          <MessageOutlined />
          <span>评论(回复)可见</span>
        </div>
        <button type="button" class="bd-secret-menu__item" @click="choose('password')">
          <LockOutlined /><span>密码可见</span>
        </button>
        <button type="button" class="bd-secret-menu__item" @click="choose('users')">
          <TeamOutlined /><span>指定用户可见</span>
        </button>
      </div>
    </template>
  </DropdownToolbar>
  <a-modal v-model:open="modalOpen" :title="gate === 'password' ? '插入密码可见区块' : '插入指定用户可见区块'"
    :width="440" :z-index="22000" ok-text="插入区块" @ok="confirm" @cancel="reset">
    <a-form layout="vertical" class="bd-secret-form">
      <a-form-item v-if="gate === 'password'" label="访问密码">
        <a-input-password v-model:value="password" autocomplete="new-password" placeholder="设置此区块的独立密码" @pressEnter="confirm" />
        <p class="bd-secret-help">保存后密码仅以哈希保存，无法找回；最多 72 字节。</p>
      </a-form-item>
      <a-form-item v-else label="允许查看的用户">
        <a-select v-model:value="selectedUsers" mode="multiple" show-search option-filter-prop="label"
          :dropdown-style="{ zIndex: 23000 }" placeholder="搜索并选择用户" style="width: 100%">
          <a-select-option v-for="user in users" :key="user.id" :value="user.id" :label="user.displayName || user.username">
            {{ user.displayName || user.username }}（{{ user.username }}）
          </a-select-option>
        </a-select>
        <p class="bd-secret-help">仅所选用户、文章作者与管理员可见；最多选择 100 人。</p>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { ref } from 'vue';
import { DropdownToolbar } from 'md-editor-v3';
import { EyeInvisibleOutlined, UserOutlined, MessageOutlined, LockOutlined, TeamOutlined } from '@ant-design/icons-vue';
import { message } from 'ant-design-vue';
import { encodeBlockPayload } from '@/utils/blog-markdown';
defineProps({ users: { type: Array, default: () => [] } });
const emit = defineEmits(['select']);
const visible = ref(false);
const modalOpen = ref(false);
const gate = ref('password');
const password = ref('');
const selectedUsers = ref([]);
const reset = () => { password.value = ''; selectedUsers.value = []; };
const onChange = next => {
  visible.value = next;
};
const choose = next => {
  visible.value = false;
  if (next === 'login' || next === 'comment') { emit('select', next); return; }
  reset();
  gate.value = next;
  modalOpen.value = true;
};
const confirm = () => {
  if (gate.value === 'password' && (!password.value.trim() || new TextEncoder().encode(password.value).length > 72)) {
    message.warning('请设置非空且不超过 72 字节的密码'); return;
  }
  if (gate.value === 'users' && (!selectedUsers.value.length || selectedUsers.value.length > 100)) {
    message.warning('请选择 1 至 100 位可见用户'); return;
  }
  const payload = gate.value === 'password' ? { password: password.value } : { userIds: selectedUsers.value };
  emit('select', `${gate.value} ${encodeBlockPayload(payload)}`);
  modalOpen.value = false;
  reset();
};
</script>

<style scoped>
.bd-secret-menu {
  padding: 4px;
  min-width: 160px;
}
.bd-secret-menu__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  color: var(--ant-color-text, #1f2329);
  background: transparent;
  border: 0;
  width: 100%;
  text-align: left;
}
.bd-secret-menu__item:hover {
  background: var(--ant-color-fill-tertiary, #f2f3f5);
}
.bd-secret-form { padding-top: 16px; }
.bd-secret-help { margin: 8px 0 0; font-size: 12px; color: var(--ant-color-text-secondary, #64748b); }
</style>
