<script setup lang="ts">
const {
  siteSettings
} = useSiteSettings();
const signTitleParts = computed(() => (siteSettings.value.homeSignTitle || '').split(/\n|<br\s*\/?>/i).map(part => part.trim()).filter(Boolean));
</script>

<template>
  <section v-if="siteSettings.homeSignEnabled" class="sign-banner sign-banner--large" style="--sb-rotate: 45deg; --sb-right: 40px;">
    <div class="sign-banner__text">
      <h2 class="sign-banner__title">
        <template v-for="(part, index) in signTitleParts" :key="`${part}-${index}`">
          <br v-if="index > 0" />
          {{ part }}
        </template>
      </h2>
      <div v-if="siteSettings.homeSignSubtitle" class="sign-banner__subtitle">{{ siteSettings.homeSignSubtitle }}</div>
    </div>

    <div class="sign-banner__icons" aria-hidden="true">
      <div class="sign-banner__icons-inner">
        <div class="sign-banner__column" data-direction="down">
          <div class="sign-banner__track">
            <div class="sign-banner__card sign-banner__card--no-bg">
              <svg class="sign-banner__card-icon sign-banner__card-icon--multi" width="58" height="58" viewBox="0 0 48 48">
                <path fill="#7CB342" d="M12 29c0 1.1-.9 2-2 2s-2-.9-2-2v-9c0-1.1.9-2 2-2s2 .9 2 2zm28 0c0 1.1-.9 2-2 2s-2-.9-2-2v-9c0-1.1.9-2 2-2s2 .9 2 2zM22 40c0 1.1-.9 2-2 2s-2-.9-2-2v-9c0-1.1.9-2 2-2s2 .9 2 2zm8 0c0 1.1-.9 2-2 2s-2-.9-2-2v-9c0-1.1.9-2 2-2s2 .9 2 2zM14 18v15c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V18zM24 8c-6 0-9.7 3.6-10 8h20c-.3-4.4-4-8-10-8z" />
              </svg>
            </div>
            <div class="sign-banner__card sign-banner__card--no-bg">
              <svg class="sign-banner__card-icon sign-banner__card-icon--multi" width="58" height="58" viewBox="0 0 48 48">
                <path fill="#7CB342" d="M24 4C13 4 4 13 4 24s9 20 20 20 20-9 20-20S35 4 24 4z" />
                <path fill="#0277BD" d="M45 24c0 11.7-9.5 21-21 21S3 35.7 3 24 12.3 3 24 3s21 9.3 21 21zM15 17l5 5 10-10 4 4 6-6-2-2-4 4-4-4-10 10-5-5-8 8 2 2z" />
              </svg>
            </div>
            <div class="sign-banner__card sign-banner__card--no-bg">
              <svg class="sign-banner__card-icon sign-banner__card-icon--multi" width="58" height="58" viewBox="0 0 48 48">
                <path fill="#FF9800" d="M44 18v-4H34V4h-4v10h-4V4h-4v10h-4V4h-4v10H4v4h10v4H4v4h10v4H4v4h10v10h4V34h4v10h4V34h4v10h4V34h10v-4H34v-4h10v-4H34v-4z" />
                <path fill="#4CAF50" d="M8 12v24c0 2.2 1.8 4 4 4h24c2.2 0 4-1.8 4-4V12c0-2.2-1.8-4-4-4H12c-2.2 0-4 1.8-4 4z" />
                <path fill="#37474F" d="M31 31H17c-1.1 0-2-.9-2-2V19c0-1.1.9-2 2-2h14c1.1 0 2 .9 2 2v10c0 1.1-.9 2-2 2z" />
              </svg>
            </div>
            <div class="sign-banner__card sign-banner__card--no-bg">
              <svg class="sign-banner__card-icon sign-banner__card-icon--multi" width="58" height="58" viewBox="0 0 48 48">
                <path fill="#263238" d="M34.3 23.9c-1.6-2.3-2.9-3.7-3.6-6.6-.7-2.9.2-2.1-.4-4.6-.6-2.7-2.7-5.7-8.6-4-2.7 1.4-2.4 4.4-1.9 10.5-.1 1.3-1.1 2.6-2 3.7-2 2.5-4.5 8.1-3.9 9.4.5-.1 6.8 9.5 6.8 9.7 2-.2 4.8-.3 8.6.1-.2-1.4.2-2.8.5-4.3-1 .9-2.8 1.9-4.5 2.2-1.5.3-4-.2-5.2-1.7.9-.1 1.4-.5 1.2-1.2-.2-.8-3.4-2.9-4.1-3.9-.8-1-.3-3.3.7-5.2.8-1.5 2.3-3.7 2.5-5.2 1.8-.7 6.5-1 7.1-.3.5 1.5 1.5 4.1 2.3 5.9 1.2 2.9 1 5.5.9 7.4 3.4-2.8 1.6-6.7 1.6-6.7s2.8 1.3 2.9 3.9c.1 2.1-.8 3.8-1 4.1.1 0 2.1.9 2.2.9.4 0 1.2-.3 1.2-.3 1.1-4.7-.6-8.4-2.3-10.8z" />
                <path fill="#FFC107" d="M39.3 37.6c-.4-.2-1.1-.5-1.7-1.4-.3-.5-.2-1.9-.7-2.5-.3-.4-.7-.2-.8-.2-.9.2-3 1.6-4.4 0-.5-.5-1.4-.8-1.9.1-.4.6-.2 1.4-.3 4.1-.2 1.7-.5 2.7-.5 3.7 0 1.8.8 2.6 2.6 2.6 1.1 0 1.8-.4 2.5-1.1.5-.5.9-.7 2.3-1.7 1.1-.7 2.8-1.6 3.1-1.9.5-.5.7-1.2-.2-1.7zM19.2 37.9c-1-1.6-1.1-1.9-1.8-2.9-.6-1-1.9-2.9-2.7-2.9-.6 0-.9.3-1.3.7-.4.4-.8 1.3-1.5 1.8-.6.5-2.3.4-2.7 1-.4.6.4 1.5.4 3 0 .6-.5 1-.6 1.4-.1.5-.2.8 0 1.2.4.6.9.8 4.3 1.5 1.8.4 3.5 1.4 4.6 1.5 1.1.1 3 0 3-2.7.1-1.6-.8-2-1.7-3.6z" />
              </svg>
            </div>
          </div>
        </div>

        <div class="sign-banner__column" data-direction="up">
          <div class="sign-banner__track">
            <div class="sign-banner__card" style="background-color: #ec4899; border-radius: 16px; --sb-icon-color: #ffffff;">
              <svg class="sign-banner__card-icon sign-banner__card-icon--single" width="58" height="58" viewBox="0 0 24 24" fill="#fff"><path d="M7.171 2.757 10.414 6h3.171l3.243-3.243a1 1 0 0 1 1.414 1.414L16.414 6H18.5A3.5 3.5 0 0 1 22 9.5v8a3.5 3.5 0 0 1-3.5 3.5h-13A3.5 3.5 0 0 1 2 17.5v-8A3.5 3.5 0 0 1 5.5 6h2.085L5.757 4.171a1 1 0 1 1 1.414-1.414zM18.5 8h-13A1.5 1.5 0 0 0 4 9.5v8A1.5 1.5 0 0 0 5.5 19h13a1.5 1.5 0 0 0 1.5-1.5v-8A1.5 1.5 0 0 0 18.5 8zM8 11a1 1 0 0 1 1 1v2a1 1 0 1 1-2 0v-2a1 1 0 0 1 1-1zm8 0a1 1 0 0 1 1 1v2a1 1 0 1 1-2 0v-2a1 1 0 0 1 1-1z" /></svg>
            </div>
            <div class="sign-banner__card" style="background-color: #334155; border-radius: 24px; --sb-icon-color: #ffffff;">
              <svg class="sign-banner__card-icon sign-banner__card-icon--single" width="58" height="58" viewBox="0 0 24 24" fill="#fff"><path d="M15.8 8.2c-.5 0-1 .1-1.8.4-.8.3-1.4.6-2.3.6-.9 0-1.5-.3-2.3-.6-.7-.3-1.1-.4-1.4-.3-1.2 0-2.2.7-2.9 1.8-1.3 2.2-.6 6.3 1.4 9 .9 1.4 1.5 1.9 1.8 1.9.2 0 .4-.1.8-.2 1-.5 1.8-.7 2.9-.7s1.8.2 2.8.6c.4.2.6.2.9.2.4 0 .8-.4 1.8-1.8.3-.4.5-.8.7-1.2-1.5-1.2-2.5-3-2.5-5.3 0-1.6.5-3 1.4-4.1-.4-.1-.8-.2-1.3-.3zM14.8 4.4c-.8.9-2.1 1.7-3.3 1.6-.2-1.3.5-2.6 1.2-3.5.8-.9 2.1-1.6 3.2-1.7.2 1.3-.3 2.6-1.1 3.6z" /></svg>
            </div>
            <div class="sign-banner__card" style="background-color: #06b6d4; border-radius: 50%; --sb-icon-color: #ffffff;">
              <svg class="sign-banner__card-icon sign-banner__card-icon--single" width="58" height="58" viewBox="0 0 24 24" fill="#fff"><path d="m24 12-5.657 5.657-1.414-1.414L21.172 12l-4.243-4.243 1.414-1.414L24 12zM2.828 12l4.243 4.243-1.414 1.414L0 12l5.657-5.657 1.414 1.414L2.828 12zM9.788 21H7.66l6.552-18h2.128L9.788 21z" /></svg>
            </div>
            <div class="sign-banner__card" style="background-color: #3b82f6; border-radius: 16px; --sb-icon-color: #ffffff;">
              <svg class="sign-banner__card-icon sign-banner__card-icon--single" width="58" height="58" viewBox="0 0 24 24" fill="#fff"><path d="M13 19.9c2.3-.5 4-2.5 4-4.9v-3c0-.7-.1-1.4-.4-2H7.4c-.3.6-.4 1.3-.4 2v3c0 2.4 1.7 4.4 4 4.9V14h2v5.9zM5.5 17.7A6.9 6.9 0 0 1 5 15H2v-2h3v-1c0-.6.1-1.3.2-1.9L3 8.9 4 7.1l2 1.2.3-.3h11.5l.3.3 2-1.2 1 1.7-2.2 1.3c.1.6.2 1.2.2 1.9v1h3v2h-3c0 1-.2 1.9-.5 2.7l2.5 1.4-1 1.7-2.5-1.5A7 7 0 0 1 12 22a7 7 0 0 1-5.4-2.6L4 20.9l-1-1.7 2.5-1.5zM8 6a4 4 0 0 1 8 0H8z" /></svg>
            </div>
          </div>
        </div>

        <div class="sign-banner__column" data-direction="down">
          <div class="sign-banner__track">
            <div class="sign-banner__card" style="background-color: #22c55e; border-radius: 18px; --sb-icon-color: #ffffff;">
              <svg class="sign-banner__card-icon sign-banner__card-icon--single" width="58" height="58" viewBox="0 0 24 24" fill="#fff"><path d="M4 6h16v12H4zM7 9h10v2H7zm0 4h7v2H7z" /></svg>
            </div>
            <div class="sign-banner__card sign-banner__card--no-bg">
              <svg class="sign-banner__card-icon sign-banner__card-icon--multi" width="58" height="58" viewBox="0 0 48 48">
                <path fill="#607D8B" d="M39.6 27.2c.1-.7.2-1.4.2-2.2s-.1-1.5-.2-2.2l4.5-3.2c.4-.3.6-.9.3-1.4L40 10.8c-.3-.5-.8-.7-1.3-.4l-5 2.3c-1.2-.9-2.4-1.6-3.8-2.2l-.5-5.5c-.1-.5-.5-.9-1-.9h-8.6c-.5 0-1 .4-1 .9l-.5 5.5c-1.4.6-2.7 1.3-3.8 2.2l-5-2.3c-.5-.2-1.1 0-1.3.4l-4.3 7.4c-.3.5-.1 1.1.3 1.4l4.5 3.2c-.1.7-.2 1.4-.2 2.2s.1 1.5.2 2.2L4 30.4c-.4.3-.6.9-.3 1.4L8 39.2c.3.5.8.7 1.3.4l5-2.3c1.2.9 2.4 1.6 3.8 2.2l.5 5.5c.1.5.5.9 1 .9h8.6c.5 0 1-.4 1-.9l.5-5.5c1.4-.6 2.7-1.3 3.8-2.2l5 2.3c.5.2 1.1 0 1.3-.4l4.3-7.4c.3-.5.1-1.1-.3-1.4l-4.2-3.2z" />
                <path fill="#455A64" d="M24 13c-6.6 0-12 5.4-12 12s5.4 12 12 12 12-5.4 12-12-5.4-12-12-12zm0 17c-2.8 0-5-2.2-5-5s2.2-5 5-5 5 2.2 5 5-2.2 5-5 5z" />
              </svg>
            </div>
            <div class="sign-banner__card" style="background-color: #0ea5e9; border-radius: 50%; --sb-icon-color: #ffffff;">
              <svg class="sign-banner__card-icon sign-banner__card-icon--single" width="58" height="58" viewBox="0 0 24 24" fill="#fff"><path d="M7 18 3 12l4-6h3l-4 6 4 6zm10 0 4-6-4-6h-3l4 6-4 6zm-5.2 2H9.7l2.5-16h2.1z" /></svg>
            </div>
            <div class="sign-banner__card sign-banner__card--no-bg">
              <svg class="sign-banner__card-icon sign-banner__card-icon--multi" width="58" height="58" viewBox="0 0 48 48">
                <rect x="6" y="6" width="36" height="36" rx="6" fill="#ff9800" />
                <rect x="13" y="13" width="22" height="22" rx="3" fill="#43a047" />
                <rect x="19" y="19" width="10" height="10" fill="#37474f" />
              </svg>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
