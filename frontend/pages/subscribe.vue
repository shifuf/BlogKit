<script setup lang="ts">
// 兼容旧 /subscribe 路由：打开首页并弹出订阅弹窗
await navigateTo({ path: '/', query: { subscribe: '1' } })
</script>

<template>
  <div />
</template>
