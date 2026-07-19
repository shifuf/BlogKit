<script setup lang="ts">
import { GitFork, Mail, Rss } from '@lucide/vue';
import { siteMeta } from '~/data/blog';
import type { FriendLinkSummary } from '~/types/blog';
import { sanitizeFooterHtml } from '~/utils/sanitize-html';
const { siteSettings } = useSiteSettings();
const { fetchFriendLinks } = useApiClient();
const subscribeOpen = ref(false);
// 不用 lazy：lazy 会让 SSR 先吐空列表、客户端再补数据，导致页脚友链 hydration 子节点数量不一致。
const { data: friendLinks } = await useAsyncData('site-friend-links', async () => {
  try {
    const response = await fetchFriendLinks();
    return response.success
      ? response.data.filter((link) => link.showInFooter === true)
      : [];
  } catch {
    return [];
  }
}, { default: () => [] as FriendLinkSummary[] });
const currentYear = new Date().getFullYear();
const siteName = computed(() => siteSettings.value.siteName || siteMeta.name);
const siteDesc = computed(() => siteSettings.value.siteDescription || siteSettings.value.siteSubtitle || siteMeta.description);
const githubUrl = computed(() => siteSettings.value.profileGithub || siteMeta.github || '');
const emailValue = computed(() => siteSettings.value.profileEmail || '');
const safeFooterInfo = computed(() => sanitizeFooterHtml(String(siteSettings.value.footerInfo || '')));
const route = useRoute();
watch(() => route.query.subscribe, value => {
  if (value === '1' || value === 'true') {
    subscribeOpen.value = true;
  }
}, { immediate: true });
</script>

<template>
  <footer class="site-footer">
    <div class="site-footer__waves" aria-hidden="true">
      <svg class="site-footer__wave-svg" viewBox="0 24 150 28" preserveAspectRatio="none">
        <defs>
          <path id="core-wave" d="M-160 44c30 0 58-18 88-18s58 18 88 18 58-18 88-18 58 18 88 18v44h-352z" />
        </defs>
        <g class="site-footer__wave-parallax">
          <use href="#core-wave" x="48" y="0" fill="var(--bg-card)" />
        </g>
      </svg>
    </div>

    <div class="site-footer__inner">
      <div class="site-footer__top">
        <div class="site-footer__brand">
          <div class="site-footer__name">{{ siteName }}</div>
          <p class="site-footer__desc">{{ siteDesc }}</p>
          <div class="site-footer__socials">
            <a v-if="githubUrl" :href="githubUrl" target="_blank" rel="nofollow noopener" aria-label="GitHub" class="site-footer__social">
              <GitFork :size="16" aria-hidden="true" />
            </a>
            <a v-if="emailValue" :href="`mailto:${emailValue}`" aria-label="邮箱" class="site-footer__social">
              <Mail :size="16" aria-hidden="true" />
            </a>
            <a href="/feed.xml" aria-label="RSS" class="site-footer__social">
              <Rss :size="16" aria-hidden="true" />
            </a>
            <button type="button" class="site-footer__social site-footer__social--text" aria-label="邮件订阅" @click="subscribeOpen = true">
              订阅
            </button>
          </div>
        </div>

        <div class="site-footer__friends">
          <span class="site-footer__friends-label">友情链接</span>
          <div class="site-footer__friend-list">
            <a
              v-for="link in friendLinks"
              :key="link.id"
              class="site-footer__friend"
              :href="link.url"
              target="_blank"
              rel="nofollow noopener"
              :title="link.description || link.name"
            >{{ link.name }}</a>
          </div>
        </div>
      </div>

      <div class="site-footer__divider"></div>

      <div class="site-footer__meta">
        <div class="site-footer__copyright">
          <span v-if="safeFooterInfo" v-html="safeFooterInfo"></span>
          <span v-else>{{ siteSettings.footerText || '持续沉淀，认真记录。' }}</span>
          <span class="site-footer__dot">·</span>
          <span>© {{ currentYear }} {{ siteName }}</span>
        </div>
        <a v-if="siteSettings.icp" class="site-footer__beian" href="https://beian.miit.gov.cn/" target="_blank" rel="nofollow noopener">
          <img src="/layout-interaction/static/picture/icp.svg" alt="ICP备案" />
          {{ siteSettings.icp }}
        </a>
      </div>
    </div>
    <BlogSubscribeModal v-model:open="subscribeOpen" />
  </footer>
