<script setup lang="ts">
import type { Article } from '~/types/blog';
import { siteMeta } from '~/data/blog';
import { normalizeOptionalAssetUrl } from '~/utils/blog-adapter';
const props = defineProps<{
  articles: Article[];
}>();
const {
  siteSettings
} = useSiteSettings();
const featuredArticle = computed<Article | undefined>(() => props.articles[0]);

type HeroSlide = {
  imageUrl: string;
  linkType: string;
  linkUrl: string;
  articleId: string;
  signEnabled: boolean;
  signTitle: string;
  signSubtitle: string;
};

// 多图轮播：解析后台 home.heroSlides(JSON)，丢弃无图项。
// 仅在列表为空时兼容历史 homeHeroImageUrl 单图配置（后台已不再展示兜底配置项）。
const parsedSlides = computed<HeroSlide[]>(() => {
  const raw = String(siteSettings.value.homeHeroSlides || '').trim();
  if (!raw) return [];
  try {
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    return arr
      .map((item: any) => ({
        imageUrl: String(item?.imageUrl || ''),
        linkType: String(item?.linkType || 'none'),
        linkUrl: String(item?.linkUrl || ''),
        articleId: item?.articleId ? String(item.articleId) : '',
        signEnabled: item?.signEnabled === true || item?.signEnabled === 'true',
        signTitle: String(item?.signTitle || ''),
        signSubtitle: String(item?.signSubtitle || '')
      }))
      .filter(slide => slide.imageUrl);
  } catch {
    return [];
  }
});
// 兼容历史单图字段（后台不再编辑；仅旧数据迁移期使用）。
const legacySlide = computed<HeroSlide | null>(() => {
  const imageUrl = normalizeOptionalAssetUrl(siteSettings.value.homeHeroImageUrl);
  if (!imageUrl) return null;
  return {
    imageUrl,
    linkType: siteSettings.value.homeHeroLinkType || 'none',
    linkUrl: siteSettings.value.homeHeroLinkUrl || '',
    articleId: siteSettings.value.homeHeroArticleId || '',
    signEnabled: !!siteSettings.value.homeSignEnabled,
    signTitle: siteSettings.value.homeSignTitle || '',
    signSubtitle: siteSettings.value.homeSignSubtitle || ''
  };
});
const slides = computed<HeroSlide[]>(() => {
  const list = parsedSlides.value.length
    ? parsedSlides.value
    : (legacySlide.value ? [legacySlide.value] : []);
  return list.filter(slide => normalizeOptionalAssetUrl(slide.imageUrl));
});

const articleTitleById = (id: string) => {
  if (!id) return '';
  const hit = props.articles.find(item => String(item.slug) === String(id) || String((item as any).id) === String(id));
  return hit?.title || '';
};

const imageOf = (slide: HeroSlide) => normalizeOptionalAssetUrl(slide.imageUrl);
// none=不跳转；auto=首篇文章；article/custom=指定目标。
const linkOf = (slide: HeroSlide) => {
  const type = slide.linkType || 'none';
  if (type === 'none') return '';
  if (type === 'article' && slide.articleId) return `/article/${slide.articleId}`;
  if (type === 'custom' && slide.linkUrl.trim()) return slide.linkUrl.trim();
  if (type === 'auto') {
    if (slide.linkUrl.trim()) return slide.linkUrl.trim();
    return featuredArticle.value ? `/article/${featuredArticle.value.slug}` : '';
  }
  return '';
};
const isExternal = (link: string) => /^https?:\/\//i.test(link);
const altOf = (slide: HeroSlide) => signTitleOf(slide) || featuredArticle.value?.title || siteSettings.value.siteName || siteMeta.name;

const signTitleOf = (slide: HeroSlide) => {
  if (!slide.signEnabled) return '';
  const custom = String(slide.signTitle || '').trim();
  if (custom) return custom;
  if (slide.linkType === 'article' && slide.articleId) {
    return articleTitleById(slide.articleId) || featuredArticle.value?.title || '';
  }
  return '';
};
const signTitlePartsOf = (slide: HeroSlide) => signTitleOf(slide)
  .split(/\n|<br\s*\/?>/i)
  .map(part => part.trim())
  .filter(Boolean);
const signSubtitleOf = (slide: HeroSlide) => (slide.signEnabled ? String(slide.signSubtitle || '').trim() : '');
</script>

