/**
 * 全局滚动回顶：
 * - 路由跳转（非浏览器前进/后退）强制到顶部
 * - 首次进入 / 刷新 / bfcache 恢复后也回顶（文章详情、动态等页面一致）
 */
export default defineNuxtPlugin((nuxtApp) => {
  if (!import.meta.client) return;

  const scrollTopNow = () => {
    const top = { top: 0, left: 0, behavior: 'auto' as const };
    try {
      window.scrollTo(top);
      document.documentElement.scrollTop = 0;
      document.body.scrollTop = 0;
      document.scrollingElement && (document.scrollingElement.scrollTop = 0);
    } catch {
      // ignore
    }
  };

  // 首屏 / 刷新
  scrollTopNow();

  // 路由完成后回顶（前进后退由 router.scrollBehavior 的 savedPosition 处理）
  nuxtApp.hook('page:finish', () => {
    // 等布局/异步组件稳定后再滚，避免内容撑开后停在中段
    requestAnimationFrame(() => {
      requestAnimationFrame(scrollTopNow);
    });
  });

  // bfcache 返回时强制回顶
  window.addEventListener('pageshow', (event) => {
    if (event.persisted) scrollTopNow();
  });
});
