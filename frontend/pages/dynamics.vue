<template>
  <main class="moments-page">
    <section class="moments-shell">
      <header class="moments-hero">
        <div class="moments-hero__banner" :style="coverStyle">
          <div class="moments-hero__id">
            <span class="moments-hero__name">{{ profileName }}</span>
            <img class="moments-hero__avatar" :src="avatar" :alt="profileName" />
          </div>
        </div>
        <p v-if="signature" class="moments-hero__signature">{{ signature }}</p>
      </header>

      <el-skeleton v-if="loading" class="moments-loading" :rows="6" animated />

      <el-empty v-else-if="!parsedItems.length" description="暂无公开动态" />

    <ul v-else class="timeline">
      <li v-for="item in visibleItems" :key="item.id" class="moment">
          <div class="moment__rail">
            <img class="moment__avatar" :src="avatar" :alt="profileName" />
          </div>
          <div class="moment__body">
            <div class="moment__bubble">
              <template v-if="item.textMarkdown">
                <div class="moment__text-wrap" :class="{ 'is-collapsed': isLong(item) && !isExpanded(item.id) }">
                  <BlogMarkdownPreview class="moment__text" :content="item.textMarkdown" />
                </div>
                <button v-if="isLong(item)" type="button" class="moment__more" @click="toggleExpand(item.id)">
                  {{ isExpanded(item.id) ? '收起' : '阅读更多' }}
                </button>
              </template>
              <div v-if="item.images.length" class="moment__grid" :class="`is-${gridCols(item.images.length)}`">
                <button
                  v-for="(img, i) in item.images.slice(0, 9)"
                  :key="i"
                  type="button"
                  class="moment__img"
                  @click="openViewer(item.images, i)"
                >
                  <img :src="img" :alt="`配图 ${i + 1}`" loading="lazy" decoding="async" />
                  <span v-if="i === 8 && item.images.length > 9" class="moment__img-more">+{{ item.images.length - 9 }}</span>
                </button>
              </div>
            </div>
            <div class="moment__meta">
              <div class="moment__meta-left">
                <time :datetime="postTimeSource(item)" :title="relativeTime(postTimeSource(item))">{{ absoluteTime(postTimeSource(item)) }}</time>
                <button v-if="item.location" type="button" class="moment__location" :title="`在高德地图查看：${item.location}`" @click="viewLocation(item.location)"><MapPin :size="13" />{{ item.location }}</button>
              </div>
              <div v-if="eng(item.id)" class="moment__actions">
                <button type="button" class="moment__action" :class="{ liked: eng(item.id).liked }" @click="toggleLike(item)">
                  <Heart :size="15" :fill="eng(item.id).liked ? 'currentColor' : 'none'" />
                  {{ eng(item.id).likeCount }}
                </button>
                <button type="button" class="moment__action" @click="toggleComments(item)">
                  <MessageCircle :size="15" />
                  {{ eng(item.id).commentCount }}
                </button>
              </div>
            </div>
            <div v-if="eng(item.id) && eng(item.id).open" class="moment__comments">
              <div class="dc-editor">
                <div class="dc-editor__avatar">{{ commentInitial(eng(item.id).form.authorName) }}</div>
                <div class="dc-editor__main">
                  <input
                    v-model="eng(item.id).form.authorName"
                    class="dc-editor__name"
                    placeholder="昵称（选填，默认游客）"
                    maxlength="40"
                  />
                  <textarea
                    v-model="eng(item.id).form.content"
                    class="dc-editor__textarea"
                    placeholder="友善地说点什么…"
                    maxlength="1000"
                    rows="2"
                    @keydown.ctrl.enter.prevent="submitComment(item)"
                  />
                  <div class="dc-editor__foot">
                    <span class="dc-editor__count">{{ eng(item.id).form.content.length }}/1000</span>
                    <button
                      type="button"
                      class="dc-editor__submit"
                      :disabled="eng(item.id).submitting || !eng(item.id).form.content.trim()"
                      @click="submitComment(item)"
                    >
                      发送 Ctrl ↵
                    </button>
                  </div>
                </div>
              </div>

              <el-skeleton v-if="eng(item.id).loadingComments && !eng(item.id).comments.length" :rows="2" animated />
              <ul v-else-if="eng(item.id).comments.length" class="dc-list">
                <li v-for="c in eng(item.id).comments" :key="c.id" class="dc-item" :class="{ 'dc-item--mine': isSelfDynamicComment(c) }">
                  <div class="dc-item__avatar">{{ commentInitial(c.authorName) }}</div>
                  <div class="dc-item__body">
                    <div class="dc-item__head">
                      <span class="dc-item__author" :class="{ 'dc-item__author--mine': isSelfDynamicComment(c) }">{{ c.authorName }}</span>
                      <span v-if="isSelfDynamicComment(c)" class="dc-item__me">我</span>
                      <CommentStatusBadge :status="c.status" />
                      <span v-if="c.region" class="dc-item__region">{{ c.region }}</span>
                      <time class="dc-item__time" :datetime="c.createdAt" :title="absoluteTime(c.createdAt)">{{ relativeTime(c.createdAt) }}</time>
                    </div>
                    <div class="dc-item__text">{{ c.content }}</div>
                    <div class="dc-item__tools">
                      <button v-if="isApprovedDynamicComment(c)" type="button" class="dc-item__reply-btn" @click="openReply(item, c)">回复</button>
                    </div>

                    <!-- 回复列表（一层缩进） -->
                    <ul v-if="c.replies && c.replies.length" class="dc-replies">
                      <li v-for="r in c.replies" :key="r.id" class="dc-reply" :class="{ 'dc-item--mine': isSelfDynamicComment(r) }">
                        <div class="dc-reply__avatar">{{ commentInitial(r.authorName) }}</div>
                        <div class="dc-reply__body">
                          <div class="dc-item__head">
                            <span class="dc-item__author" :class="{ 'dc-item__author--mine': isSelfDynamicComment(r) }">{{ r.authorName }}</span>
                            <span v-if="isSelfDynamicComment(r)" class="dc-item__me">我</span>
                            <CommentStatusBadge :status="r.status" />
                            <span v-if="r.replyTo" class="dc-reply__to">回复 @{{ r.replyTo }}</span>
                            <span v-if="r.region" class="dc-item__region">{{ r.region }}</span>
                            <time class="dc-item__time" :datetime="r.createdAt" :title="absoluteTime(r.createdAt)">{{ relativeTime(r.createdAt) }}</time>
                          </div>
                          <div class="dc-item__text">{{ r.content }}</div>
                          <div class="dc-item__tools">
                            <button v-if="isApprovedDynamicComment(c) && isApprovedDynamicComment(r)" type="button" class="dc-item__reply-btn" @click="openReply(item, r, c)">回复</button>
                          </div>
                        </div>
                      </li>
                    </ul>

                    <button v-if="hasMoreDynamicReplies(c)" type="button" :disabled="dynamicReplyLoading.has(c.id)" @click="loadDynamicReplies(item, c)">加载更多回复</button>
                    <!-- 回复输入框：每个根评论下一个，回复根或其下回复时都在此显示 -->
                    <div v-if="eng(item.id).reply.rootId === c.id" class="dc-reply-form">
                      <input
                        v-model="eng(item.id).reply.authorName"
                        class="dc-editor__name"
                        placeholder="昵称（选填，默认游客）"
                        maxlength="40"
                      />
                      <div class="dc-reply-form__row">
                        <input
                          v-model="eng(item.id).reply.content"
                          class="dc-editor__name dc-reply-form__input"
                          :placeholder="`回复 @${eng(item.id).reply.targetName}`"
                          maxlength="1000"
                          @keyup.enter="submitReply(item)"
                        />
                        <button type="button" class="dc-editor__submit" :disabled="eng(item.id).reply.submitting || !eng(item.id).reply.content.trim()" @click="submitReply(item)">回复</button>
                        <button type="button" class="dc-reply-form__cancel" @click="closeReply(item)">取消</button>
                      </div>
                    </div>
                  </div>
                </li>
              </ul>
              <p v-else-if="!eng(item.id).loadingComments" class="dc-empty">还没有评论，来抢沙发~</p>

              <button
                v-if="eng(item.id).comments.length < eng(item.id).commentsTotal"
                type="button"
                class="dc-more"
                :disabled="eng(item.id).loadingComments"
                @click="loadMoreComments(item)"
              >
                {{ eng(item.id).loadingComments ? '加载中…' : '加载更多评论' }}
              </button>
            </div>
          </div>
        </li>
    </ul>

    <div v-if="showLoadMore" class="moment-load-more">
      <button type="button" class="load-more-btn" :disabled="loading" @click="loadMoreItems">
        加载更多
      </button>
    </div>
  </section>

  </main>
