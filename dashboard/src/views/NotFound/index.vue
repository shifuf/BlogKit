<template>
  <div class="not-found">
    <a-result status="404" title="404" :sub-title="`抱歉，您访问的页面不存在或已被移动。${countdown} 秒后自动返回工作台。`">
      <template #extra>
        <a-space>
          <a-button type="primary" @click="goHome">立即返回工作台</a-button>
          <a-button @click="goBack">返回上一页</a-button>
        </a-space>
      </template>
    </a-result>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
const router = useRouter();
const countdown = ref(5);
let timer = null;
const clearTimer = () => {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
};
const goHome = () => {
  clearTimer();
  router.push('/');
};
const goBack = () => {
  clearTimer();
  if (window.history.length > 1) {
    router.back();
  } else {
    router.push('/');
  }
};
onMounted(() => {
  timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) {
      goHome();
    }
  }, 1000);
});
onUnmounted(clearTimer);
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 200px);
}
</style>