/* 关键尺寸/定位前置为组件 scoped 样式：Nuxt 会随组件内联进 SSR <head>，
   先于体量很大的 main.scss 生效，避免首屏刷新时 svg 以默认超大尺寸+黑色闪出大黑块(FOUC)。 */
.sign-banner.sign-banner--large {
  position: relative;
  overflow: hidden;
}
.sign-banner__icons {
  position: absolute;
  top: 50%;
  right: var(--sb-right, 40px);
  width: 430px;
  height: 360px;
  transform: translateY(-50%) rotate(var(--sb-rotate, 45deg));
  pointer-events: none;
}
.sign-banner__icons-inner {
  display: flex;
  gap: 18px;
  height: 100%;
  justify-content: flex-end;
}
.sign-banner__column {
  width: 86px;
  height: 360px;
  overflow: hidden;
}
.sign-banner__card {
  width: 86px;
  height: 86px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--sb-icon-color, #ffffff);
}
.sign-banner__card-icon {
  width: 58px;
  height: 58px;
  display: block;
  fill: currentColor;
}
.sign-banner__card-icon--multi {
  fill: initial;
}

/* scoped 样式特异性高于全局 main.scss，故移动端断点(与 main.scss 一致 700px)需在此重申，
   否则上面的桌面尺寸会盖过全局移动端响应式，导致手机端图标不缩放。 */
@media (max-width: 700px) {
  .sign-banner__icons {
    width: 210px;
    height: 220px;
    right: var(--sb-right-mobile, 12px);
  }
  .sign-banner__icons-inner {
    gap: 10px;
  }
  .sign-banner__column {
    width: 52px;
    height: 220px;
  }
  .sign-banner__card {
    width: 52px;
    height: 52px;
  }
  .sign-banner__card-icon {
    width: 34px;
    height: 34px;
  }
}
</style>
