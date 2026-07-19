<script setup lang="ts">
// 文章详情骨架屏：在客户端切换文章(SSR 命中前)展示，结构对齐 hero + 正文 + 操作区，
// 减少「白屏 → 内容跳变」的突兀感。纯展示组件，无逻辑。
</script>

<template>
  <div class="article-skeleton" aria-hidden="true">
    <section class="article-skeleton__hero">
      <div class="article-skeleton__hero-inner">
        <div class="article-skeleton__chips">
          <span class="sk-bar" style="width: 56px"></span>
          <span class="sk-bar" style="width: 72px"></span>
          <span class="sk-bar" style="width: 64px"></span>
        </div>
        <div class="sk-bar sk-bar--title" style="width: 70%"></div>
        <div class="sk-bar sk-bar--title" style="width: 46%"></div>
        <div class="article-skeleton__meta">
          <span class="sk-bar" style="width: 80px"></span>
          <span class="sk-bar" style="width: 96px"></span>
          <span class="sk-bar" style="width: 70px"></span>
          <span class="sk-bar" style="width: 88px"></span>
        </div>
      </div>
      <div class="article-skeleton__wave" aria-hidden="true">
        <svg viewBox="0 0 1440 96" preserveAspectRatio="none" focusable="false">
          <path d="M0 56L48 61.3C96 67 192 77 288 72C384 67 480 45 576 37.3C672 29 768 35 864 45.3C960 56 1056 72 1152 72C1248 72 1344 56 1392 48L1440 40V96H0Z" />
        </svg>
      </div>
    </section>

    <div class="article-skeleton__body page-container page-container--narrow">
      <div class="sk-bar sk-bar--reading" style="width: 240px"></div>

      <div class="article-skeleton__lines">
        <div class="sk-bar sk-line" style="width: 100%"></div>
        <div class="sk-bar sk-line" style="width: 96%"></div>
        <div class="sk-bar sk-line" style="width: 88%"></div>
        <div class="sk-bar sk-line" style="width: 100%"></div>
        <div class="sk-bar sk-line" style="width: 64%"></div>
        <div class="sk-bar sk-block"></div>
        <div class="sk-bar sk-line" style="width: 92%"></div>
        <div class="sk-bar sk-line" style="width: 100%"></div>
        <div class="sk-bar sk-line" style="width: 78%"></div>
      </div>

      <div class="article-skeleton__actions">
        <span class="sk-bar sk-circle"></span>
        <span class="sk-bar sk-circle"></span>
        <span class="sk-bar sk-circle"></span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.article-skeleton {
  --sk-base: color-mix(in srgb, var(--text-muted, #94a3b8) 22%, var(--bg-card, #fff));
  --sk-shine: color-mix(in srgb, var(--text-muted, #94a3b8) 10%, var(--bg-card, #fff));
}

.sk-bar {
  display: block;
  height: 14px;
  border-radius: 8px;
  background: linear-gradient(100deg, var(--sk-base) 30%, var(--sk-shine) 50%, var(--sk-base) 70%);
  background-size: 200% 100%;
  animation: sk-shimmer 1.3s ease-in-out infinite;
}

.article-skeleton__hero {
  position: relative;
  margin-bottom: 8px;
  padding: 60px 0 64px;
  background: linear-gradient(135deg, color-mix(in srgb, var(--theme-color, #409eff) 78%, #1e293b), color-mix(in srgb, var(--theme-color, #409eff) 55%, #0f172a));
}

.article-skeleton__hero .sk-bar {
  --sk-base: rgba(255, 255, 255, 0.22);
  --sk-shine: rgba(255, 255, 255, 0.4);
}

.article-skeleton__hero-inner {
  width: min(1100px, calc(100vw - 32px));
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.article-skeleton__chips {
  display: flex;
  gap: 10px;
}

.article-skeleton__chips .sk-bar {
  height: 22px;
  border-radius: 999px;
}

.sk-bar--title {
  height: 28px;
  border-radius: 10px;
}

.article-skeleton__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 6px;
}

.article-skeleton__meta .sk-bar {
  height: 13px;
}

.article-skeleton__wave {
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 56px;
  line-height: 0;
}

.article-skeleton__wave svg {
  width: 100%;
  height: 100%;
  fill: var(--bg-body, #f8fafc);
}

.article-skeleton__body {
  padding-top: 16px;
}

.sk-bar--reading {
  height: 20px;
  margin-bottom: 24px;
}

.article-skeleton__lines {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sk-line {
  height: 15px;
}

.sk-block {
  height: 120px;
  border-radius: 12px;
  margin: 6px 0;
}

.article-skeleton__actions {
  display: flex;
  justify-content: center;
  gap: 28px;
  margin-top: 40px;
}

.sk-circle {
  width: 52px;
  height: 52px;
  border-radius: 14px;
}

@keyframes sk-shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .sk-bar {
    animation: none;
  }
}
</style>
