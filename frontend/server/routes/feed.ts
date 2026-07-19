// /feed 是 /feed.xml 的别名：不少阅读器/用户习惯直接访问 /feed。
// 直接复用 feed.xml 的处理器，保证两个地址输出完全一致的 RSS。
export { default } from './feed.xml';
