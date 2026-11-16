<script setup lang="ts">
import { Bell, ChevronDown, LogIn, LogOut, Menu, Moon, Rss, Search, Sun, UserCircle, UserRound, X } from '@lucide/vue';
import { siteMeta } from '~/data/blog';
import { buildAvatarUrl } from '~/utils/blog-adapter';
import type { CategorySummary, NavItem } from '~/types/blog';
const route = useRoute();
const userStore = useStore.user();
const {
  isDark,
  toggleTheme
} = useTheme();
const {
  openLogin: showLoginModal
} = useLoginModal();
const {
  logout: requestLogout,
  fetchNotificationUnreadCount,
  fetchSeries
} = useApiClient();
const { data: navigationSeries } = await useAsyncData('navigation-series', async () => {
  try {
    const response = await fetchSeries();
    return response.success && Array.isArray(response.data) ? response.data : [];
  } catch { return []; }
}, { default: () => [] });
const unreadNotifications = ref(0);
const loadUnreadNotifications = async () => { if (!userStore.isAuthenticated) { unreadNotifications.value=0; return; } try { const r=await fetchNotificationUnreadCount(); unreadNotifications.value=r.success?Number(r.data.count||0):0; } catch {} };
let unreadTimer: number | undefined;
const refreshUnreadWhenVisible = () => {
  if (document.visibilityState === 'visible') void loadUnreadNotifications();
};
const mobileOpen = ref(false);
const expandedMobileMenus = ref<Set<string>>(new Set());
const mobileMenuKey = (item: NavItem) => `${item.label}-${item.to}`;
const isMobileMenuExpanded = (item: NavItem) => expandedMobileMenus.value.has(mobileMenuKey(item));
const toggleMobileMenu = (item: NavItem) => {
  const key = mobileMenuKey(item);
  const next = new Set(expandedMobileMenus.value);
  if (next.has(key)) {
    next.delete(key);
  } else {
    next.add(key);
  }
  expandedMobileMenus.value = next;
};
const searchOpen = ref(false);
const keyword = ref('');
const articleTitleVisible = ref(false);
const articleTitleScrollOffset = 1;
const liveArticleTitle = useState('current-article-title', () => '');
// 文章详情仅在「真实文章已加载、hero 已渲染」时才让顶栏透明(白 logo/白分类)。
// 文章不存在/未发布时无 hero，透明会让白底白字看不到 logo/分类，故此处回退为普通浅色顶栏。
const articleHeroActive = useState('article-hero-active', () => false);
let articleTitleFrame = 0;
const homeNavItem: NavItem = {
  label: '主页',
  to: '/',
  icon: 'Home'
};
const dynamicsNavItem: NavItem = {
  label: '动态',
  to: '/dynamics',
  icon: 'Chat'
};
const archiveNavItem: NavItem = {
  label: '归档',
  to: '/archive',
  icon: 'Package'
};
const friendsNavItem: NavItem = {
  label: '友链',
  to: '/friends',
  icon: 'Link'
};
const {
  data: navigationCategories
} = await usePublicCategories();
const {
  siteSettings,
  loadSiteSettings
} = useSiteSettings();
await loadSiteSettings();
const sortCategories = (items: CategorySummary[]) => items.slice().sort((a, b) => {
  const orderDiff = Number(a.displayOrder || 0) - Number(b.displayOrder || 0);
  return orderDiff || Number(a.id || 0) - Number(b.id || 0);
});
const toNavItem = (category: CategorySummary, children: NavItem[] = []): NavItem => {
  const externalUrl = (category.externalUrl || '').trim();
  return {
    label: category.name,
    to: externalUrl || `/category/${category.slug}`,
    icon: category.iconCode || 'Package',
    ...(externalUrl ? { external: true } : {}),
    ...(children.length ? { children } : {})
  };
};
// 顶层导航项（一级分类 + 「动态」）带排序权重，便于把「动态」按后台设置插入到分类之间。
const buildTopLevelEntries = (categories: CategorySummary[]): Array<{ order: number; id: number; item: NavItem }> => {
  const visibleCategories = categories.filter(category => category.navVisible !== false && category.slug);
  const visibleIds = new Set(visibleCategories.map(category => category.id));
  const byParentId = new Map<number, CategorySummary[]>();
  visibleCategories.forEach(category => {
    const parentKey = category.parentId && visibleIds.has(category.parentId) ? category.parentId : 0;
    if (!byParentId.has(parentKey)) {
      byParentId.set(parentKey, []);
    }
    byParentId.get(parentKey)!.push(category);
  });
  const appendChildren = (parentId: number): NavItem[] => sortCategories(byParentId.get(parentId) || []).map(category => toNavItem(category, appendChildren(category.id)));
  return sortCategories(byParentId.get(0) || []).map(category => ({
    order: Number(category.displayOrder || 0),
    id: Number(category.id || 0),
    item: toNavItem(category, appendChildren(category.id))
  }));
};
const navItems = computed<NavItem[]>(() => {
  const entries = buildTopLevelEntries(navigationCategories.value || []);
  // 固定导航项（主页/动态/归档/友链）均可在后台「分类管理 → 导航项」控制显隐与排序。
  const fixedNavs: Array<{ visible: boolean; order: number; id: number; item: NavItem }> = [
    {
      visible: siteSettings.value.homeHomeNavVisible !== false,
      order: Number(siteSettings.value.homeHomeNavOrder ?? -100),
      id: -3,
      item: homeNavItem
    },
    {
      visible: siteSettings.value.homeDynamicsNavVisible !== false,
      order: Number(siteSettings.value.homeDynamicsNavOrder || 0),
      id: Number.MAX_SAFE_INTEGER - 2,
      item: dynamicsNavItem
    },
    {
      visible: siteSettings.value.homeFriendsNavVisible !== false,
      order: Number(siteSettings.value.homeFriendsNavOrder ?? 800),
      id: Number.MAX_SAFE_INTEGER - 1,
      item: friendsNavItem
    },
    {
      visible: siteSettings.value.homeArchiveNavVisible !== false,
      order: Number(siteSettings.value.homeArchiveNavOrder ?? 900),
      id: Number.MAX_SAFE_INTEGER,
      item: archiveNavItem
    }
  ];
  fixedNavs.filter(item => item.visible).forEach(item => {
    entries.push({ order: item.order, id: item.id, item: item.item });
  });
  entries.sort((a, b) => (a.order - b.order) || (a.id - b.id));
  return [...entries.map(entry => entry.item), ...(navigationSeries.value.length ? [{ label: '系列', to: '/series', icon: 'icon-archive' }] : []), ...(userStore.isAuthenticated ? [{ label: '继续阅读', to: '/reading', icon: 'icon-article' }] : [])];
});
// 文章路由即挂沉浸顶栏 class；无 hero 时(404)靠 body:has(.article-detail-hero) 的 CSS 不把图标强行变白。
const isArticlePage = computed(() => route.path.startsWith('/article/'));
const isHomePage = computed(() => route.path === '/' || route.path === '/index');
const currentArticleTitle = computed(() => {
  if (!isArticlePage.value) return '';
  return liveArticleTitle.value || '';
});
const userDisplayName = computed(() => userStore.displayName || userStore.username || '个人中心');
// 移动端抽屉用户卡：登录后优先显示用户名，否则回退到站点主人资料。
const drawerName = computed(() => (userStore.isAuthenticated ? userDisplayName.value : siteSettings.value.profileName || siteMeta.name));
const drawerBio = computed(() => siteSettings.value.profileBio || siteMeta.description);
const drawerAvatar = computed(() => buildAvatarUrl(siteSettings.value.profileAvatarUrl) || siteMeta.avatar);
const drawerCopyright = computed(() => {
  const year = new Date().getFullYear();
  const name = siteSettings.value.siteName || siteMeta.name;
  return `© ${year} ${name}`;
});
const isNavActive = (item: NavItem): boolean => {
  if (item.external) return false;
  if (item.to === '/') return route.path === '/' || route.path === '/index';
  return route.path === item.to || route.path.startsWith(`${item.to}/`);
};
const isDrawerActive = (item: NavItem): boolean => {
  if (item.external) return false;
  if (item.to === '/') return route.path === '/' || route.path === '/index';
  return route.path === item.to;
};
const drawerHero = computed(() => siteSettings.value.homeHeroImageUrl || '');
const submitSearch = async () => {
  const q = keyword.value.trim();
  if (!q) return;
  mobileOpen.value = false;
  searchOpen.value = false;
  await navigateTo({
    path: '/search',
    query: {
      q
    }
  });
};
const openLogin = () => {
  mobileOpen.value = false;
  if (userStore.isAuthenticated) {
    void navigateTo('/profile');
    return;
  }
  showLoginModal();
};
const closeMobileMenu = () => {
  mobileOpen.value = false;
};
// 抽屉内重内容（天气）延后挂载，避免首帧滑动被请求/布局抢占主线程。
const mobileWeatherReady = ref(false);
let mobileScrollLockY = 0;
const lockBodyScroll = () => {
  if (!import.meta.client) return;
  mobileScrollLockY = window.scrollY || document.documentElement.scrollTop || 0;
  const body = document.body;
  body.style.position = 'fixed';
  body.style.top = `-${mobileScrollLockY}px`;
  body.style.left = '0';
  body.style.right = '0';
  body.style.width = '100%';
};
const unlockBodyScroll = () => {
  if (!import.meta.client) return;
  const body = document.body;
  body.style.position = '';
  body.style.top = '';
  body.style.left = '';
  body.style.right = '';
  body.style.width = '';
  window.scrollTo(0, mobileScrollLockY);
};
const logoutUser = async () => {
  mobileOpen.value = false;
  try {
    await requestLogout();
  } catch {}
  userStore.clearSession();
  await navigateTo('/');
};
const getPageScrollTop = () => {
  if (!import.meta.client) return 0;
  return Math.max(window.scrollY, document.documentElement.scrollTop, document.body.scrollTop, document.scrollingElement?.scrollTop ?? 0);
};
const syncArticleTitleVisible = () => {
  if (!import.meta.client) return;
  const nextVisible = isArticlePage.value && getPageScrollTop() > articleTitleScrollOffset;
  if (articleTitleVisible.value !== nextVisible) {
    articleTitleVisible.value = nextVisible;
  }
};
const requestArticleTitleVisibleSync = () => {
  if (!import.meta.client || articleTitleFrame) return;
  articleTitleFrame = window.requestAnimationFrame(() => {
    articleTitleFrame = 0;
    syncArticleTitleVisible();
  });
};
const scrollArticleTop = () => {
  if (!import.meta.client) return;
  window.scrollTo({
    top: 0,
    behavior: 'smooth'
  });
};
watch(() => route.fullPath, () => {
  mobileOpen.value = false;
  requestArticleTitleVisibleSync();
  void loadUnreadNotifications();
});
watch(() => userStore.isAuthenticated, () => void loadUnreadNotifications());
watch(mobileOpen, value => {
  if (!import.meta.client) return;
  document.documentElement.classList.toggle('mobile-nav-open', value);
  if (value) {
    lockBodyScroll();
    // 下一帧再挂天气，让抽屉先开始滑动
    if (!mobileWeatherReady.value) {
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          mobileWeatherReady.value = true;
        });
      });
    }
  } else {
    unlockBodyScroll();
  }
});
onMounted(() => {
  void loadUnreadNotifications();
  unreadTimer = window.setInterval(refreshUnreadWhenVisible, 60_000);
  document.addEventListener('visibilitychange', refreshUnreadWhenVisible);
  syncArticleTitleVisible();
  window.addEventListener('scroll', requestArticleTitleVisibleSync, {
    passive: true
  });
  window.addEventListener('resize', requestArticleTitleVisibleSync, {
    passive: true
  });
  window.addEventListener('pageshow', requestArticleTitleVisibleSync);
});
onUnmounted(() => {
  if (import.meta.client) {
    if (mobileOpen.value) unlockBodyScroll();
    document.documentElement.classList.remove('mobile-nav-open');
    window.removeEventListener('scroll', requestArticleTitleVisibleSync);
    window.removeEventListener('resize', requestArticleTitleVisibleSync);
    window.removeEventListener('pageshow', requestArticleTitleVisibleSync);
    document.removeEventListener('visibilitychange', refreshUnreadWhenVisible);
    if (unreadTimer) window.clearInterval(unreadTimer);
    if (articleTitleFrame) {
      window.cancelAnimationFrame(articleTitleFrame);
    }
  }
});
</script>

