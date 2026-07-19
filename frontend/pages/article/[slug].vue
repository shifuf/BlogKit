<script setup lang="ts">
import { Copyright, FolderOpen, Lock, Link2, Sparkles, Star, Tag, ThumbsUp } from '@lucide/vue';
import { ElMessage } from 'element-plus';
import { type App, createApp, defineAsyncComponent, nextTick } from 'vue';
import type { Article, Category } from '~/types/blog';
import { buildCoverUrl, NO_COVER_IMAGE, toBlogArticle, toBlogCategory } from '~/utils/blog-adapter';
import { applyBlogContainer } from '~/utils/blog-markdown';
import { hasCommentedLocally, markCommentedLocally, getGuestKey } from '~/utils/blog-blocks';
import { defineMusicPlayerElement } from '~/utils/define-music-player';
import { sanitizeArticleHtml as sanitizeArticleHtmlBase } from '~/utils/sanitize-html';
import DownloadCard from '~/components/blog/DownloadCard.vue';
import SecretBlock from '~/components/blog/SecretBlock.vue';
import CustomLinkButton from '~/components/blog/CustomLinkButton.vue';
const route = useRoute();
const userStore = useStore.user();
const {
  isDark
} = useTheme();
const {
  openLogin
} = useLoginModal();
const {
  fetchArticleDetail,
  fetchEngagementStatus,
  fetchRelatedArticles,
  likeArticle,
  openDownload,
  toggleFavorite,
  unlockArticle
} = useApiClient();
const {
  data: publicCategories
} = await usePublicCategories();
const slug = computed(() => String(route.params.slug || ''));
const articleId = computed(() => Number(slug.value));
const isApiArticle = computed(() => Number.isInteger(articleId.value) && articleId.value > 0);
const articlePageDataKey = computed(() => `article-detail-page-${slug.value || 'empty'}`);
const liked = ref(false);
const favorited = ref(false);
const favoriteCount = ref(0);
const likeCount = ref(0);
const markdownPreviewReady = ref(false);
const hasCommentedBlock = ref(false);
const tocRefreshKey = ref(0);
const liveArticleTitle = useState('current-article-title', () => '');
// 顶栏透明(白 logo/白分类)仅在真实文章 hero 渲染时启用；文章不存在/未发布时置 false，避免白底白字看不到 logo/分类。
const articleHeroActive = useState('article-hero-active', () => false);
const MdPreview = defineAsyncComponent(async () => {
  defineMusicPlayerElement();
  const [module] = await Promise.all([import('md-editor-v3'), import('md-editor-v3/lib/preview.css')]);
  module.config({
    editorExtensions: {
      highlight: {
        js: '/vendor/highlightjs/highlight.min.js',
        css: {
          atom: {
            light: '/vendor/highlightjs/styles/atom-one-light.min.css',
            dark: '/vendor/highlightjs/styles/atom-one-dark.min.css'
          }
        }
      }
    },
    markdownItConfig(md) {
      applyBlogContainer(md);
    }
  });
  return module.MdPreview;
});
let tocRefreshFrame = 0;
interface ArticlePageData {
  article: Article | null;
  categories: Category[];
  related: Article[];
}
const emptyArticlePageData = (): ArticlePageData => ({
  article: null,
  categories: [],
  related: []
});
const {
  data: pageData,
  pending: loading,
  refresh: refreshArticle
} = await useAsyncData<ArticlePageData>(articlePageDataKey, async () => {
  if (!isApiArticle.value) {
    return emptyArticlePageData();
  }
  const [articleResult, relatedResult] = await Promise.allSettled([
    fetchArticleDetail(articleId.value),
    fetchRelatedArticles(articleId.value, 3)
  ]);
  const detailResponse = articleResult.status === 'fulfilled' ? articleResult.value : null;
  const relatedResponse = relatedResult.status === 'fulfilled' ? relatedResult.value : null;
  return {
    article: detailResponse?.success ? toBlogArticle(detailResponse.data) : null,
    categories: (publicCategories.value || []).map(toBlogCategory).filter(Boolean) as Category[],
    related: relatedResponse?.success ? relatedResponse.data.map(toBlogArticle) : []
  };
}, {
  default: emptyArticlePageData,
  watch: [slug],
  server: true,
  lazy: false
});
const article = computed(() => pageData.value.article);
const related = computed(() => pageData.value.related.slice(0, 3));
const readingProgress = ref(0);
const readingMinutes = computed(() => {
  const text = article.value?.rawContent || article.value?.content?.join('') || '';
  const chars = text.replace(/\s+/g, '').length;
  return Math.max(1, Math.ceil(chars / 400));
});
const updateReadingProgress = () => {
  if (!import.meta.client) return;
  const doc = document.documentElement;
  const scrollTop = window.scrollY || doc.scrollTop;
  const height = doc.scrollHeight - window.innerHeight;
  readingProgress.value = height > 0 ? Math.min(100, Math.max(0, (scrollTop / height) * 100)) : 0;
};
const category = computed(() => {
  const current = article.value;
  if (!current) return undefined;
  return pageData.value.categories.find(item => item.slug === current.category || item.label === current.category);
});
const likes = computed(() => likeCount.value);
const normalizeArticleMarkdown = (value: string) => value
  .replace(/!\[([^\]]*)\]\(!\[([^\]]*)\]\(([^)\s]+)\)\s*\)/g, '![$1]($3)')
  .replace(/!\[([^\]]*)\]\(!\[([^\]]*)\]\(([^)\s]+)\)\s*\n\s*\)/g, '![$1]($3)');
