import { defineMusicPlayerElement } from '~/utils/define-music-player';

/**
 * 客户端启动时立刻注册 <music-player> 自定义元素。
 * 文章/动态 Markdown 渲染出标签后，浏览器会自动调用 connectedCallback 挂载黑胶播放器。
 */
export default defineNuxtPlugin(() => {
  defineMusicPlayerElement(); // layout-v5-3 volume-pct-mobile
});