<template>
  <header
    class="site-header site-header--classic site-header--bg-color site-header--light-bg"
    :class="{ 'site-header--article-mobile': isArticlePage, 'site-header--article-scrolled': articleTitleVisible, 'site-header--home': isHomePage }"
    style="--header-bg-image: none;"
  >
    <svg class="site-logo-filters" aria-hidden="true" width="0" height="0" focusable="false">
      <defs>
        <filter id="site-logo-white-glyphs" color-interpolation-filters="sRGB">
          <!-- The source logo has an opaque white background. Use its ink as alpha
               before painting it white, preserving antialiasing and transparent space. -->
          <feColorMatrix in="SourceGraphic" type="matrix" values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  -0.2126 -0.7152 -0.0722 0 1" />
          <feComposite in2="SourceAlpha" operator="in" />
        </filter>
      </defs>
    </svg>
    <div class="site-header__top">
      <div class="site-header__top-inner">
        <div class="site-header__left">
          <NuxtLink class="site-logo" to="/" :aria-label="`${siteMeta.name} 首页`">
            <img class="site-logo-img site-logo-light" :src="siteMeta.logoLight" :alt="siteMeta.name">
            <img class="site-logo-img site-logo-dark" :src="siteMeta.logoDark" :alt="siteMeta.name">
          </NuxtLink>

          <form class="site-header__search" role="search" @submit.prevent="submitSearch">
            <Search class="site-header__search-icon" :size="16" aria-hidden="true" />
            <input v-model="keyword" type="text" name="s" class="site-header__search-input" placeholder="搜索文章...">
          </form>

          <nav class="site-nav site-nav--inline" aria-label="顶部导航">
            <ul class="nav-list">
              <BlogSiteNavItem
                v-for="item in navItems"
                :key="`${item.label}-${item.to}`"
                :item="item"
              />
            </ul>
          </nav>
        </div>

        <button
          v-if="isArticlePage && currentArticleTitle"
          class="site-header__article-title"
          :class="{ visible: articleTitleVisible }"
          type="button"
          aria-label="返回文章顶部"
          @click="scrollArticleTop"
        >
          <span>{{ currentArticleTitle }}</span>
        </button>

        <div class="site-header__actions">
          <button class="site-header__search-btn" type="button" aria-label="搜索" @click="searchOpen = true">
            <Search :size="18" aria-hidden="true" />
          </button>

          <button class="theme-toggle" type="button" aria-label="切换深色模式" @click="toggleTheme">
            <Sun v-if="isDark" class="theme-toggle__dark" :size="20" aria-hidden="true" />
            <Moon v-else class="theme-toggle__light" :size="20" aria-hidden="true" />
          </button>
          <NuxtLink v-if="userStore.isAuthenticated" class="theme-toggle header-notice" to="/notifications" aria-label="通知中心">
            <Bell :size="19" aria-hidden="true" /><span v-if="unreadNotifications" class="header-notice__badge">{{ unreadNotifications > 99 ? '99+' : unreadNotifications }}</span>
          </NuxtLink>

          <div class="user-menu" :class="{ 'user-menu--authenticated': userStore.isAuthenticated }">
            <button class="header-login-btn" type="button" :aria-label="userStore.isAuthenticated ? '个人中心' : '登录'" @click="openLogin">
              <UserRound :size="16" aria-hidden="true" />
              <span>{{ userStore.isAuthenticated ? userDisplayName : '登录' }}</span>
            </button>

            <div v-if="userStore.isAuthenticated" class="user-menu__dropdown" role="menu">
              <NuxtLink class="user-menu__dropdown-item" to="/profile" role="menuitem">
                <UserCircle :size="16" aria-hidden="true" />
                <span>个人中心</span>
              </NuxtLink>
              <NuxtLink class="user-menu__dropdown-item" to="/notifications" role="menuitem"><Bell :size="16"/><span>通知中心<span v-if="unreadNotifications">（{{ unreadNotifications }}）</span></span></NuxtLink>
              <button class="user-menu__dropdown-item" type="button" role="menuitem" @click="logoutUser">
                <LogOut :size="16" aria-hidden="true" />
                <span>退出登录</span>
              </button>
            </div>
          </div>

          <button class="nav-toggle" :class="{ active: mobileOpen }" type="button" aria-label="菜单" @click="mobileOpen = !mobileOpen">
            <Menu class="nav-toggle__open" :size="20" aria-hidden="true" />
            <X class="nav-toggle__close" :size="20" aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>

    <div class="site-header__bottom">
      <div class="site-header__bottom-inner">
        <nav class="site-nav site-nav--desktop" aria-label="桌面导航">
          <ul class="nav-list">
            <BlogSiteNavItem
              v-for="item in navItems"
              :key="`${item.label}-${item.to}`"
              :item="item"
            />
          </ul>
        </nav>
      </div>
    </div>
  </header>

  <div class="mobile-nav__overlay" :class="{ open: mobileOpen }" aria-hidden="true" @click="mobileOpen = false"></div>

  <nav class="site-nav--mobile" :class="{ open: mobileOpen }" aria-label="移动端导航">
    <button
      class="mobile-nav__user"
      type="button"
      :style="drawerHero ? { '--mobile-nav-hero': `url(${drawerHero})` } : undefined"
      :class="{ 'has-hero': drawerHero }"
      :aria-label="userStore.isAuthenticated ? '个人中心' : '立即登录'"
      @click="openLogin"
    >
      <img class="mobile-nav__user-avatar" :src="drawerAvatar" :alt="drawerName" loading="lazy">
      <span class="mobile-nav__user-text">
        <strong>{{ drawerName }}</strong>
        <small>{{ drawerBio }}</small>
      </span>
    </button>

    <div class="mobile-nav__scroll">
      <WidgetWeather v-if="siteSettings.weatherEnabled && (mobileOpen || mobileWeatherReady)" compact />

      <ul class="mobile-nav__menu">
        <li v-for="item in navItems" :key="`${item.label}-${item.to}`" class="mobile-nav__menu-item">
          <div class="mobile-nav__row" :class="{ 'has-children': item.children?.length }">
            <a
              v-if="item.external"
              :href="item.to"
              target="_blank"
              rel="noopener noreferrer"
              class="mobile-nav__link"
              @click="closeMobileMenu"
            >
              <span>{{ item.label }}</span>
            </a>
            <NuxtLink
              v-else
              :to="item.to"
              class="mobile-nav__link"
              :class="{ 'is-active': isDrawerActive(item) }"
              @click="closeMobileMenu"
            >
              <span>{{ item.label }}</span>
            </NuxtLink>

            <button
              v-if="item.children?.length"
              type="button"
              class="mobile-nav__expand"
              :class="{ 'is-open': isMobileMenuExpanded(item) }"
              :aria-label="`展开 ${item.label} 子菜单`"
              :aria-expanded="isMobileMenuExpanded(item)"
              @click="toggleMobileMenu(item)"
            >
              <ChevronDown :size="20" aria-hidden="true" />
            </button>
          </div>

          <ul v-if="item.children?.length && isMobileMenuExpanded(item)" class="mobile-nav__submenu">
            <li v-for="child in item.children" :key="`${child.label}-${child.to}`">
              <NuxtLink
                :to="child.to"
                class="mobile-nav__link mobile-nav__link--child"
                :class="{ 'is-active': isDrawerActive(child) }"
                @click="closeMobileMenu"
              >
                <span>{{ child.label }}</span>
              </NuxtLink>
            </li>
          </ul>
        </li>
      </ul>
      <div class="mobile-nav__divider"></div>

      <button v-if="userStore.isAuthenticated" class="mobile-nav__link mobile-nav__logout" type="button" @click="logoutUser">
        <LogOut :size="20" aria-hidden="true" />
        <span>退出登录</span>
      </button>
      <button v-else class="mobile-nav__link mobile-nav__logout" type="button" @click="openLogin">
        <LogIn :size="20" aria-hidden="true" />
        <span>立即登录</span>
      </button>
    </div>

    <div class="mobile-nav__foot">
      <div class="mobile-nav__social">
        <a v-if="siteMeta.github" :href="siteMeta.github" target="_blank" rel="noopener noreferrer" aria-label="GitHub">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 .5C5.73.5.5 5.73.5 12c0 5.08 3.29 9.39 7.86 10.91.58.11.79-.25.79-.56v-2c-3.2.7-3.88-1.54-3.88-1.54-.53-1.34-1.29-1.7-1.29-1.7-1.05-.72.08-.71.08-.71 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.71 1.26 3.37.96.1-.75.4-1.26.73-1.55-2.56-.29-5.25-1.28-5.25-5.7 0-1.26.45-2.29 1.19-3.1-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.18 1.18a11.1 11.1 0 0 1 5.8 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.12 3.05.74.81 1.18 1.84 1.18 3.1 0 4.43-2.69 5.41-5.26 5.69.41.36.78 1.07.78 2.16v3.2c0 .31.21.68.8.56A11.51 11.51 0 0 0 23.5 12C23.5 5.73 18.27.5 12 .5z"/></svg>
        </a>
        <a v-if="siteMeta.twitter" :href="siteMeta.twitter" target="_blank" rel="noopener noreferrer" aria-label="Twitter">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M18.9 1.6h3.5l-7.7 8.8 9 11.9h-7l-5.5-7.2-6.3 7.2H1.4l8.2-9.4L1 1.6h7.2l4.9 6.5 5.8-6.5zm-1.2 18.6h1.9L6.4 3.5H4.3l13.4 16.7z"/></svg>
        </a>
        <a href="/feed.xml" aria-label="RSS" @click="closeMobileMenu">
          <Rss :size="20" aria-hidden="true" />
        </a>
      </div>
      <small class="mobile-nav__copyright">{{ drawerCopyright }}</small>
    </div>
  </nav>

  <BlogSearchModal v-model="searchOpen" />
