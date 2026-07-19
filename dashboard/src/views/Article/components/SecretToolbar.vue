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
      </div>
    </template>
  </DropdownToolbar>
</template>

<script setup>
import { ref } from 'vue';
import { DropdownToolbar } from 'md-editor-v3';
import { EyeInvisibleOutlined, UserOutlined, MessageOutlined } from '@ant-design/icons-vue';
const emit = defineEmits(['select']);
const visible = ref(false);
const onChange = next => {
  visible.value = next;
};
const choose = gate => {
  visible.value = false;
  emit('select', gate);
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
  color: #1f2329;
}
.bd-secret-menu__item:hover {
  background: #f2f3f5;
}
</style>
