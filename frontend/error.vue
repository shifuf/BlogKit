<script setup lang="ts">
import type { NuxtError } from '#app';
const props = defineProps<{
  error: NuxtError;
}>();
const statusCode = computed(() => Number(props.error?.statusCode) || 500);
const isNotFound = computed(() => statusCode.value === 404);
const title = computed(() => (isNotFound.value ? '页面走丢了' : '服务开小差了'));
const subtitle = computed(() => {
  if (isNotFound.value) return '抱歉，你访问的页面不存在、已被移动或链接已失效。';
  return '抱歉，服务器暂时无法处理你的请求，请稍后再试。';
});
const handleHome = () => clearError({ redirect: '/' });
const handleBack = () => {
  if (import.meta.client && window.history.length > 1) {
    window.history.back();
    return;
  }
  clearError({ redirect: '/' });
};
const countdown = ref(5);
let countdownTimer: ReturnType<typeof setInterval> | null = null;
onMounted(() => {
  if (!isNotFound.value) return;
  countdownTimer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) {
      if (countdownTimer) clearInterval(countdownTimer);
      countdownTimer = null;
      handleHome();
    }
  }, 1000);
});
onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
});
useHead({
  title: computed(() => `${statusCode.value} - ${title.value} - 我的博客`)
});
</script>

<template>
  <div class="error-page" :data-status="statusCode">
    <div class="error-page__inner">
      <div class="error-page__code" aria-hidden="true">{{ statusCode }}</div>
      <h1 class="error-page__title">{{ title }}</h1>
      <p class="error-page__subtitle">{{ subtitle }}</p>
      <div class="error-page__actions">
        <button class="error-page__btn error-page__btn--primary" type="button" @click="handleHome">返回首页</button>
        <button class="error-page__btn" type="button" @click="handleBack">返回上一页</button>
      </div>
      <p v-if="isNotFound" class="error-page__hint">{{ countdown }} 秒后将自动跳转至首页…</p>
      <p v-else class="error-page__hint">如果问题持续出现，欢迎通过站点页脚的联系方式反馈。</p>
    </div>
  </div>
</template>

<style scoped>
.error-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(60% 60% at 50% 0%, rgba(64, 158, 255, 0.16), transparent 70%),
    var(--bg-page, #f4f6f8);
  color: var(--text-primary, #1f2733);
}
.error-page__inner {
  width: 100%;
  max-width: 520px;
  text-align: center;
}
.error-page__code {
  font-size: clamp(96px, 22vw, 168px);
  font-weight: 800;
  line-height: 1;
  letter-spacing: 2px;
  background: linear-gradient(135deg, var(--theme-color, #409eff), #7c6ccf);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  user-select: none;
}
.error-page__title {
  margin: 12px 0 0;
  font-size: 26px;
  font-weight: 700;
}
.error-page__subtitle {
  margin: 12px auto 0;
  max-width: 420px;
  font-size: 15px;
  line-height: 1.7;
  color: var(--text-secondary, #647184);
}
.error-page__actions {
  margin-top: 28px;
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}
.error-page__btn {
  min-width: 120px;
  padding: 10px 22px;
  font-size: 15px;
  border-radius: 10px;
  border: 1px solid var(--border-color, #e3e8f7);
  background: var(--bg-card, #fff);
  color: var(--text-primary, #1f2733);
  cursor: pointer;
  transition: transform 0.15s ease, filter 0.15s ease, box-shadow 0.15s ease;
}
.error-page__btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.08);
}
.error-page__btn--primary {
  border-color: transparent;
  background: var(--theme-color, #409eff);
  color: #fff;
}
.error-page__btn--primary:hover {
  filter: brightness(1.06);
}
.error-page__hint {
  margin-top: 22px;
  font-size: 13px;
  color: var(--text-muted, #94a0b3);
}
</style>