</template>

<style scoped lang="scss">

.header-notice { position: relative; text-decoration: none; }
.header-notice__badge { position: absolute; top: -5px; right: -7px; min-width: 16px; height: 16px; padding: 0 4px; border-radius: 9px; background: #ef4444; color: #fff; font-size: 10px; line-height: 16px; text-align: center; }

.site-header__top {
  backdrop-filter: saturate(165%) blur(12px);
  -webkit-backdrop-filter: saturate(165%) blur(12px);
  border-bottom: 1px solid color-mix(in srgb, var(--border-color) 65%, transparent);
  box-shadow: 0 6px 22px rgba(15, 23, 42, 0.06);
}

/* 文章详情沉浸态：覆盖上方默认毛玻璃，避免移动端顶栏发白。 */
:global(.site-header.site-header--article-mobile:not(.site-header--article-scrolled) .site-header__top) {
  border-bottom: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}


.nav-list li a {
  position: relative;
  border-radius: 10px;
  transition: color 0.2s ease, background 0.2s ease, transform 0.15s ease;
}

.nav-list li a:hover {
  transform: translateY(-1px);
}

.nav-list li.current-menu-item > a,
.nav-list li.active > a {
  color: var(--theme-color);
}


.sub-menu {
  padding: 6px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.16);
  overflow: hidden;
}