<template>
  <section v-if="siteSettings.homeHeroEnabled && slides.length" class="slider-component" style="--slider-h: 300px; --slider-h-mobile: 200px;">
    <!-- 单图 -->
    <div v-if="slides.length === 1" class="hero-single">
      <a
        v-if="linkOf(slides[0]!) && isExternal(linkOf(slides[0]!))"
        :href="linkOf(slides[0]!)"
        target="_blank"
        rel="noopener noreferrer"
        class="slider-component__link"
      >
        <img :src="imageOf(slides[0]!)" :alt="altOf(slides[0]!)" width="1100" height="300" loading="eager" decoding="async" fetchpriority="high" />
      </a>
      <NuxtLink v-else-if="linkOf(slides[0]!)" :to="linkOf(slides[0]!)" class="slider-component__link">
        <img :src="imageOf(slides[0]!)" :alt="altOf(slides[0]!)" width="1100" height="300" loading="eager" decoding="async" fetchpriority="high" />
      </NuxtLink>
      <div v-else class="slider-component__link is-static">
        <img :src="imageOf(slides[0]!)" :alt="altOf(slides[0]!)" width="1100" height="300" loading="eager" decoding="async" fetchpriority="high" />
      </div>
      <div v-if="signTitlePartsOf(slides[0]!).length || signSubtitleOf(slides[0]!)" class="hero-sign">
        <h2 v-if="signTitlePartsOf(slides[0]!).length" class="hero-sign__title">
          <template v-for="(part, index) in signTitlePartsOf(slides[0]!)" :key="`${part}-${index}`">
            <br v-if="index > 0" />
            {{ part }}
          </template>
        </h2>
        <div v-if="signSubtitleOf(slides[0]!)" class="hero-sign__subtitle">{{ signSubtitleOf(slides[0]!) }}</div>
      </div>
    </div>

    <!-- 多图：PC 悬停显示左右箭头；触控可滑动；底部指示点可点。 -->
    <el-carousel
      v-else
      class="hero-carousel"
      height="100%"
      :interval="5000"
      arrow="hover"
      indicator-position=""
      :pause-on-hover="true"
      trigger="click"
      :loop="true"
    >
      <el-carousel-item v-for="(slide, index) in slides" :key="index">
        <a
          v-if="linkOf(slide) && isExternal(linkOf(slide))"
          :href="linkOf(slide)"
          target="_blank"
          rel="noopener noreferrer"
          class="slider-component__link"
        >
          <img :src="imageOf(slide)" :alt="altOf(slide)" width="1100" height="300" :loading="index === 0 ? 'eager' : 'lazy'" decoding="async" :fetchpriority="index === 0 ? 'high' : undefined" />
        </a>
        <NuxtLink v-else-if="linkOf(slide)" :to="linkOf(slide)" class="slider-component__link">
          <img :src="imageOf(slide)" :alt="altOf(slide)" width="1100" height="300" :loading="index === 0 ? 'eager' : 'lazy'" decoding="async" :fetchpriority="index === 0 ? 'high' : undefined" />
        </NuxtLink>
        <div v-else class="slider-component__link is-static">
          <img :src="imageOf(slide)" :alt="altOf(slide)" width="1100" height="300" :loading="index === 0 ? 'eager' : 'lazy'" decoding="async" :fetchpriority="index === 0 ? 'high' : undefined" />
        </div>
        <div v-if="signTitlePartsOf(slide).length || signSubtitleOf(slide)" class="hero-sign">
          <h2 v-if="signTitlePartsOf(slide).length" class="hero-sign__title">
            <template v-for="(part, pIndex) in signTitlePartsOf(slide)" :key="`${part}-${pIndex}`">
              <br v-if="pIndex > 0" />
              {{ part }}
            </template>
          </h2>
          <div v-if="signSubtitleOf(slide)" class="hero-sign__subtitle">{{ signSubtitleOf(slide) }}</div>
        </div>
      </el-carousel-item>
    </el-carousel>
  </section>
</template>

<style scoped>
.hero-single,
.hero-carousel {
  position: relative;
  width: 100%;
  height: 100%;
}
.hero-carousel :deep(.el-carousel__container) {
  height: 100% !important;
}
.hero-carousel :deep(.el-carousel__item) {
  height: 100%;
  /* 允许水平滑动切图；避免链接拖拽干扰 */
  touch-action: pan-y;
  user-select: none;
}
/* PC：悬停显示箭头，半透明以免遮挡签名 */
.hero-carousel :deep(.el-carousel__arrow) {
  z-index: 5;
  width: 36px;
  height: 36px;
  background: rgba(15, 23, 42, 0.38);
  border: 1px solid rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(4px);
}
.hero-carousel :deep(.el-carousel__arrow:hover) {
  background: rgba(15, 23, 42, 0.55);
}
.hero-carousel :deep(.el-carousel__indicators) {
  z-index: 4;
  bottom: 14px;
}
.hero-carousel :deep(.el-carousel__indicator) {
  padding: 4px 3px;
}
/* 指示点默认半透明白；激活态加长高亮，明确当前第几张 */
.hero-carousel :deep(.el-carousel__button) {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.45);
  opacity: 1;
  transition: width .2s ease, background .2s ease, box-shadow .2s ease;
}
.hero-carousel :deep(.el-carousel__indicator.is-active .el-carousel__button) {
  width: 18px;
  background: #fff;
  box-shadow: 0 0 0 1px rgba(15, 23, 42, 0.12), 0 2px 8px rgba(15, 23, 42, 0.25);
}
.slider-component__link {
  display: block;
  width: 100%;
  height: 100%;
  -webkit-user-drag: none;
}
.slider-component__link.is-static {
  cursor: default;
}
.slider-component__link img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  pointer-events: none;
  -webkit-user-drag: none;
}
.hero-sign {
  position: absolute;
  left: 28px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 3;
  max-width: min(52%, 520px);
  color: #fff;
  text-shadow: 0 2px 12px rgba(15, 23, 42, 0.45);
  pointer-events: none;
}
.hero-sign__title {
  margin: 0;
  font-size: clamp(22px, 3.2vw, 34px);
  font-weight: 800;
  line-height: 1.25;
}
.hero-sign__subtitle {
  margin-top: 10px;
  font-size: 14px;
  opacity: 0.92;
}
@media (max-width: 700px) {
  .hero-sign {
    left: 16px;
    max-width: 70%;
  }
  .hero-sign__title {
    font-size: 20px;
  }
  .hero-sign__subtitle {
    font-size: 12px;
  }
}
</style>
