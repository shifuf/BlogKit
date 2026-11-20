<script setup lang="ts">
const props = defineProps<{ status?: string }>();
const approved = computed(() => !props.status || props.status === 'APPROVED');
const label = computed(() => approved.value ? '已通过'
  : props.status === 'PENDING' ? '待审核 · 仅自己可见' : '未通过 · 仅自己可见');
</script>

<template>
  <span class="comment-status-badge" :class="{ 'comment-status-badge--approved': approved }" role="status">{{ label }}</span>
</template>

<style scoped>
.comment-status-badge {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  border: 1px solid color-mix(in srgb, #b86508 45%, transparent);
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.5;
  color: #875012;
  background: #fff6df;
  white-space: normal;
}
.comment-status-badge--approved { color: #276447; background: #edf7ef; border-color: #9fc7ab; }
:global(html[data-theme='dark']) .comment-status-badge { color: #f3c77d; background: #352b19; border-color: #776238; }
:global(html[data-theme='dark']) .comment-status-badge--approved { color: #98d9b1; background: #1d3329; border-color: #406b54; }
</style>
