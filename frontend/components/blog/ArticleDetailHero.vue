<script setup lang="ts">
import { CalendarDays, Eye, FileText, MapPin, MessageCircle, PenLine, Tags, UserRound } from '@lucide/vue';
import type { Article, Category } from '~/types/blog';
import { buildCoverUrl, NO_COVER_IMAGE } from '~/utils/blog-adapter';
const props = defineProps<{
  article: Article;
  category?: Category;
}>();
// 无真实封面时 cover 是「暂无图片」占位图(no-cover.png)。此时 hero 不铺占位图，统一用蓝色背景。
const hasRealCover = computed(() => {
  const cover = props.article.cover || '';
  return Boolean(cover) && !cover.startsWith('data:') && !cover.includes(NO_COVER_IMAGE);
});
const coverSrc = computed(() => buildCoverUrl(props.article.cover, 1200));
type Rgb = {
  r: number;
  g: number;
  b: number;
};
const defaultHeroRgb: Rgb = {
  r: 64,
  g: 158,
  b: 255
};
const wordCount = computed(() => (props.article.rawContent || props.article.content.join('')).replace(/\s+/g, '').length);
const isReprint = computed(() => String(props.article.sourceType || 'ORIGINAL').toUpperCase() === 'REPRINT');
// 访客 IP 属地（省/市）：客户端按访客 IP 实时取，内网/失败则留空不显示。
const { fetchVisitorLocation } = useApiClient();
const visitorLocation = ref('');
onMounted(async () => {
  try {
    const res = await fetchVisitorLocation();
    if (res.success && res.data?.region) {
      visitorLocation.value = res.data.region;
    }
  } catch {}
});
// 有真实封面才用封面主色/分类色；无封面统一蓝色(defaultHeroRgb=#409eff)。
const heroRgb = computed<Rgb>(() => {
  if (!hasRealCover.value) return defaultHeroRgb;
  return hexToRgb(props.article.heroColor) ?? hexToRgb(props.category?.accent) ?? defaultHeroRgb;
});
const heroStyle = computed(() => {
  const main = normalizeHeroColor(heroRgb.value);
  const deep = shiftLightness(main, -0.16);
  const glow = shiftLightness(main, 0.18);
  return {
    '--article-hero-main': rgbToCss(main),
    '--article-hero-deep': rgbToCss(deep),
    '--article-hero-glow': rgbToCss(glow),
    '--article-hero-main-rgb': `${main.r} ${main.g} ${main.b}`
  };
});
const htmlHeroStyle = computed(() => {
  const style = heroStyle.value;
  return [`--article-hero-main: ${style['--article-hero-main']}`, `--article-hero-deep: ${style['--article-hero-deep']}`, `--article-hero-glow: ${style['--article-hero-glow']}`, `--article-hero-main-rgb: ${style['--article-hero-main-rgb']}`].join('; ');
});
useHead(() => ({
  htmlAttrs: {
    style: htmlHeroStyle.value
  }
}));
function hexToRgb(hex?: string | null): Rgb | null {
  if (!hex) return null;
  const normalized = hex.replace('#', '').trim();
  if (!/^[\da-f]{6}$/i.test(normalized)) return null;
  return {
    r: Number.parseInt(normalized.slice(0, 2), 16),
    g: Number.parseInt(normalized.slice(2, 4), 16),
    b: Number.parseInt(normalized.slice(4, 6), 16)
  };
}
function rgbToCss(color: Rgb) {
  return `rgb(${color.r}, ${color.g}, ${color.b})`;
}
function normalizeHeroColor(color: Rgb): Rgb {
  const hsl = rgbToHsl(color);
  return hslToRgb({
    h: hsl.h,
    s: clamp(Math.max(hsl.s, 0.42), 0.38, 0.82),
    l: clamp(hsl.l, 0.34, 0.56)
  });
}
function shiftLightness(color: Rgb, amount: number): Rgb {
  const hsl = rgbToHsl(color);
  return hslToRgb({
    ...hsl,
    l: clamp(hsl.l + amount, 0.18, 0.78)
  });
}
function rgbToHsl({
  r,
  g,
  b
}: Rgb) {
  const rn = r / 255;
  const gn = g / 255;
  const bn = b / 255;
  const max = Math.max(rn, gn, bn);
  const min = Math.min(rn, gn, bn);
  const delta = max - min;
  const l = (max + min) / 2;
  const s = delta === 0 ? 0 : delta / (1 - Math.abs(2 * l - 1));
  let h = 0;
  if (delta !== 0) {
    if (max === rn) h = (gn - bn) / delta % 6;else if (max === gn) h = (bn - rn) / delta + 2;else h = (rn - gn) / delta + 4;
  }
  return {
    h: (h * 60 + 360) % 360,
    s,
    l
  };
}
function hslToRgb({
  h,
  s,
  l
}: {
  h: number;
  s: number;
  l: number;
}): Rgb {
  const c = (1 - Math.abs(2 * l - 1)) * s;
  const x = c * (1 - Math.abs(h / 60 % 2 - 1));
  const m = l - c / 2;
  let rn = 0;
  let gn = 0;
  let bn = 0;
  if (h < 60) [rn, gn, bn] = [c, x, 0];else if (h < 120) [rn, gn, bn] = [x, c, 0];else if (h < 180) [rn, gn, bn] = [0, c, x];else if (h < 240) [rn, gn, bn] = [0, x, c];else if (h < 300) [rn, gn, bn] = [x, 0, c];else [rn, gn, bn] = [c, 0, x];
  return {
    r: Math.round((rn + m) * 255),
    g: Math.round((gn + m) * 255),
    b: Math.round((bn + m) * 255)
  };
}
function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}
</script>