</template>

<script setup lang="ts">
import { Heart, MessageCircle, MapPin } from '@lucide/vue';
import type { DynamicPost, DynamicComment } from '~/types/blog';
import { formatDateTime, formatRelativeTime } from '~/utils/date';
import { getGuestKey } from '~/utils/blog-blocks';
import { buildAvatarUrl } from '~/utils/blog-adapter';
import CommentStatusBadge from '~/components/blog/CommentStatusBadge.vue';
const userStore = useStore.user();
const {
  fetchDynamics,
  toggleDynamicLike,
  fetchDynamicLikeStatus,
  fetchDynamicComments,
  fetchDynamicReplies,
  postDynamicComment
} = useApiClient();
const {
  siteSettings,
  loadSiteSettings
} = useSiteSettings();
await loadSiteSettings();
const loading = ref(false);
const pageSize = 5;
const currentPage = ref(1);
const totalDynamics = ref(0);
const items = ref<DynamicPost[]>([]);
useHead({
  title: computed(() => `动态 - ${siteSettings.value.siteName || '我的博客'}`)
});
const avatar = computed(() => buildAvatarUrl(siteSettings.value.profileAvatarUrl));
const profileName = computed(() => siteSettings.value.profileName || siteSettings.value.siteName);
const profileBio = computed(() => siteSettings.value.profileBio || '');
// 签名优先动态页覆盖，其次侧边栏简介
const signature = computed(() => siteSettings.value.homeDynamicsSignature || siteSettings.value.profileBio || '记录此刻的想法与碎片');
const coverStyle = computed(() => {
  const bg = siteSettings.value.homeDynamicsBackground;
  return bg ? { backgroundImage: `url(${bg})`, backgroundSize: 'cover', backgroundPosition: 'center' } : {};
});
// 把动态正文拆成「图片九宫格」+「纯文本」：图片单独成格，文字交给 md-editor-v3 渲染(与后台一致)。
const parseDynamic = (content?: string) => {
  const raw = String(content || '');
  const images: string[] = [];
  const imgRegex = /!\[[^\]]*\]\(\s*([^)\s]+)[^)]*\)/g;
  let match: RegExpExecArray | null;
  while ((match = imgRegex.exec(raw)) !== null) {
    if (match[1]) images.push(match[1]);
  }
  const textMarkdown = raw
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/^\s*:::.*$/gm, '')
    .trim();
  return {
    images,
    textMarkdown
  };
};
const parsedItems = computed(() => items.value.map(item => ({
  ...item,
  ...parseDynamic(item.content),
  location: normalizeLocation(item.location || '')
})));
const relativeTime = (value?: string | null) => formatRelativeTime(value);
const absoluteTime = (value?: string | null) => formatDateTime(value);
const postTimeSource = (item: DynamicPost) => item.publishedAt || item.createdAt;
const visibleItems = computed(() => parsedItems.value);
const hasMoreItems = computed(() => items.value.length < totalDynamics.value);
const showLoadMore = computed(() => hasMoreItems.value);
const locationNameByRaw = reactive<Record<string, string>>({});
const resolvingLocations = new Set<string>();
// 高德 Web 服务 Key：优先用后台「动态页外观」配置的 amapKey，未配置时回退到内置值（兼容旧站点）。
const amapWebServiceKey = computed(() => siteSettings.value.amapKey || '20a0b24432927686bd7b6e5245dc8c8e');

const isCoordinatePair = (value: string): [number, number] | null => {
  const normalized = value.trim().replace(/；/g, ';').replace(/\s+/g, '');
  const match = /^(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)$/.exec(normalized);
  if (!match) {
    return null;
  }
  const lng = Number(match[1]);
  const lat = Number(match[2]);
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
    return null;
  }
  return [lng, lat];
};

const isCoordinateFallback = (value: string) => /^经纬度\s/.test(value.trim());

const normalizeAmapPart = (value: unknown): string => {
  if (Array.isArray(value)) return value.map(normalizeAmapPart).filter(Boolean).join('');
  return String(value || '').trim();
};

