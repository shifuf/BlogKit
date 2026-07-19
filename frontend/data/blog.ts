import type { Category } from '~/types/blog';
const asset = (path: string) => `/layout-interaction/static/picture/${path}`;
export const siteMeta = {
  name: '我的博客',
  description: '记录技术、项目与生活',
  logoLight: asset('site-logo.png'),
  logoDark: asset('site-logo-dark.png'),
  avatar: asset('site-avatar.png'),
  hero: '',
  github: '',
  twitter: ''
};
export const categories: Category[] = [{
  slug: 'soft',
  label: '软件资源',
  description: '软件、工具和主题资源发布。',
  accent: '#f97316'
}, {
  slug: 'soft-use',
  label: '软件使用',
  description: '软件配置、排错与使用笔记。',
  accent: '#10b981'
}, {
  slug: 'frontend',
  label: '代码编程',
  description: '前端、服务端和工程实践。',
  accent: '#409eff'
}, {
  slug: 'android',
  label: 'Android',
  description: 'Android 开发与生态记录。',
  accent: '#22c55e'
}, {
  slug: 'wordpress',
  label: 'WordPress',
  description: 'WordPress 主题、插件和站点优化。',
  accent: '#21759b'
}, {
  slug: 'electron',
  label: 'Electron',
  description: '桌面端开发、打包和常见问题。',
  accent: '#64748b'
}, {
  slug: 'kotlin',
  label: 'Kotlin',
  description: 'Kotlin 语法和实践。',
  accent: '#7c3aed'
}, {
  slug: 'php',
  label: 'PHP',
  description: 'PHP 项目、框架和部署。',
  accent: '#6366f1'
}, {
  slug: 'node',
  label: 'Node',
  description: 'Node.js 服务端与工具链。',
  accent: '#16a34a'
}, {
  slug: 'vue',
  label: 'Vue',
  description: 'Vue 3、Nuxt 和组件实践。',
  accent: '#42b883'
}, {
  slug: 'javascript',
  label: 'JavaScript',
  description: 'JavaScript 基础、框架与工程化。',
  accent: '#eab308'
}, {
  slug: 'uniapp',
  label: 'UniApp',
  description: '跨端应用开发。',
  accent: '#0ea5e9'
}, {
  slug: 'cpp',
  label: 'C/C++',
  description: '底层语言、工程和调试记录。',
  accent: '#0f766e'
}, {
  slug: 'reverse',
  label: '汇编逆向',
  description: '逆向、安全和汇编资料。',
  accent: '#ef4444'
}, {
  slug: 'asm-doc',
  label: '汇编文档',
  description: '汇编学习文档整理。',
  accent: '#dc2626'
}, {
  slug: 'portable',
  label: '便携软件',
  description: '软件便携化和调试笔记。',
  accent: '#f43f5e'
}, {
  slug: 'operation',
  label: '运维',
  description: '服务器、网络和自动化运维。',
  accent: '#0891b2'
}];
