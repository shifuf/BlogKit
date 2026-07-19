<script setup lang="ts">
// 全屏加载页：SSR 直出，随 HTML 立即显示；待应用水合且所有 await 数据(useAsyncData/站点设置)解析完成后淡出释放。
// 用 useState 保证 SSR 与客户端初始一致(默认显示)，避免水合闪烁。
const appLoading = useState('app-loading', () => true);
const nuxtApp = useNuxtApp();

const release = () => {
  appLoading.value = false;
};

if (import.meta.client) {
  // app:suspense:resolve 在首屏所有异步数据解析、应用挂载完成后触发 —— 即"数据加载完再释放"。
  nuxtApp.hook('app:suspense:resolve', () => {
    // 留一帧让首屏内容绘制好，再淡出，避免白屏闪一下。
    requestAnimationFrame(() => requestAnimationFrame(release));
  });
  // 路由切换完成也释放，避免 suspense hook 在部分场景不触发导致遮罩常驻挡点击。
  nuxtApp.hook('page:finish', () => {
    requestAnimationFrame(() => requestAnimationFrame(release));
  });
  // 兜底：hook 未触发时最多 3s 强制释放，避免遮罩常驻挡交互。
  onMounted(() => {
    setTimeout(release, 3000);
  });
}
</script>

<template>
  <Transition name="app-loading-fade">
    <div v-if="appLoading" class="app-loading" aria-hidden="true">
      <div class="app-loading__inner">
        <div class="app-loading__spinner"></div>
        <p class="app-loading__text">正在加载…</p>
      </div>
    </div>
  </Transition>
</template>

<style>
.app-loading {
  position: fixed;
  inset: 0;
  z-index: 99999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  pointer-events: auto;
}
.app-loading-fade-leave-active,
.app-loading-fade-leave-to {
  pointer-events: none !important;
}
:root[data-theme='dark'] .app-loading {
  background: #14161a;
}
.app-loading__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}
.app-loading__spinner {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 3px solid rgba(64, 158, 255, 0.18);
  border-top-color: #409eff;
  animation: app-loading-spin 0.8s linear infinite;
}
.app-loading__text {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
  letter-spacing: 0.08em;
}
:root[data-theme='dark'] .app-loading__text {
  color: #9aa3af;
}
@keyframes app-loading-spin {
  to {
    transform: rotate(360deg);
  }
}
.app-loading-fade-leave-active {
  transition: opacity 0.35s ease;
}
.app-loading-fade-leave-to {
  opacity: 0;
}
</style>