const isWeakLocationName = (value: string) => {
  const text = value.trim();
  if (!text) return true;
  return /^[\d\s#号栋幢楼层单元室\-]+$/.test(text) || /^[\d.]+$/.test(text);
};

const buildAmapLocationLabel = (payload: any, raw: string) => {
  const regeocode = payload?.regeocode;
  if (!regeocode) return '';
  const address = regeocode.addressComponent || {};
  const pois = Array.isArray(regeocode.pois) ? regeocode.pois : [];
  const aois = Array.isArray(regeocode.aois) ? regeocode.aois : [];
  const roads = Array.isArray(regeocode.roads) ? regeocode.roads : [];
  const strongName = [
    ...pois.map((poi: any) => normalizeAmapPart(poi?.name)),
    ...aois.map((aoi: any) => normalizeAmapPart(aoi?.name)),
    ...roads.map((road: any) => normalizeAmapPart(road?.name)),
    normalizeAmapPart(address?.streetNumber?.street),
    normalizeAmapPart(address?.township)
  ].find(name => name && !isWeakLocationName(name));
  const district = [
    normalizeAmapPart(address?.city),
    normalizeAmapPart(address?.district)
  ].filter(Boolean).join('');
  const formatted = normalizeAmapPart(regeocode.formatted_address);
  const label = strongName
    ? `${district && !strongName.startsWith(district) ? district : ''}${strongName}`
    : formatted;
  return label && label !== raw && !isWeakLocationName(label) ? label : '';
};

// 逆地理外部接口可能不可达（如服务器无法访问 nominatim 会一直 pending），
// 必须带超时中断，否则「正在获取位置名称...」会卡住几分钟不回退。
const fetchWithTimeout = async (url: string, ms = 6000) => {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), ms);
  try {
    return await fetch(url, { headers: { accept: 'application/json' }, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
};

const requestAmapLocationName = async (lng: number, lat: number, raw: string) => {
  const response = await fetchWithTimeout(`https://restapi.amap.com/v3/geocode/regeo?key=${amapWebServiceKey.value}&location=${encodeURIComponent(`${lng},${lat}`)}&extensions=all&radius=1000&roadlevel=0`);
  if (!response.ok) {
    throw new Error('amap reverse-geocode failed');
  }
  const payload = await response.json();
  if (payload?.status !== '1') {
    throw new Error(payload?.info || 'amap reverse-geocode failed');
  }
  return buildAmapLocationLabel(payload, raw);
};

const requestOpenLocationName = async (lng: number, lat: number) => {
  const response = await fetchWithTimeout(`https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${encodeURIComponent(lat)}&lon=${encodeURIComponent(lng)}&zoom=18&addressdetails=0`);
  if (!response.ok) {
    throw new Error('reverse-geocode failed');
  }
  const payload = await response.json();
  const label = payload?.name || payload?.display_name || '';
  return typeof label === 'string' && label.trim() ? label.trim() : '';
};

const readLocationCache = (): Record<string, string> => {
  if (!import.meta.client) return {};
  try {
    const raw = localStorage.getItem('dynamic_location_name_cache');
    if (!raw) return {};
    return JSON.parse(raw);
  } catch {
    return {};
  }
};

const saveLocationCache = (cache: Record<string, string>) => {
  if (!import.meta.client) return;
  try {
    localStorage.setItem('dynamic_location_name_cache', JSON.stringify(cache));
  } catch {}
};

const requestLocationName = async (value: string) => {
  const cached = locationNameByRaw[value];
  if ((cached && !isCoordinateFallback(cached)) || !import.meta.client) return;
  if (resolvingLocations.has(value)) return;
  const pair = isCoordinatePair(value);
  if (!pair) {
    locationNameByRaw[value] = value.trim();
    return;
  }
  resolvingLocations.add(value);
  try {
    const [lng, lat] = pair;
    const label = await requestAmapLocationName(lng, lat, value).catch(() => requestOpenLocationName(lng, lat));
    locationNameByRaw[value] = label || `经纬度 ${value}`;
  } catch {
    locationNameByRaw[value] = `经纬度 ${value}`;
  } finally {
    resolvingLocations.delete(value);
    saveLocationCache({ ...locationNameByRaw });
  }
};

const normalizeLocation = (raw: string) => {
  const source = raw.trim();
  if (!source) return '';
  if (/[\u4e00-\u9fff]/.test(source)) return source;
  const cached = locationNameByRaw[source];
  if (cached && !isCoordinateFallback(cached)) {
    return cached;
  }
  if (isCoordinatePair(source)) {
    if (import.meta.client) {
      void requestLocationName(source);
      return '正在获取位置名称...';
    }
    return `经纬度 ${source}`;
  }
  return source;
};

// 点击动态的位置：在高德地图网页打开。坐标对用 marker 直接定位，否则按名称搜索。
const viewLocation = (location?: string) => {
  const raw = String(location || '').trim();
  if (!raw) return;
  const display = normalizeLocation(raw);
  let url: string;
  if (isCoordinatePair(raw)) {
    const [lng, lat] = raw.split(',').map(s => s.trim());
    const name = display && !isCoordinateFallback(display) && display !== '正在获取位置名称...' ? display : '发布位置';
    url = `https://uri.amap.com/marker?position=${lng},${lat}&name=${encodeURIComponent(name)}&src=blog&coordinate=gaode&callnative=1`;
  } else {
    url = `https://uri.amap.com/search?keyword=${encodeURIComponent(raw)}&src=blog&callnative=1`;
  }
  window.open(url, '_blank', 'noopener,noreferrer');
};

const hydrateLocationNames = async () => {
  if (!import.meta.client) return;
  const cache = readLocationCache();
  Object.assign(locationNameByRaw, cache);
  const uniqRaw = Array.from(new Set(items.value.map(item => String(item.location || '').trim()).filter(Boolean)));
  for (let i = 0; i < uniqRaw.length; i += 3) {
    await Promise.allSettled(uniqRaw.slice(i, i + 3).map(item => requestLocationName(item)));
  }
};

const loadMoreItems = async () => {
  if (loading.value || !hasMoreItems.value) return;
  loading.value = true;
  try {
    const response = await fetchDynamics(currentPage.value, pageSize, getGuestKey());
    if (response.success) {
      items.value.push(...response.data.items);
      totalDynamics.value = response.data.totalItems;
      currentPage.value += 1;
      response.data.items.forEach(ensureEngagement);
      void hydrateLocationNames();
    }
  } finally { loading.value = false; }
};
// 长文折叠：正文较长(字数或行数超阈值)时默认折叠，提供「阅读更多/收起」。
const expandedSet = reactive<Set<number>>(new Set());
const isExpanded = (id: number) => expandedSet.has(id);
const toggleExpand = (id: number) => {
  if (expandedSet.has(id)) expandedSet.delete(id);
  else expandedSet.add(id);
};
const isLong = (item: { textMarkdown?: string }) => {
  // 音乐组件标签本身很长，不能按字符数算「长文」，否则纯音乐动态会被 max-height 裁掉播放器。
  const text = String(item.textMarkdown || '')
    .replace(/<music-player\b[\s\S]*?<\/music-player>/gi, '')
    .replace(/<music-player\b[^>]*\/?>/gi, '')
    .trim();
  if (!text) return false;
  return text.length > 120 || text.split(/\n/).length > 5;
};
// 1 张大图、2/4 张两列、其余按三列九宫格。
const gridCols = (count: number) => count === 1 ? 1 : (count === 2 || count === 4) ? 2 : 3;
const openViewer = (images: string[], index: number) => {
  if (!import.meta.client) return;
  window.dispatchEvent(new CustomEvent('bd:image-preview', {
    detail: {
      urls: images,
      index
    }
  }));
};
// 互动：按动态 id 维护点赞/评论状态。
interface ReplyState {
  rootId: number | null;
  targetId: number | null;
  targetName: string;
  authorName: string;
  content: string;
  submitting: boolean;
}
interface MomentEngagement {
  liked: boolean;
  likeCount: number;
  commentCount: number;
  open: boolean;
  comments: DynamicComment[];
  loadingComments: boolean;
  commentsPage: number;
  commentsTotal: number;
  form: { authorName: string; content: string };
  submitting: boolean;
  reply: ReplyState;
  generation: number;
  commentRequest: number;
  pollAttempts: number;
}
const COMMENT_PAGE_SIZE = 5;
const GUEST_PROFILE_KEY = 'bd-guest-comment-profile';
const readGuestAuthor = () => {
  if (!import.meta.client) return '';
  try {
    const raw = localStorage.getItem(GUEST_PROFILE_KEY);
    if (!raw) return '';
    const parsed = JSON.parse(raw) as { author?: string };
    return String(parsed.author || '').trim().slice(0, 64);
  } catch {
    return '';
  }
};
const saveGuestAuthor = (name: string) => {
  if (!import.meta.client) return;
  const author = name.trim().slice(0, 64);
  if (!author) return;
  try {
    // 尽量保留文章评论里已有的邮箱/网站字段
    let email = '';
    let website = '';
    try {
      const raw = localStorage.getItem(GUEST_PROFILE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as { email?: string; website?: string };
        email = String(parsed.email || '');
        website = String(parsed.website || '');
      }
    } catch { /* ignore */ }
    localStorage.setItem(GUEST_PROFILE_KEY, JSON.stringify({ author, email, website }));
    rememberedGuestName.value = author;
  } catch {
    // ignore
  }
};
const rememberedGuestName = ref('');
const isSelfDynamicComment = (comment: DynamicComment) => comment.mine === true;
const isApprovedDynamicComment = (comment: DynamicComment) => !comment.status || comment.status === 'APPROVED';
const newReplyState = (): ReplyState => ({ rootId: null, targetId: null, targetName: '', authorName: '', content: '', submitting: false });
const engagement = reactive<Record<number, MomentEngagement>>({});
const ensureEngagement = (item: DynamicPost) => {
  if (!engagement[item.id]) {
    engagement[item.id] = {
      liked: Boolean(item.liked),
      likeCount: Number(item.likeCount || 0),
      commentCount: Number(item.commentCount || 0),
      open: false,
      comments: [],
      loadingComments: false,
      commentsPage: 0,
      commentsTotal: 0,
      form: { authorName: rememberedGuestName.value || '', content: '' },
      submitting: false,
      reply: newReplyState(),
      generation: 0,
      commentRequest: 0,
      pollAttempts: 0
    };
  }
  return engagement[item.id]!;
};
const eng = (id: number) => engagement[id]!;
const dynamicReplyPages = ref<Record<number, number>>({});
const dynamicReplyTotals = ref<Record<number, number>>({});
const dynamicReplyLoading = reactive(new Set<number>());
const dynamicPollTimers = new Map<number, ReturnType<typeof setTimeout>>();
const dynamicControllers = new Map<number, Set<AbortController>>();
let dynamicCommentsDisposed = false;
const dynamicIdentity = () => `${userStore.tokenName || ''}:${userStore.accessToken || ''}`;
const captureDynamicContext = (state: MomentEngagement) => ({ generation: state.generation, identity: dynamicIdentity() });
const dynamicContextCurrent = (state: MomentEngagement, context: ReturnType<typeof captureDynamicContext>) =>
  !dynamicCommentsDisposed && state.open && state.generation === context.generation && context.identity === dynamicIdentity();
const clearDynamicPoll = (id: number) => {
  const timer = dynamicPollTimers.get(id);
  if (timer !== undefined) clearTimeout(timer);
  dynamicPollTimers.delete(id);
};
const beginDynamicRequest = (id: number) => {
  const controller = new AbortController();
  if (!dynamicControllers.has(id)) dynamicControllers.set(id, new Set());
  dynamicControllers.get(id)!.add(controller);
  return controller;
};
const finishDynamicRequest = (id: number, controller: AbortController) => dynamicControllers.get(id)?.delete(controller);
const invalidateDynamicComments = (id: number) => {
  const state = engagement[id];
  if (!state) return;
  state.generation += 1;
  state.loadingComments = false;
  state.submitting = false;
  state.reply.submitting = false;
  clearDynamicPoll(id);
  dynamicControllers.get(id)?.forEach(controller => controller.abort());
  dynamicControllers.delete(id);
  state.comments.forEach(root => dynamicReplyLoading.delete(root.id));
};
const mergeDynamicComments = (existing: DynamicComment[], incoming: DynamicComment[]): DynamicComment[] => {
  const byId = new Map(existing.map(comment => [comment.id, comment]));
  for (const comment of incoming) {
    const previous = byId.get(comment.id);
    byId.set(comment.id, previous ? { ...previous, ...comment, replies: mergeDynamicComments(previous.replies || [], comment.replies || []) } : comment);
  }
  return [...byId.values()];
};
const hasOwnPendingDynamicComment = (state: MomentEngagement) => state.comments.some(root =>
  (root.mine && root.status === 'PENDING') || root.replies?.some(reply => reply.mine && reply.status === 'PENDING'));
const hasMoreDynamicReplies = (root: DynamicComment) => dynamicReplyTotals.value[root.id] !== undefined
  ? (root.replies?.length || 0) < dynamicReplyTotals.value[root.id]!
  : (root.replies?.filter(isApprovedDynamicComment).length || 0) < (root.replyCount || 0) || (root.replies?.length || 0) >= 2;
const dynamicStatusSnapshot = (state: MomentEngagement) => {
  const statuses = new Map<number, boolean>();
  for (const root of state.comments) {
    const publicRoot = isApprovedDynamicComment(root);
    statuses.set(root.id, publicRoot);
    for (const reply of root.replies || []) statuses.set(reply.id, publicRoot && isApprovedDynamicComment(reply));
  }
  return statuses;
};
const applyDynamicStatusCounts = (state: MomentEngagement, before: Map<number, boolean>) => {
  for (const [id, approved] of dynamicStatusSnapshot(state)) {
    if (before.has(id) && before.get(id) !== approved) state.commentCount = Math.max(0, state.commentCount + (approved ? 1 : -1));
  }
};
const scheduleDynamicPoll = (item: DynamicPost) => {
  const state = ensureEngagement(item);
  clearDynamicPoll(item.id);
  if (!import.meta.client || dynamicCommentsDisposed || !state.open || state.pollAttempts >= 36 || !hasOwnPendingDynamicComment(state)) return;
  dynamicPollTimers.set(item.id, setTimeout(async () => {
    dynamicPollTimers.delete(item.id);
    state.pollAttempts += 1;
    await pollDynamicComments(item);
    scheduleDynamicPoll(item);
  }, 5000));
};
const pollDynamicComments = async (item: DynamicPost) => {
  const state = ensureEngagement(item);
  if (state.loadingComments || dynamicReplyLoading.size) return;
  const context = captureDynamicContext(state);
  const request = ++state.commentRequest;
  const controller = beginDynamicRequest(item.id);
  const before = dynamicStatusSnapshot(state);
  state.loadingComments = true;
  try {
    const pages = await Promise.all(Array.from({ length: Math.max(1, state.commentsPage) }, (_, page) =>
      fetchDynamicComments(item.id, page, COMMENT_PAGE_SIZE, controller.signal)));
    if (!dynamicContextCurrent(state, context) || request !== state.commentRequest || pages.some(page => !page.success)) return;
    const refreshed = mergeDynamicComments([], pages.flatMap(page => page.data.items));
    for (const root of refreshed) {
      const previous = state.comments.find(comment => comment.id === root.id);
      if (!previous || (!dynamicReplyPages.value[root.id] && !previous.replies?.some(reply => reply.mine && reply.status === 'PENDING'))) continue;
      const replyPages = await Promise.all(Array.from({ length: Math.max(1, Math.ceil((previous.replies?.length || 0) / 10)) }, (_, page) =>
        fetchDynamicReplies(item.id, root.id, page, 10, controller.signal)));
      if (!dynamicContextCurrent(state, context) || request !== state.commentRequest) return;
      if (replyPages.every(page => page.success)) {
        root.replies = mergeDynamicComments(root.replies || [], replyPages.flatMap(page => page.data.items));
        dynamicReplyPages.value[root.id] = replyPages.length;
        dynamicReplyTotals.value[root.id] = replyPages[0]!.data.totalItems;
      }
    }
    const publicStatus = await fetchDynamicLikeStatus(item.id, getGuestKey(), controller.signal).catch(() => null);
    if (!dynamicContextCurrent(state, context) || request !== state.commentRequest) return;
    state.comments = refreshed;
    state.commentsTotal = pages[0]!.data.totalItems;
    applyDynamicStatusCounts(state, before);
    if (publicStatus?.success && Number.isFinite(publicStatus.data.commentCount)) state.commentCount = publicStatus.data.commentCount;
  } catch { /* A failed refresh keeps the current view; the bounded poll can retry. */ }
  finally {
    finishDynamicRequest(item.id, controller);
    if (dynamicContextCurrent(state, context) && request === state.commentRequest) state.loadingComments = false;
  }
};
// 评论头像首字母（与文章评论 initial() 一致的观感）。
const commentInitial = (name?: string) => {
  const text = String(name || '').trim();
  return text ? text.slice(0, 1).toUpperCase() : '游';
};
const toggleLike = async (item: DynamicPost) => {
  const state = ensureEngagement(item);
  try {
    const res = await toggleDynamicLike(item.id, getGuestKey());
    if (res.success) {
      state.liked = res.data.liked;
      state.likeCount = res.data.likeCount;
    }
  } catch {}
};
const loadComments = async (item: DynamicPost, append = false) => {
  const state = ensureEngagement(item);
  if (state.loadingComments || !state.open || dynamicCommentsDisposed) return;
  const context = captureDynamicContext(state);
  const request = ++state.commentRequest;
  const controller = beginDynamicRequest(item.id);
  state.loadingComments = true;
  const page = append ? state.commentsPage : 0;
  try {
    const res = await fetchDynamicComments(item.id, page, COMMENT_PAGE_SIZE, controller.signal);
    if (res.success && dynamicContextCurrent(state, context) && request === state.commentRequest) {
      const data = res.data;
      const before = dynamicStatusSnapshot(state);
      state.comments = append ? mergeDynamicComments(state.comments, data.items) : mergeDynamicComments([], data.items);
      applyDynamicStatusCounts(state, before);
      state.commentsTotal = data.totalItems ?? state.comments.length;
      state.commentsPage = page + 1;
    }
  } catch {} finally {
    finishDynamicRequest(item.id, controller);
    if (dynamicContextCurrent(state, context) && request === state.commentRequest) {
      state.loadingComments = false;
      scheduleDynamicPoll(item);
    }
  }
};
const loadDynamicReplies = async (item: DynamicPost, root: DynamicComment) => {
  const state = ensureEngagement(item);
  if (!state.open || dynamicCommentsDisposed || dynamicReplyLoading.has(root.id)) return;
  const context = captureDynamicContext(state);
  const controller = beginDynamicRequest(item.id);
  dynamicReplyLoading.add(root.id);
  try {
    const page = dynamicReplyPages.value[root.id] || 0;
    const response = await fetchDynamicReplies(item.id, root.id, page, 10, controller.signal);
    if (response.success && dynamicContextCurrent(state, context) && state.comments.find(comment => comment.id === root.id) === root) {
      const before = dynamicStatusSnapshot(state);
      const previousReplies = new Map((root.replies || []).map(reply => [reply.id, isApprovedDynamicComment(reply)]));
      root.replies = mergeDynamicComments(root.replies || [], response.data.items);
      for (const reply of root.replies) {
        const approved = isApprovedDynamicComment(reply);
        if (previousReplies.has(reply.id) && previousReplies.get(reply.id) !== approved) {
          root.replyCount = Math.max(0, (root.replyCount || 0) + (approved ? 1 : -1));
        }
      }
      applyDynamicStatusCounts(state, before);
      dynamicReplyTotals.value[root.id] = response.data.totalItems;
      dynamicReplyPages.value[root.id] = page + 1;
    }
  } catch {} finally {
    finishDynamicRequest(item.id, controller);
    if (dynamicContextCurrent(state, context)) {
      dynamicReplyLoading.delete(root.id);
      scheduleDynamicPoll(item);
    }
  }
};
const loadMoreComments = (item: DynamicPost) => loadComments(item, true);
const toggleComments = async (item: DynamicPost) => {
  const state = ensureEngagement(item);
  state.open = !state.open;
  if (!state.open) {
    invalidateDynamicComments(item.id);
  } else {
    state.pollAttempts = 0;
    state.comments.forEach(root => { delete dynamicReplyPages.value[root.id]; delete dynamicReplyTotals.value[root.id]; });
    await loadComments(item, false);
  }
};
const submitComment = async (item: DynamicPost) => {
  const state = ensureEngagement(item);
  const content = state.form.content.trim();
  if (!content || state.submitting || !state.open || dynamicCommentsDisposed) return;
  const context = captureDynamicContext(state);
  const draft = state.form.content;
  state.submitting = true;
  try {
    const res = await postDynamicComment(item.id, {
      authorName: state.form.authorName.trim() || '游客',
      content
    });
    if (res.success && dynamicContextCurrent(state, context)) {
      state.commentRequest += 1;
      state.loadingComments = false;
      state.comments = mergeDynamicComments([{ ...res.data, replies: [] }], state.comments.filter(comment => comment.id !== res.data.id));
      if (isApprovedDynamicComment(res.data)) state.commentCount += 1;
      state.commentsTotal += 1;
      saveGuestAuthor(state.form.authorName);
      if (state.form.content === draft) state.form.content = '';
      state.pollAttempts = 0;
      scheduleDynamicPoll(item);
    }
  } catch {} finally {
    if (dynamicContextCurrent(state, context)) state.submitting = false;
  }
};
// root：被回复评论所属的根评论；target：被回复对象（可为根或某条回复）。
const openReply = (item: DynamicPost, target: DynamicComment, root?: DynamicComment) => {
  const state = ensureEngagement(item);
  if (!isApprovedDynamicComment(target) || (root && !isApprovedDynamicComment(root))) return;
  const rootId = root ? root.id : (target.parentId ?? target.id);
  if (!state.form.authorName && rememberedGuestName.value) {
    state.form.authorName = rememberedGuestName.value;
  }
  state.reply = {
    ...newReplyState(),
    rootId,
    targetId: target.id,
    targetName: target.authorName,
    authorName: state.form.authorName || rememberedGuestName.value || ''
  };
};
const closeReply = (item: DynamicPost) => {
  ensureEngagement(item).reply = newReplyState();
};
const submitReply = async (item: DynamicPost) => {
  const state = ensureEngagement(item);
  const reply = state.reply;
  const content = reply.content.trim();
  if (!content || reply.submitting || !reply.targetId || !state.open || dynamicCommentsDisposed) return;
  const context = captureDynamicContext(state);
  reply.submitting = true;
  try {
    const res = await postDynamicComment(item.id, {
      authorName: reply.authorName.trim() || '游客',
      content,
      parentId: reply.targetId
    });
    if (res.success && dynamicContextCurrent(state, context)) {
      state.commentRequest += 1;
      state.loadingComments = false;
      const root = state.comments.find(c => c.id === reply.rootId);
      if (root) {
        root.replies = mergeDynamicComments(root.replies || [], [res.data]);
        if (isApprovedDynamicComment(res.data)) root.replyCount = (root.replyCount || 0) + 1;
        if (dynamicReplyTotals.value[root.id] !== undefined) dynamicReplyTotals.value[root.id]! += 1;
      }
      if (isApprovedDynamicComment(res.data)) state.commentCount += 1;
      saveGuestAuthor(reply.authorName);
      state.form.authorName = reply.authorName.trim() || state.form.authorName;
      if (state.reply === reply && state.reply.content.trim() === content) state.reply = newReplyState();
      state.pollAttempts = 0;
      scheduleDynamicPoll(item);
    }
  } catch {} finally {
    reply.submitting = false;
  }
};
watch(() => [userStore.accessToken, userStore.tokenName], () => {
  Object.entries(engagement).forEach(([key, state]) => {
    const id = Number(key);
    invalidateDynamicComments(id);
    state.comments.forEach(root => { delete dynamicReplyPages.value[root.id]; delete dynamicReplyTotals.value[root.id]; });
    state.comments = [];
    state.commentsPage = 0;
    state.commentsTotal = 0;
    state.reply = newReplyState();
    state.pollAttempts = 0;
    if (state.open) void loadComments({ id } as DynamicPost);
  });
}, { flush: 'sync' });
onBeforeUnmount(() => {
  dynamicCommentsDisposed = true;
  Object.keys(engagement).forEach(key => invalidateDynamicComments(Number(key)));
});
// 首屏列表改为 SSR 期 fetch（useAsyncData）：服务端直出 HTML，配合 routeRules 的 swr:30 让 nitro 缓存真正命中、首屏不再闪空壳。
// 注意：回调内只做纯 $fetch，不碰 window/localStorage/AMap，保证 SSR 不报错。
const { data: initialDynamics } = await useAsyncData('dynamics-list', async () => {
  const response = await fetchDynamics(0, pageSize);
  return response.success ? response.data : { items: [], totalItems: 0 };
}, { default: () => ({ items: [] as DynamicPost[], totalItems: 0 }) });
items.value = initialDynamics.value.items;
totalDynamics.value = initialDynamics.value.totalItems;
// engagement 初始化是纯响应式赋值（无浏览器依赖），服务端/客户端一致执行，避免互动区块水合不一致。
items.value.forEach(ensureEngagement);
// 浏览器专属增强：定位逆地理（localStorage + AMap/网络）与游客点赞状态（guestKey 取自 localStorage），仅客户端执行，不影响首屏 HTML。
onMounted(async () => {
  rememberedGuestName.value = readGuestAuthor();
  // 已创建的 engagement 表单回填昵称
  Object.values(engagement).forEach(state => {
    if (!state.form.authorName && rememberedGuestName.value) {
      state.form.authorName = rememberedGuestName.value;
    }
  });
  await hydrateLocationNames();
  const response = await fetchDynamics(0, pageSize, getGuestKey());
  if (response.success) {
    response.data.items.forEach(item => { const state = ensureEngagement(item); state.liked = Boolean(item.liked); });
  }
});
</script>

<style lang="scss" scoped>
.moments-page {
  min-height: calc(100vh - 80px);
  padding: 6rem 1.5rem 3rem;
  background: var(--bg-color);
}
.moments-shell {
  max-width: 720px;
  margin: 0 auto;
}

/* 顶部个人卡片：渐变封面 + 头像 */
.moments-hero {
  margin-bottom: 1.8rem;
}
.moments-hero__banner {
  position: relative;
  height: 300px;
  border-radius: 14px;
  overflow: hidden;
  background:
    radial-gradient(120% 140% at 100% 0%, #6ea8fe 0%, transparent 55%),
    linear-gradient(120deg, #2583e8, #4f46e5);
  background-size: cover;
  background-position: center;
}
.moments-hero__id {
  position: absolute;
  right: 18px;
  bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.moments-hero__name {
  font-size: 1.15rem;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
}
.moments-hero__avatar {
  width: 58px;
  height: 58px;
  border-radius: 12px;
  object-fit: cover;
  border: 2px solid #fff;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.3);
  background: #fff;
}
.moments-hero__signature {
  margin: 0.8rem 0 0;
  text-align: right;
  font-size: 0.9rem;
  color: var(--text-muted, #909399);
}
@media screen and (max-width: 600px) {
  .moments-hero__banner {
    height: 168px;
  }
  .moments-hero__avatar {
    width: 46px;
    height: 46px;
  }
  .moments-hero__name {
    font-size: 1rem;
  }
}

/* 时间线 */
.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  position: relative;
}
.moment {
  display: flex;
  gap: 14px;
  position: relative;
  padding-bottom: 1.6rem;
}
/* 头像列与连接线 */
.moment__rail {
  position: relative;
  flex: 0 0 44px;
  display: flex;
  justify-content: center;
}
.moment:not(:last-child) .moment__rail::after {
  content: '';
  position: absolute;
  top: 48px;
  bottom: -1.6rem;
  width: 2px;
  background: linear-gradient(var(--border-color), transparent);
}
.moment__avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--card-bg-color);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.12);
  background: #fff;
  z-index: 1;
}
.moment__body {
  flex: 1;
  min-width: 0;
}
/* 内容卡片（去掉旧版聊天气泡尖角，改为干净的社交卡片） */
.moment__bubble {
  position: relative;
  padding: 1rem 1.1rem;
  border-radius: 16px;
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition: box-shadow 0.18s ease, border-color 0.18s ease;
}
.moment__bubble:hover {
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.1);
  border-color: color-mix(in srgb, var(--theme-color, #409eff) 30%, var(--border-color));
}
.moment__bubble::before {
  display: none;
}
.moment__text {
  color: var(--text-color);
  line-height: 1.75;
  word-break: break-word;
}
/* 长文折叠：收起态限制高度并底部渐隐，配合「阅读更多」按钮 */
.moment__text-wrap {
  position: relative;
}
.moment__text-wrap.is-collapsed {
  max-height: 7.2em;
  overflow: hidden;
}
/* 折叠态仍要完整展示音乐播放器，避免黑胶被 max-height 裁切 */
.moment__text-wrap.is-collapsed :deep(music-player) {
  max-height: none;
}
/* 动态气泡内：播放器贴合卡片全宽 */
.moment__bubble :deep(music-player) {
  display: block;
  width: 100% !important;
  max-width: 100% !important;
  margin: 6px 0 0 !important;
}
.moment__bubble :deep(.md-editor-preview > p:has(music-player)) {
  margin: 0.15em 0 0;
}
.moment__bubble :deep(.md-editor-preview > p:has(> music-player:only-child)) {
  margin: 0;
}
.moment__text-wrap.is-collapsed::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2.4em;
  background: linear-gradient(transparent, var(--card-bg-color));
  pointer-events: none;
}
.moment__more {
  margin-top: 0.35rem;
  padding: 0;
  border: 0;
  background: none;
  color: #2583e8;
  font-size: 0.82rem;
  cursor: pointer;
}
.moment__more:hover {
  text-decoration: underline;
}
/* 九宫格配图 */
.moment__grid {
  display: grid;
  gap: 6px;
  margin-top: 0.7rem;
  &.is-1 {
    grid-template-columns: minmax(0, 70%);
  }
  &.is-2 {
    grid-template-columns: repeat(2, 1fr);
  }
  &.is-3 {
    grid-template-columns: repeat(3, 1fr);
  }
}
.moment__img {
  padding: 0;
  border: 0;
  background: none;
  cursor: zoom-in;
  aspect-ratio: 1 / 1;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
    transition: transform 0.25s ease;
  }
  &:hover img {
    transform: scale(1.05);
  }
}
/* 九宫格超 9 张时第 9 格盖「+N」遮罩 */
.moment__img-more {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 1.1rem;
  font-weight: 600;
}
.is-1 .moment__img {
  aspect-ratio: auto;
  max-height: 360px;
  img {
    height: auto;
    max-height: 360px;
    object-fit: cover;
  }
}
.moment__meta {
  margin-top: 0.5rem;
  padding-left: 2px;
  font-size: 0.78rem;
  color: var(--text-muted, #909399);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.moment__meta-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.moment__location {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 0;
  border: 0;
  background: transparent;
  font: inherit;
  color: #2583e8;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.18s ease;
}
.moment__location:hover {
  color: #1a6fd0;
  text-decoration: underline;
}
.moment__actions {
  display: flex;
  gap: 14px;
  flex: 0 0 auto;
}
.moment__action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--text-muted, #909399);
  font-size: 0.8rem;
  cursor: pointer;
  transition: color 0.2s ease;
}
.moment__action:hover {
  color: #2583e8;
}
.moment__action.liked {
  color: #ef4444;
}
.moment__comments {
  margin-top: 0.7rem;
  padding: 0.85rem;
  border-radius: 14px;
  background: rgba(127, 127, 127, 0.06);
  border: 1px solid var(--border-color);
}

/* 评论编辑区：头像 + 昵称 + 多行输入 + 发送，对齐文章评论卡片观感 */
.dc-editor {
  display: flex;
  gap: 10px;
  margin-bottom: 0.85rem;
}
.dc-editor__avatar,
.dc-item__avatar {
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--theme-color, #409eff);
  background: color-mix(in srgb, var(--theme-color, #409eff) 14%, transparent);
  user-select: none;
}
.dc-editor__main {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 8px;
}
.dc-editor__name,
.dc-editor__textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--card-bg-color, #fff);
  color: var(--text-color);
  font-size: 0.88rem;
  line-height: 1.6;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.dc-editor__name {
  height: 36px;
}
.dc-editor__textarea {
  resize: vertical;
  min-height: 60px;
  font-family: inherit;
}
.dc-editor__name:focus,
.dc-editor__textarea:focus {
  outline: none;
  border-color: #2583e8;
  box-shadow: 0 0 0 3px color-mix(in srgb, #2583e8 14%, transparent);
}
.dc-editor__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.dc-editor__count {
  font-size: 0.74rem;
  color: var(--text-muted, #909399);
}
.dc-editor__submit {
  height: 32px;
  padding: 0 16px;
  border: 0;
  border-radius: 999px;
  background: #2583e8;
  color: #fff;
  font-size: 0.82rem;
  cursor: pointer;
  transition: filter 0.2s ease, transform 0.1s ease;
}
.dc-editor__submit:hover:not(:disabled) {
  filter: brightness(1.06);
}
.dc-editor__submit:active:not(:disabled) {
  transform: scale(0.97);
}
.dc-editor__submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 评论列表：每条头像 + 昵称/时间 + 内容，卡片风 */
.dc-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.7rem;
}
.dc-item {
  display: flex;
  gap: 10px;
}
.dc-item__avatar {
  background: color-mix(in srgb, var(--theme-color, #409eff) 14%, transparent);
}
.dc-item__body {
  flex: 1;
  min-width: 0;
  padding: 0.55rem 0.75rem;
  border-radius: 4px 12px 12px 12px;
  background: var(--card-bg-color, #fff);
  border: 1px solid var(--border-color);
}
.dc-item__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: flex-start;
  gap: 8px;
  margin-bottom: 2px;
}
.dc-item__author {
  overflow-wrap: anywhere;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-highlight-color);
}
.dc-item__author--mine {
  color: var(--theme-color, #409eff);
  font-weight: 700;
}
.dc-item__me {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 6px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  color: var(--theme-color, #409eff);
  background: color-mix(in srgb, var(--theme-color, #409eff) 14%, transparent);
  border: 1px solid color-mix(in srgb, var(--theme-color, #409eff) 28%, transparent);
}
.dc-item--mine {
  border-radius: 10px;
  background: color-mix(in srgb, var(--theme-color, #409eff) 6%, transparent);
}
.dc-item__time {
  margin-left: auto;
  flex: 0 0 auto;
  font-size: 0.72rem;
  color: var(--text-muted, #909399);
}
.dc-item__text {
  font-size: 0.86rem;
  line-height: 1.65;
  color: var(--text-color);
  word-break: break-word;
}
.dc-item__tools {
  margin-top: 3px;
}
.dc-item__region {
  font-size: 0.72rem;
  color: var(--text-muted, #909399);
  padding: 0 6px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--text-muted, #909399) 12%, transparent);
}
.dc-item__reply-btn {
  padding: 0;
  border: 0;
  background: none;
  color: var(--text-muted, #909399);
  font-size: 0.76rem;
  cursor: pointer;
}
.dc-item__reply-btn:hover {
  color: #2583e8;
}
/* 回复列表：在根评论气泡内缩进一层 */
.dc-replies {
  list-style: none;
  margin: 0.5rem 0 0;
  padding: 0.4rem 0 0 0.6rem;
  display: grid;
  gap: 0.5rem;
  border-left: 2px solid var(--border-color);
}
.dc-reply {
  display: flex;
  gap: 8px;
}
.dc-reply__avatar {
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--theme-color, #409eff);
  background: color-mix(in srgb, var(--theme-color, #409eff) 14%, transparent);
  user-select: none;
}
.dc-reply__body {
  flex: 1;
  min-width: 0;
}
.dc-reply__to {
  font-size: 0.72rem;
  color: #2583e8;
}
.dc-reply-form {
  margin-top: 0.5rem;
  display: grid;
  gap: 6px;
}
.dc-reply-form__row {
  display: flex;
  gap: 6px;
}
.dc-reply-form__input {
  flex: 1;
  min-width: 0;
}
.dc-reply-form__cancel {
  flex: 0 0 auto;
  padding: 0 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: none;
  color: var(--text-muted, #909399);
  font-size: 0.8rem;
  cursor: pointer;
}
.dc-more {
  margin-top: 0.6rem;
  width: 100%;
  padding: 6px 0;
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  background: none;
  color: var(--text-muted, #909399);
  font-size: 0.8rem;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease;
}
.dc-more:hover:not(:disabled) {
  color: #2583e8;
  border-color: #2583e8;
}
.dc-more:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.dc-empty {
  margin: 0;
  font-size: 0.82rem;
  color: var(--text-muted, #909399);
}
.moment-load-more {
  display: flex;
  justify-content: center;
  padding: 1.4rem 0 0.4rem;
}

@media screen and (max-width: 600px) {
  .moments-page {
    padding: 5.4rem 1rem 2rem;
  }
  .moment__grid.is-3 {
    gap: 5px;
  }
}
</style>
