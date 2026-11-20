<template>
  <NormalToolbar title="插入当前北京时间" @onClick="emit('insert', formatBeijingTime())">
    <template #trigger><ClockCircleOutlined /></template>
  </NormalToolbar>
</template>

<script setup>
import { NormalToolbar } from 'md-editor-v3';
import { ClockCircleOutlined } from '@ant-design/icons-vue';
import { formatBeijingTime } from '@/utils/date';
const emit = defineEmits(['insert']);
</script>
