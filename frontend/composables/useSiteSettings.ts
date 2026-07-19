import type { SiteSettings } from '~/types/blog';
const defaultSiteSettings: SiteSettings = {
  siteName: '我的博客',
  siteSubtitle: '记录技术、项目与生活',
  siteDescription: '一个基于 Spring Boot 与 Nuxt 的个人博客系统。',
  siteKeywords: '博客,技术,Nuxt,Spring Boot',
  icp: '',
  footerText: '持续沉淀，认真记录。',
  profileName: '站点管理员',
  profileBio: '记录技术、项目与生活。',
  profileAvatarUrl: '/layout-interaction/static/picture/site-avatar.png',
  allowRegister: true,
  commentsEnabled: true,
  oauthLoginEnabled: false,
  homeHeroEnabled: true,
  homeHeroImageUrl: '',
  homeHeroLinkUrl: '',
  homeHeroLinkType: 'auto',
  homeHeroArticleId: '',
  homeHeroSlides: '',
  homeSignEnabled: true,
  homeSignTitle: 'Hello, World',
  homeSignSubtitle: '欢迎来到我的博客',
  homeTabCategorySlugs: '',
  homeTabLatestEnabled: true,
  homeTabHotEnabled: false,
  homeArticleListEnabled: true,
  homeDynamicsBackground: '',
  homeDynamicsSignature: '',
  homeDynamicsNavVisible: true,
  homeDynamicsNavOrder: 0,
  homeHomeNavVisible: true,
  homeHomeNavOrder: -100,
  homeArchiveNavVisible: true,
  homeArchiveNavOrder: 900,
  homeFriendsNavVisible: true,
  homeFriendsNavOrder: 800,
  amapKey: '',
  weatherEnabled: false,
  notificationPlatformEnabled: false,
  commentReplyEmailEnabled: false,
  announcementEnabled: false,
  announcementContent: '',
  announcementLinkUrl: '',
  friendExchangeName: '',
  friendExchangeBio: '',
  friendExchangeUrl: '',
  friendExchangeLogo: '',
  friendPageIntro: ''
};
export const useSiteSettings = () => {
  const config = useRuntimeConfig();
  const siteSettings = useState<SiteSettings>('site-settings', () => ({
    ...defaultSiteSettings
  }));
  const loaded = useState('site-settings-loaded', () => false);
  const loading = useState('site-settings-loading', () => false);
  // 注意：不要在这里调用 useApiClient()（它会触碰 pinia user store）。
  // 站点公开设置应在 SSR layout 阶段也能安全读取，避免 getActivePinia 报错导致 500。
  const loadSiteSettings = async () => {
    if (loaded.value || loading.value) {
      return siteSettings.value;
    }
    loading.value = true;
    try {
      const response = await $fetch<{ success?: boolean; data?: SiteSettings }>('/site-settings', {
        baseURL: config.public.apiBaseUrl
      });
      if (response?.success && response.data) {
        siteSettings.value = {
          ...defaultSiteSettings,
          ...response.data
        };
        loaded.value = true;
      } else if ((response as any)?.siteName) {
        // 兼容直接返回 data 的情况
        siteSettings.value = {
          ...defaultSiteSettings,
          ...(response as any)
        };
        loaded.value = true;
      }
    } catch {
      siteSettings.value = {
        ...defaultSiteSettings
      };
    } finally {
      loading.value = false;
    }
    return siteSettings.value;
  };
  return {
    siteSettings,
    loadSiteSettings
  };
};
