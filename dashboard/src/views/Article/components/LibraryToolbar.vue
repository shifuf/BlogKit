<template>
  <NormalToolbar title="从图库选择" @onClick="emit('open')">
    <template #trigger>
      <FolderOpenOutlined />
    </template>
  </NormalToolbar>
</template>

<script setup>
import { NormalToolbar } from 'md-editor-v3';
import { FolderOpenOutlined } from '@ant-design/icons-vue';

const emit = defineEmits(['open']);
</script>
