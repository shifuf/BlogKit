/**
 * DevTools 切换设备宽度时，部分浏览器对 media 查询重算不及时。
 * 用 matchMedia 同步 class，并在变化时强制一次 reflow。
 */
export default defineNuxtPlugin(() => {
  if (!import.meta.client) return;
  const mq = window.matchMedia('(max-width: 840px)');
  const apply = () => {
    document.documentElement.classList.toggle('is-narrow-viewport', mq.matches);
    document.documentElement.classList.toggle('is-wide-viewport', !mq.matches);
    // 强制 reflow，避免 DevTools 设备切换后样式残留
    void document.body?.offsetHeight;
  };
  apply();
  if (typeof mq.addEventListener === 'function') {
    mq.addEventListener('change', apply);
  } else if (typeof mq.addListener === 'function') {
    mq.addListener(apply);
  }
  window.addEventListener('resize', apply, { passive: true });
  window.addEventListener('orientationchange', apply);
});