</template>

<style scoped>
.site-footer {
  position: relative;
  margin-top: auto;
  flex-shrink: 0;
  padding-top: 36px;
  background:
    radial-gradient(60% 80% at 50% 0%, rgba(64, 158, 255, 0.06), transparent 70%),
    var(--bg-card, #ffffff);
  color: var(--text-secondary, #475569);
  border-top: 1px solid color-mix(in srgb, var(--border-color, #e8edf3) 60%, transparent);
}
.site-footer__waves {
  position: absolute;
  left: 0;
  right: 0;
  top: -28px;
  height: 32px;
  overflow: hidden;
  pointer-events: none;
}
.site-footer__wave-svg {
  width: 100%;
  height: 100%;
  display: block;
}
.site-footer__inner {
  width: min(1200px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 8px 0 24px;
}
.site-footer__top {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 28px;
  align-items: flex-start;
}
.site-footer__brand {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.site-footer__name {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--text-primary, #1e293b);
}
.site-footer__desc {
  margin: 0;
  max-width: 460px;
  line-height: 1.6;
}
.site-footer__socials {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}
.site-footer__social {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary, #64748b);
  background: color-mix(in srgb, var(--bg-soft, #f1f5f9) 80%, transparent);
  border: 1px solid color-mix(in srgb, var(--border-color, #e5e7eb) 70%, transparent);
  text-decoration: none;
  transition: color .18s ease, background .18s ease, transform .15s ease;
}
.site-footer__social--text {
  width: auto;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid color-mix(in srgb, var(--border-color, #e5e7eb) 70%, transparent);
  background: color-mix(in srgb, var(--bg-soft, #f1f5f9) 80%, transparent);
  cursor: pointer;
}
.site-footer__social:hover {
  color: #fff;
  background: var(--theme-color, #409eff);
  border-color: transparent;
  transform: translateY(-1px);
}
.site-footer__friends {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.site-footer__friends-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-muted, #94a3b8);
}
.site-footer__friend-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
}
.site-footer__friend {
  padding: 4px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--bg-soft, #f1f5f9) 80%, transparent);
  color: var(--text-secondary, #475569);
  font-size: 12.5px;
  transition: color 0.18s ease, background 0.18s ease, transform 0.15s ease;
  text-decoration: none;
}
.site-footer__friend:hover {
  color: #fff;
  background: var(--theme-color, #409eff);
  transform: translateY(-1px);
}
.site-footer__divider {
  height: 1px;
  margin: 22px 0 14px;
  background: linear-gradient(90deg, transparent, color-mix(in srgb, var(--border-color, #e5e7eb) 80%, transparent) 20%, color-mix(in srgb, var(--border-color, #e5e7eb) 80%, transparent) 80%, transparent);
}
.site-footer__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12.5px;
  color: var(--text-tertiary, #64748b);
}
.site-footer__copyright {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.site-footer__dot {
  color: var(--text-muted, #cbd5e1);
}
.site-footer__beian {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-tertiary, #64748b);
  transition: color 0.18s ease;
}
.site-footer__beian:hover {
  color: var(--theme-color, #409eff);
}
.site-footer__beian img {
  width: 14px;
  height: 14px;
}
html[data-theme='dark'] .site-footer {
  background:
    radial-gradient(60% 80% at 50% 0%, rgba(96, 165, 250, 0.08), transparent 70%),
    var(--bg-card, #1f2937);
  border-top-color: color-mix(in srgb, var(--border-color, #374151) 60%, transparent);
}
html[data-theme='dark'] .site-footer__name {
  color: var(--text-primary, #f9fafb);
}
html[data-theme='dark'] .site-footer__friend {
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-secondary, #d1d5db);
}
html[data-theme='dark'] .site-footer__friend:hover {
  background: var(--theme-color, #409eff);
  color: #fff;
}
html[data-theme='dark'] .site-footer__social {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.08);
}
@media (max-width: 720px) {
  .site-footer {
    padding-top: 28px;
  }
  .site-footer__top {
    grid-template-columns: 1fr;
    gap: 22px;
  }
}
</style>
