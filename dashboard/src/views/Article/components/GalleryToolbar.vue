<template>
  <NormalToolbar title="图片组（九宫格）" @onClick="trigger">
    <template #trigger>
      <AppstoreOutlined />
    </template>
  </NormalToolbar>
  <input
    ref="fileRef"
    type="file"
    accept="image/*"
    multiple
    style="display: none"
    @change="onChange"
  />
</template>

<script setup>
import { ref } from 'vue';
import { NormalToolbar } from 'md-editor-v3';
import { AppstoreOutlined } from '@ant-design/icons-vue';
const emit = defineEmits(['pick']);
const fileRef = ref();
const trigger = () => fileRef.value?.click();
const onChange = e => {
  const files = Array.from(e.target.files || []);
  if (files.length) emit('pick', files);
  e.target.value = '';
};
</script>