.sub-menu li a {
  border-radius: 9px;
}

/* 一级菜单行：链接占满 + 右侧展开按钮（有二级时） */
.mobile-nav__row {
  display: flex;
  align-items: center;
}
.mobile-nav__row .mobile-nav__link {
  flex: 1;
  min-width: 0;
}
/* 展开二级菜单的按钮 */
.mobile-nav__expand {
  flex: 0 0 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin-right: 6px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease;
}
.mobile-nav__expand:hover {
  color: var(--theme-color);
  background: color-mix(in srgb, var(--theme-color) 8%, transparent);
}
.mobile-nav__expand svg {
  transition: transform 0.22s ease;
}
.mobile-nav__expand.is-open svg {
  transform: rotate(180deg);
}
/* 菜单文字：更紧凑的字重与字距，区别于正文 */
:deep(.mobile-nav__link) {
  font-weight: 600;
  letter-spacing: 0.02em;
}
:deep(.mobile-nav__link--child) {
  font-weight: 500;
  letter-spacing: 0;
}


.site-header__search-btn,
.theme-toggle,
.nav-toggle {
  border-radius: 12px;
  transition: background 0.2s ease, color 0.2s ease, transform 0.12s ease;
}

.site-header__search-btn:hover,
.theme-toggle:hover,
.nav-toggle:hover {
  transform: translateY(-1px);
}

.theme-toggle:active,
.site-header__search-btn:active,
.nav-toggle:active {
  transform: scale(0.92);
}

.header-login-btn {
  border-radius: 999px;
  transition: transform 0.12s ease, box-shadow 0.2s ease, filter 0.2s ease;
}

.header-login-btn:hover {
  transform: translateY(-1px);
  filter: brightness(1.05);
}


</style>