const articleMarkdown = computed(() => normalizeArticleMarkdown(article.value?.rawContent || article.value?.content.join('\n\n') || ''));
const mdPreviewId = computed(() => `article-preview-${article.value?.slug || 'empty'}`);
const articleRenderKey = computed(() => `article-render-${article.value?.slug || slug.value || 'empty'}`);
const CONTENT_IMAGE_WIDTH = 1080;
// 公共 sanitize（收紧 URL / 事件属性 / use 向量）+ 内容图缩略。
const sanitizeArticleHtml = (html: string) => {
  const sanitized = sanitizeArticleHtmlBase(html);
  return sanitized.replace(/<img\b[^>]*>/gi, tag => {
    let next = tag.replace(/(\bsrc\s*=\s*)(["'])(.*?)\2/i, (_match, prefix, quote, url) => `${prefix}${quote}${buildCoverUrl(url, CONTENT_IMAGE_WIDTH)}${quote}`);
    if (!/\bloading\s*=/i.test(next)) {
      next = next.replace(/<img\b/i, '<img loading="lazy" decoding="async"');
    }
    return next;
  });
};
const requestTocRefresh = () => {
  if (!import.meta.client) return;
  if (tocRefreshFrame) {
    window.cancelAnimationFrame(tocRefreshFrame);
  }
  tocRefreshFrame = window.requestAnimationFrame(() => {
    tocRefreshFrame = 0;
    tocRefreshKey.value += 1;
  });
};
const clearMarkdownRenderTask = () => {};
const scheduleMarkdownPreview = () => {
  if (!import.meta.client || !article.value) {
    markdownPreviewReady.value = false;
    return;
  }
  markdownPreviewReady.value = true;
  requestTocRefresh();
};
const handleArticleHtmlChanged = () => {
  requestTocRefresh();
  void nextTick(() => {
    // music-player 由自定义元素 connectedCallback 自动挂载，无需手动 replaceWith
    defineMusicPlayerElement();
    enhanceBlocks();
    enhanceArticleExtras();
  });
};
const enhanceArticleExtras = () => {
  if (!import.meta.client) return;
  const root = document.getElementById(mdPreviewId.value);
  if (!root) return;
  // 图片点击放大由 md-editor-v3 预览内置，这里不再重复绑定（否则会双开/出现两张重复图）。
  // 图片组（:::bd-gallery）：标注张数用于网格列数；超 9 张折叠，第 9 格盖「+N」点击展开。
  Array.from(root.querySelectorAll<HTMLElement>('.bd-block--gallery')).forEach(gallery => {
    if (gallery.dataset.galleryBound) return;
    gallery.dataset.galleryBound = '1';
    const imgs = Array.from(gallery.querySelectorAll<HTMLImageElement>('img'));
    gallery.dataset.count = String(imgs.length);
    if (imgs.length > 9) {
      imgs.forEach((img, i) => {
        if (i >= 8) img.style.display = 'none';
      });
      const more = document.createElement('div');
      more.className = 'gallery-cell bd-gallery-more';
      more.style.backgroundImage = `url("${imgs[8].src}")`;
      more.style.backgroundSize = 'cover';
      more.style.backgroundPosition = 'center';
      const overlay = document.createElement('div');
      overlay.className = 'bd-gallery-more__overlay';
      overlay.textContent = `+${imgs.length - 8}`;
      more.appendChild(overlay);
      more.addEventListener('click', () => {
        imgs.forEach(img => {
          img.style.display = '';
        });
        more.remove();
      });
      imgs[8].parentNode?.insertBefore(more, imgs[8]);
    }
  });
  const pres = Array.from(root.querySelectorAll<HTMLElement>('pre'));
  pres.forEach(pre => {
    if (pre.dataset.copyBound) return;
    pre.dataset.copyBound = '1';
    // md-editor-v3 代码块自带标题栏复制按钮（.md-editor-copy-button，位于 .md-editor-code-head，是 pre 的兄弟节点）。
    // 若外层 .md-editor-code 容器已有原生复制按钮，则不再注入自定义按钮，避免出现两个复制按钮。
    const codeWrap = pre.closest('.md-editor-code');
    if (codeWrap?.querySelector('.md-editor-copy-button')) return;
    if (pre.querySelector('[class*="copy"], .bd-code-copy')) return;
    pre.classList.add('bd-code-pre');
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'bd-code-copy';
    button.textContent = '复制';
    button.addEventListener('click', async () => {
      const code = pre.querySelector('code')?.innerText ?? pre.innerText;
      try {
        await navigator.clipboard.writeText(code.replace(/复制$/, ''));
        button.textContent = '已复制';
        window.setTimeout(() => {
          button.textContent = '复制';
        }, 1500);
      } catch {
        ElMessage.error('复制失败，请手动选择');
      }
    });
    pre.appendChild(button);
  });
};
const mountedBlockApps: App[] = [];
const secretOriginals = new WeakMap<HTMLElement, {
  gate: string;
  html: string;
}>();
let blockInstanceSeq = 0;
const resolveDownload = async (token: string) => {
  const response = await openDownload(token);
  if (!response.success) {
    throw new Error(response.message || '获取下载地址失败');
  }
  return response.data;
};
const lockedAction = (gate: string) => {
  if (gate === 'comment') {
    ElMessage.info('评论后即可查看');
    if (import.meta.client) {
      document.querySelector('.comments-section')?.scrollIntoView({
        behavior: 'smooth',
        block: 'start'
      });
    }
    return;
  }
  requireLogin('登录后可查看该内容');
};
const unmountBlocks = () => {
  while (mountedBlockApps.length) {
    mountedBlockApps.pop()?.unmount();
  }
};
const enhanceBlocks = () => {
  if (!import.meta.client) return;
  unmountBlocks();
  const container = document.getElementById(mdPreviewId.value);
  if (!container) return;
  const gateProps = {
    isAuthenticated: userStore.isAuthenticated,
    hasCommented: hasCommentedBlock.value,
    onLockedAction: lockedAction
  };
  container.querySelectorAll<HTMLElement>('.bd-block').forEach(node => {
    const instanceId = blockInstanceSeq += 1;
    if (node.classList.contains('bd-block--download')) {
      const payload = node.dataset.token || '';
      node.innerHTML = '';
      const app = createApp(DownloadCard, {
        payload,
        instanceId,
        isDark: isDark.value,
        resolve: resolveDownload,
        ...gateProps
      });
      app.mount(node);
      mountedBlockApps.push(app);
    } else if (node.classList.contains('bd-block--button')) {
      const payload = node.dataset.token || '';
      node.innerHTML = '';
      const app = createApp(CustomLinkButton, {
        payload,
        instanceId,
        isDark: isDark.value,
        resolve: resolveDownload,
        ...gateProps
      });
      app.mount(node);
      mountedBlockApps.push(app);
    } else if (node.classList.contains('bd-block--secret')) {
      let original = secretOriginals.get(node);
      if (!original) {
        original = {
          gate: node.dataset.gate || 'login',
          html: node.innerHTML
        };
        secretOriginals.set(node, original);
      }
      node.innerHTML = '';
      const app = createApp(SecretBlock, {
        gate: original.gate,
        html: original.html,
        ...gateProps
      });
      app.mount(node);
      mountedBlockApps.push(app);
    }
  });
};
const onCommentPosted = (event: Event) => {
  const detail = (event as CustomEvent<{
    articleId?: number;
  }>).detail;
  if (detail?.articleId === articleId.value) {
    markCommentedLocally(articleId.value);
    hasCommentedBlock.value = true;
  }
};
watch([() => userStore.isAuthenticated, hasCommentedBlock, isDark], () => {
  void nextTick(() => enhanceBlocks());
});
const syncEngagementFromArticle = () => {
  likeCount.value = article.value?.likes || 0;
  favoriteCount.value = article.value?.favorites || 0;
  liked.value = false;
  favorited.value = false;
};
const loadEngagementStatus = async () => {
  if (!import.meta.client || !isApiArticle.value) {
    return;
  }
  try {
    // 登录用户返回点赞+收藏态；游客带 guestKey 返回自己的点赞态(收藏恒 false)。
    const guestKey = userStore.isAuthenticated ? undefined : getGuestKey();
    const response = await fetchEngagementStatus(articleId.value, guestKey);
    if (response.success) {
      likeCount.value = response.data.likeCount;
      favoriteCount.value = response.data.favoriteCount;
      liked.value = response.data.liked;
      favorited.value = response.data.favorited;
    }
  } catch {}
};
const requireLogin = (message: string) => {
  if (userStore.isAuthenticated) return true;
  ElMessage.warning(message);
  openLogin();
  return false;
};
// 令牌可能在页面打开期间过期：此时 isAuthenticated 仍为 true，请求却会返回 401。
// 捕获 401 后清理残留登录态并提示重新登录，避免「点击无反应」的静默失败。
const handleExpiredSession = (error: unknown) => {
  const apiError = error as { response?: { status?: number }; status?: number; statusCode?: number };
  const status = apiError?.response?.status || apiError?.status || apiError?.statusCode || 0;
  if (status === 401 || status === 403) {
    userStore.clearSession();
    ElMessage.warning('登录状态已失效，请重新登录');
    openLogin();
    return true;
  }
  return false;
};
const toggleLike = async () => {
  if (!article.value) return;
  // 点赞对游客开放：未登录时带 guestKey 去重，登录用户按账号去重。
  const guestKey = userStore.isAuthenticated ? undefined : getGuestKey();
  const response = await likeArticle(articleId.value, guestKey);
  if (response.success) {
    likeCount.value = response.data.likeCount;
    favoriteCount.value = response.data.favoriteCount;
    liked.value = response.data.liked;
    favorited.value = response.data.favorited;
  }
};
const toggleCurrentFavorite = async () => {
  if (!article.value) return;
  if (!(await requireLogin('登录后可收藏文章'))) return;
  try {
    const response = await toggleFavorite(articleId.value);
    if (response.success) {
      likeCount.value = response.data.likeCount;
      favoriteCount.value = response.data.favoriteCount;
      liked.value = response.data.liked;
      favorited.value = response.data.favorited;
    }
  } catch (error) {
    if (!handleExpiredSession(error)) {
      ElMessage.error('操作失败，请稍后再试');
    }
  }
};
const copyArticleLink = async () => {
  if (!import.meta.client) return;
  const url = articleCanonical.value || window.location.href;
  try {
    await navigator.clipboard.writeText(url);
    ElMessage.success('链接已复制');
  } catch {
    ElMessage.info(`请手动复制：${url}`);
  }
};
const unlockPassword = ref('');
const unlocking = ref(false);
const submitUnlock = async () => {
  if (!article.value || !unlockPassword.value || unlocking.value) return;
  unlocking.value = true;
  try {
    const response = await unlockArticle(articleId.value, unlockPassword.value);
    if (response.success) {
      // 整体替换 pageData，确保 article 计算属性可靠刷新（改 .value.article 嵌套属性不一定触发）。
      pageData.value = { ...pageData.value, article: toBlogArticle(response.data) };
      unlockPassword.value = '';
      ElMessage.success('已解锁');
    } else {
      ElMessage.error(response.message || '访问密码错误');
    }
  } catch (error) {
    const apiError = error as {
      data?: {
        message?: string;
      };
      response?: {
        _data?: {
          message?: string;
        };
      };
    };
    ElMessage.error(apiError?.data?.message || apiError?.response?._data?.message || '访问密码错误');
  } finally {
    unlocking.value = false;
  }
};
// SSR 阶段无 localStorage 令牌（始终匿名），故登录用户拿到的可能是「锁定」结果。
// 客户端在已登录且文章被锁定时，带 token 重新拉取，让 登录可见/指定用户/管理员 门控生效。
const refreshDetailForAuth = async () => {
  if (!import.meta.client || !isApiArticle.value || !userStore.isAuthenticated) return;
  if (!article.value?.locked) return;
  try {
    const response = await fetchArticleDetail(articleId.value);
    if (response.success) {
      pageData.value = { ...pageData.value, article: toBlogArticle(response.data) };
    }
  } catch {
    // 拉取失败保持原锁定态
  }
};
watch(article, value => {
  liveArticleTitle.value = value?.title || '';
  // 有真实文章(hero 会渲染)才允许顶栏透明；否则(不存在/未发布)回退普通浅色顶栏。
  articleHeroActive.value = Boolean(value);
  scheduleMarkdownPreview();
  syncEngagementFromArticle();
  void loadEngagementStatus();
  if (import.meta.client) {
    hasCommentedBlock.value = hasCommentedLocally(articleId.value);
  }
}, {
  immediate: true
});
watch(() => userStore.isAuthenticated, () => {
  syncEngagementFromArticle();
  void loadEngagementStatus();
  void refreshDetailForAuth();
});
onMounted(() => {
  if (import.meta.client) {
    defineMusicPlayerElement();
    window.addEventListener('bd:comment-posted', onCommentPosted as EventListener);
    window.addEventListener('scroll', updateReadingProgress, { passive: true });
    updateReadingProgress();
    void refreshDetailForAuth();
  }
});
onUnmounted(() => {
  clearMarkdownRenderTask();
  if (import.meta.client && tocRefreshFrame) {
    window.cancelAnimationFrame(tocRefreshFrame);
  }
  if (import.meta.client) {
    window.removeEventListener('bd:comment-posted', onCommentPosted as EventListener);
    window.removeEventListener('scroll', updateReadingProgress);
  }
  unmountBlocks();
  liveArticleTitle.value = '';
  articleHeroActive.value = false;
});
const {
  public: {
    siteUrl
  }
} = useRuntimeConfig();
// siteUrl 未配置时，用本次请求的实际地址（协议+host）兜底，做到换 IP/域名免配置。
// 认反向代理转发头(x-forwarded-host/proto)，否则反代未透传 Host 时 SSR 会取到内部端口 localhost:3256。
const requestUrl = useRequestURL({ xForwardedHost: true, xForwardedProto: true });
const siteOrigin = String(siteUrl || '').trim().replace(/\/$/, '') || requestUrl.origin;
const toAbsoluteUrl = (path: string) => {
  if (!path) return '';
  if (/^https?:\/\//.test(path)) return path;
  return `${siteOrigin}${path.startsWith('/') ? '' : '/'}${path}`;
};
const articleCanonical = computed(() => article.value ? toAbsoluteUrl(`/article/${article.value.slug}`) : '');
// 文章底部来源/版权页脚：站点名 + 原创/转载判定。
const { siteSettings } = useSiteSettings();
const licenseSiteName = computed(() => siteSettings.value?.siteName || '我的博客');
const articleIsReprint = computed(() => String(article.value?.sourceType || 'ORIGINAL').toUpperCase() === 'REPRINT');
useHead(() => {
  const current = article.value;
  if (!current) {
    return {
      title: '文章 - 我的博客'
    };
  }
  const description = current.excerpt || '记录技术、项目与生活';
  const image = toAbsoluteUrl(current.cover);
  // 详情页 hero 主图为首屏 LCP：与 ArticleDetailHero 一致用 buildCoverUrl(cover, 1200) 缩略，
  // 无真实封面(占位图/data:)不预加载。preload 让浏览器尽早抓取 LCP 图。
  const cover = current.cover || '';
  const hasRealCover = Boolean(cover) && !cover.startsWith('data:') && !cover.includes(NO_COVER_IMAGE);
  const heroPreloadHref = hasRealCover ? buildCoverUrl(current.cover, 1200) : '';
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BlogPosting',
    headline: current.title,
    description,
    image: image ? [image] : undefined,
    author: {
      '@type': 'Person',
      name: current.author
    },
    mainEntityOfPage: articleCanonical.value,
    publisher: {
      '@type': 'Organization',
      name: '我的博客'
    }
  };
  return {
    title: `${current.title} - 我的博客`,
    link: [{
      rel: 'canonical',
      href: articleCanonical.value
    }, ...(heroPreloadHref ? [{
      rel: 'preload',
      as: 'image',
      href: heroPreloadHref,
      fetchpriority: 'high'
    }] : [])],
    meta: [{
      name: 'description',
      content: description
    }, {
      property: 'og:type',
      content: 'article'
    }, {
      property: 'og:title',
      content: current.title
    }, {
      property: 'og:description',
      content: description
    }, {
      property: 'og:url',
      content: articleCanonical.value
    }, ...(image ? [{
      property: 'og:image',
      content: image
    }] : []), {
      name: 'twitter:card',
      content: image ? 'summary_large_image' : 'summary'
    }],
    script: [{
      type: 'application/ld+json',
      innerHTML: JSON.stringify(jsonLd)
    }]
  };
});
</script>

<template>
  <div v-if="article" class="reading-progress" aria-hidden="true">
    <div class="reading-progress__bar" :style="{ width: `${readingProgress}%` }" />
  </div>
  <BlogArticleDetailSkeleton v-if="loading && !article" />

  <template v-else-if="article">
    <BlogArticleDetailHero :key="articleRenderKey" :article="article" :category="category" :reading-minutes="readingMinutes" />

    <BlogPageLayout class="article-detail-layout" toc :toc-refresh-key="tocRefreshKey" :sidebar="false">
      <template #sidebar>
        <BlogRandomPostsWidget class="article-random-widget" />
      </template>

      <BlogSingleArticle :title="article.title" hide-header>
        <div class="article-reading-title">
          <Sparkles :size="18" aria-hidden="true" />
          <span>{{ article.title }}</span>
          <span class="article-reading-time">约 {{ readingMinutes }} 分钟</span>
        </div>

        <aside v-if="!article.locked && article.aiSummary" class="article-ai-summary" aria-label="文章摘要">
          <div class="article-ai-summary__head">
            <Sparkles :size="16" aria-hidden="true" />
            <span>文章摘要</span>
            <span class="article-ai-summary__badge">AI</span>
          </div>
          <p class="article-ai-summary__body">{{ article.aiSummary }}</p>
        </aside>

        <section v-if="article.locked" class="article-locked" aria-label="访问受限">
          <Lock :size="30" aria-hidden="true" />
          <template v-if="article.requiresPassword">
            <p class="article-locked__title">本文需要访问密码</p>
            <form class="article-locked__form" @submit.prevent="submitUnlock">
              <input
                v-model="unlockPassword"
                class="article-locked__input"
                type="password"
                placeholder="请输入访问密码"
                autocomplete="off"
                :disabled="unlocking"
              />
              <button class="article-locked__button" type="submit" :disabled="unlocking || !unlockPassword">
                {{ unlocking ? '验证中...' : '解锁查看' }}
              </button>
            </form>
          </template>
          <template v-else-if="article.visibility === 'LOGIN'">
            <p class="article-locked__title">本文登录后可见</p>
            <button v-if="!userStore.isAuthenticated" class="article-locked__button" type="button" @click="openLogin">登录查看</button>
          </template>
          <template v-else>
            <p class="article-locked__title">{{ userStore.isAuthenticated ? '你没有查看该文章的权限' : '本文仅指定用户可见' }}</p>
            <button v-if="!userStore.isAuthenticated" class="article-locked__button" type="button" @click="openLogin">登录查看</button>
          </template>
        </section>

        <ClientOnly v-else>
          <MdPreview
            v-if="markdownPreviewReady"
            :key="mdPreviewId"
            class="article-md-preview"
            :id="mdPreviewId"
            :model-value="articleMarkdown"
            :sanitize="sanitizeArticleHtml"
            :theme="isDark ? 'dark' : 'light'"
            preview-theme="github"
            code-theme="atom"
            :no-katex="true"
            :no-mermaid="true"
            :no-echarts="true"
            :no-prettier="true"
            :on-html-changed="handleArticleHtmlChanged"
          />
          <ElSkeleton v-else class="article-md-preview" :rows="10" animated />
          <template #fallback>
            <ElSkeleton class="article-md-preview" :rows="10" animated />
          </template>
        </ClientOnly>

        <section v-if="!article.locked" class="article-license" aria-label="版权与来源声明">
          <div class="article-license__head">
            <Copyright :size="18" aria-hidden="true" />
            <span class="article-license__head-title">版权声明</span>
            <span class="article-license__badge" :class="articleIsReprint ? 'is-reprint' : 'is-original'">{{ articleIsReprint ? '转载' : '原创' }}</span>
          </div>
          <ul class="article-license__list">
            <li>
              <span class="article-license__label">本文链接</span>
              <a class="article-license__value article-license__link" :href="articleCanonical">{{ articleCanonical }}</a>
            </li>
            <li v-if="articleIsReprint && article.sourceUrl">
              <span class="article-license__label">原文出处</span>
              <a class="article-license__value article-license__link" :href="article.sourceUrl" target="_blank" rel="noopener noreferrer nofollow">{{ article.sourceUrl }}</a>
            </li>
            <li>
              <span class="article-license__label">许可协议</span>
              <span class="article-license__value">
                本文采用 <a class="article-license__link" href="https://creativecommons.org/licenses/by-nc-nd/4.0/deed.zh" target="_blank" rel="noopener noreferrer nofollow">CC BY-NC-ND 4.0</a> 协议，完整转载请注明来自 {{ licenseSiteName }}。
              </span>
            </li>
          </ul>
        </section>

        <section class="article-end-panel" aria-label="文章标签和操作">
          <div class="article-end-panel__line">
            <span></span>
            <strong>THE END</strong>
            <span></span>
          </div>

          <div class="article-end-panel__groups">
            <NuxtLink v-if="category" class="article-end-panel__category" :to="`/category/${category.slug}`">
              <FolderOpen :size="15" aria-hidden="true" />
              {{ category.label }}
            </NuxtLink>
            <NuxtLink
              v-for="tagName in article.tags"
              :key="tagName"
              class="article-end-panel__tag"
              :to="`/tags/${encodeURIComponent(tagName)}`"
            >
              <Tag :size="13" aria-hidden="true" />
              {{ tagName }}
            </NuxtLink>
          </div>

          <p>喜欢就支持一下吧!</p>

          <div class="article-actions">
            <button type="button" class="article-actions__item article-actions__like" :class="{ liked }" @click="toggleLike">
              <ThumbsUp :size="24" :fill="liked ? 'currentColor' : 'none'" aria-hidden="true" />
              <span>{{ liked ? '已赞' : '点赞' }} {{ likes }}</span>
            </button>
            <button type="button" class="article-actions__item" @click="copyArticleLink">
              <Link2 :size="24" aria-hidden="true" />
              <span>复制链接</span>
            </button>
            <button type="button" class="article-actions__item" :class="{ liked: favorited }" @click="toggleCurrentFavorite">
              <Star :size="25" :fill="favorited ? 'currentColor' : 'none'" aria-hidden="true" />
              <span>{{ favorited ? '已稍后读' : '稍后读' }} {{ favoriteCount || '' }}</span>
            </button>
          </div>
        </section>
      </BlogSingleArticle>

      <section v-if="related.length" class="related-posts" data-toc-exclude>
        <h2>相关阅读</h2>
        <div class="article-list article-list--image_text article-list--cards">
          <BlogArticleCard v-for="item in related" :key="item.slug" :article="item" :show-relative-time="true" />
        </div>
      </section>

      <BlogCommentSection
        :key="`comments-${articleId}`"
        :article-id="isApiArticle ? articleId : undefined"
        :comments-enabled="article.commentsEnabled !== false"
        :interaction-count="Number(article.comments) || 0"
      />
    </BlogPageLayout>
  </template>

  <div v-else class="page-container page-container--narrow">
    <ElEmpty description="文章不存在或未发布" />
  </div>
</template>

<style>
.reading-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1200;
  height: 3px;
  background: transparent;
  pointer-events: none;
}
.reading-progress__bar {
  height: 100%;
  background: linear-gradient(90deg, #409eff, #66b1ff);
  transition: width .08s linear;
}
.article-reading-time {
  margin-left: auto;
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}
.article-locked {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 48px 24px;
  margin: 12px 0 8px;
  text-align: center;
  color: var(--text-secondary);
  background: var(--bg-soft);
  border: 1px solid var(--border-color);
  border-radius: 14px;
}
.article-locked__title {
  margin: 0;
  font-size: 16px;
  color: var(--text-primary);
}
.article-locked__form {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}
.article-locked__input {
  height: 38px;
  min-width: 220px;
  padding: 0 14px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 14px;
}
.article-locked__input:focus {
  outline: none;
  border-color: var(--theme-color);
}
.article-locked__button {
  height: 38px;
  padding: 0 20px;
  border: 0;
  border-radius: 10px;
  background: var(--theme-color);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: filter 0.15s ease;
}
.article-locked__button:hover:not(:disabled) {
  filter: brightness(1.08);
}
.article-locked__button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}


/* AI 摘要卡片：正文上方信息卡；仅当文章开启展示且已生成时渲染。 */
.article-ai-summary {
  margin: 4px 0 20px;
  padding: 14px 18px;
  border: 1px solid color-mix(in srgb, var(--theme-color) 22%, var(--border-color));
  border-radius: 12px;
  background: color-mix(in srgb, var(--theme-color) 7%, var(--bg-card));
}
.article-ai-summary__head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-color);
}
.article-ai-summary__badge {
  padding: 1px 7px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #fff;
  background: var(--theme-color);
}
.article-ai-summary__body {
  margin: 0;
  font-size: 14px;
  line-height: 1.85;
  color: var(--text-secondary);
  text-align: justify;
}