<template>
  <section class="article-detail-hero" :class="{ 'article-detail-hero--no-cover': !hasRealCover }" :style="heroStyle">
    <div v-if="hasRealCover" class="article-detail-hero__cover" aria-hidden="true">
      <img :src="coverSrc" :alt="article.title" loading="lazy" decoding="async" style="aspect-ratio: 16 / 9;" />
    </div>

    <NuxtLink v-if="hasRealCover" class="article-detail-hero__preview" :to="`/article/${article.slug}`" aria-label="查看文章封面">
      <img :src="coverSrc" :alt="article.title" loading="eager" decoding="async" fetchpriority="high" style="aspect-ratio: 16 / 9;" />
    </NuxtLink>

    <div class="article-detail-hero__inner">
      <div class="article-detail-hero__info">
        <div class="article-detail-hero__firstline">
          <a v-if="isReprint && article.sourceUrl" class="article-detail-hero__original" :href="article.sourceUrl" target="_blank" rel="noopener noreferrer">
            <PenLine :size="14" />
            转载
          </a>
          <span v-else class="article-detail-hero__original">
            <PenLine :size="14" />
            {{ isReprint ? '转载' : '原创' }}
          </span>
          <NuxtLink v-if="category" class="article-detail-hero__category" :to="`/category/${category.slug}`">
            {{ category.label }}
          </NuxtLink>
          <div class="article-detail-hero__tags">
            <NuxtLink v-for="tag in article.tags" :key="tag" :to="`/tags/${encodeURIComponent(tag)}`">
              <Tags :size="13" />
              {{ tag }}
            </NuxtLink>
          </div>
        </div>

        <h1 class="article-detail-hero__title">{{ article.title }}</h1>

        <div class="article-detail-hero__meta" aria-label="文章信息">
          <time :datetime="article.date" :title="article.date">
            <CalendarDays :size="15" />
            {{ article.date }}
          </time>
          <span title="作者">
            <UserRound :size="15" />
            {{ article.author }}
          </span>
          <span title="字数">
            <FileText :size="15" />
            {{ wordCount }} 字
          </span>
          <span title="浏览">
            <Eye :size="15" />
            {{ article.views }} 阅读
          </span>
          <span title="评论">
            <MessageCircle :size="15" />
            {{ article.comments }} 评论
          </span>
          <span v-if="visitorLocation" title="定位">
            <MapPin :size="15" />
            {{ visitorLocation }}
          </span>
          <span v-if="article.location" title="文章定位" class="article-detail-hero__location">
            <MapPin :size="15" />
            {{ article.location }}
          </span>
        </div>
      </div>
    </div>

    <div class="article-detail-hero__wave" aria-hidden="true">
      <svg viewBox="0 0 1440 96" preserveAspectRatio="none" focusable="false">
        <path d="M0 56L48 61.3C96 67 192 77 288 72C384 67 480 45 576 37.3C672 29 768 35 864 45.3C960 56 1056 72 1152 72C1248 72 1344 56 1392 48L1440 40V96H0Z" />
      </svg>
    </div>
  </section>
</template>