.article-md-preview pre.bd-code-pre {
  position: relative;
}
.article-md-preview pre.bd-code-pre .bd-code-copy {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 2px 10px;
  font-size: 12px;
  line-height: 1.6;
  
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 6px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease, background 0.15s ease;
}
.article-md-preview pre.bd-code-pre:hover .bd-code-copy {
  opacity: 1;
}
.article-md-preview pre.bd-code-pre .bd-code-copy:hover {
  background: rgba(0, 0, 0, 0.68);
}

/* ---------- 代码块统一深色(atom-one-dark) ---------- */
/* preview-theme=github 在浅色模式下会把代码块背景改成 #f6f8fa(白底)，与 atom-one-dark 高亮配色冲突，
   导致正文里代码块「黑底变白底、token 颜色发灰」。这里无论 light/dark 都把 md-editor 代码块相关变量
   强制成 atom-one-dark 配色，保证正文代码块始终是黑底浅字、与编辑器预览一致。 */
.article-md-preview .md-editor-code {
  --md-theme-code-block-color: #abb2bf;
  --md-theme-code-block-bg-color: #282c34;
  --md-theme-code-before-bg-color: #21252b;
}
.article-md-preview .md-editor-code pre code,
.article-md-preview .md-editor-code pre code .hljs,
.article-md-preview .md-editor-code .md-editor-code-block {
  background-color: #282c34 !important;
  color: #abb2bf;
}
.article-md-preview .md-editor-code .md-editor-code-head {
  background-color: #21252b !important;
  color: #9aa4b2;
}
.article-md-preview .md-editor-code .md-editor-code-lang {
  color: #9aa4b2;
}
/* 代码块标题栏的复制按钮在深色头部下保持浅色可见 */
.article-md-preview .md-editor-code .md-editor-copy-button {
  color: #9aa4b2;
}
.article-md-preview .md-editor-code .md-editor-copy-button:hover {
  color: #fff;
}
/* 代码块折叠开关：SVG 用 currentColor；深色头部下显式浅色，与后台预览一致。 */
.article-md-preview .md-editor-code .md-editor-collapse-tips {
  color: #9aa4b2;
}
.article-md-preview .md-editor-code summary.md-editor-code-head {
  cursor: pointer;
}
.article-md-preview .md-editor-code summary.md-editor-code-head:hover .md-editor-collapse-tips {
  color: #fff;
}
/* 行号列：md-editor 用 <span rn-wrapper> 绝对定位行号，sanitize 放行该属性后这里补足定位上下文，
   并给代码区留出左侧行号宽度，避免行号与代码错位重叠。 */
.article-md-preview .md-editor-code pre {
  position: relative;
}
.article-md-preview .md-editor-code span[rn-wrapper] {
  color: #636d83;
}
/* 文章正文音乐播放器：柔和白卡片，最大 560 居中 */
.article-md-preview :deep(music-player) {
  display: block;
  width: min(560px, 100%);
  margin: 16px auto;
}
</style>
