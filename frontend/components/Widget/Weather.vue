<script setup lang="ts">
// 自绘心知天气组件：数据经 Nitro 代理 /weather-proxy 获取（避开跨域/CSP），不再注入心知 bundle.js。
// 图标按接口返回的 code（天气现象编码）取自心知 CDN：/widget2/assets/img/chameleon/56/{code}.svg。
// 随容器宽度自适应：窄容器（侧栏/移动端）竖向紧凑排布，宽容器横向展开。
const props = withDefaults(defineProps<{
  compact?: boolean;
}>(), {
  compact: false
});

interface WeatherTile {
  header: string;
  value: string;
  // 图标型 tile（今日天气：白天/夜间现象图标）
  icons?: { code: string; label: string }[];
}
interface WeatherState {
  location: string;
  text: string;
  temperature: string;
  iconCode: string;
  updateAt: string;
  todayLow: string;
  todayHigh: string;
  sunrise: string;
  sunset: string;
  suggestion: string;
  tiles: WeatherTile[];
}

const ICON_BASE = 'https://cdn.sencdn.com/widget2/assets/img/chameleon/56';
const iconUrl = (code: string) => `${ICON_BASE}/${code}.svg`;

const state = ref<WeatherState | null>(null);
const loading = ref(true);
const failed = ref(false);
const failReason = ref('');
const rootEl = ref<HTMLElement | null>(null);
const isWide = ref(false);
let observer: ResizeObserver | null = null;

// 按心知天气现象编码（code）粗分类，决定 hero 头部的天空渐变主题。
// 晴白天/晴夜间/多云阴/雨/雪/雾霾沙 各一套渐变，暗色亮色通吃（hero 固定亮色渐变+白字）。
const skyKey = computed(() => {
  const code = Number(state.value?.iconCode ?? NaN);
  if (Number.isNaN(code)) return 'default';
  if (code === 0 || code === 2) return 'sunny';        // 晴（白天）/晴间多云
  if (code === 1 || code === 3) return 'night';        // 晴（夜间）
  if (code >= 4 && code <= 9) return 'cloudy';         // 多云 ~ 阴
  if (code >= 10 && code <= 19) return 'rain';         // 各类雨 + 冻雨
  if (code >= 20 && code <= 25) return 'snow';         // 各类雪
  if (code >= 26 && code <= 31) return 'haze';         // 尘/沙/雾/霾
  return 'default';
});

const mainText = (node: { text?: string; suffix?: string }) => `${node?.text ?? ''}${node?.suffix ?? ''}`;

const parseWeather = (payload: any): WeatherState | null => {
  const results = Array.isArray(payload?.results) ? payload.results : [];
  const main = results.find((r: any) => r.UIType === 'main');
  const record = main?.data?.[0];
  if (!record) return null;
  const tiles: WeatherTile[] = results
    .filter((r: any) => r.UIType === 'tile')
    .map((r: any) => {
      const tile = r.data?.[0] || {};
      const content = Array.isArray(tile.content) ? tile.content : [];
      const icons = content
        .filter((c: any) => c.type === 'icon' && c.text)
        .map((c: any) => ({ code: String(c.text), label: '' }));
      const value = content.map(mainText).join('').trim();
      return { header: String(tile.header || ''), value, icons: icons.length ? icons : undefined } as WeatherTile;
    });
  return {
    location: String(record.location || ''),
    text: String(record.text || ''),
    temperature: String(record.temperature || ''),
    iconCode: String(record.code?.now || record.code?.day || ''),
    updateAt: String(record.updateAt || ''),
    todayLow: String(record.today?.low || ''),
    todayHigh: String(record.today?.high || ''),
    sunrise: String(record.sun?.rise || ''),
    sunset: String(record.sun?.set || ''),
    suggestion: String(record.suggestion || ''),
    tiles
  };
};

const loadWeather = async () => {
  loading.value = true;
  failed.value = false;
  failReason.value = '';
  try {
    const { unwrapApiData } = await import('~/utils/apiPayloadCrypto');
    const payload = await $fetch('/weather-proxy');
    // 未配置 token 时 proxy 返回明文 error（不密封）
    if (payload && typeof payload === 'object' && (payload as any).error && !(payload as any).enc) {
      failReason.value = String((payload as any).message || (payload as any).error);
      failed.value = true;
      return;
    }
    const data = await unwrapApiData(payload);
    if (data && (data as any).success === false) {
      failReason.value = String((data as any).message || (data as any).error || '天气加载失败');
      failed.value = true;
      return;
    }
    const parsed = parseWeather(data);
    if (parsed) {
      state.value = parsed;
    } else {
      failReason.value = '天气数据为空';
      failed.value = true;
    }
  } catch {
    failReason.value = '天气服务暂时不可用';
    failed.value = true;
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  void loadWeather();
  if (rootEl.value && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(entries => {
      const width = entries[0]?.contentRect.width ?? 0;
      // 宽度阈值：≥360 横向展开（正文/宽侧栏），否则竖向紧凑（窄侧栏/移动端）。
      isWide.value = width >= 360;
    });
    observer.observe(rootEl.value);
  }
});

onUnmounted(() => {
  observer?.disconnect();
  observer = null;
});
</script>

<template>
  <section
    ref="rootEl"
    class="weather-widget"
    :class="{ 'weather-widget--compact': props.compact, 'weather-widget--wide': isWide }"
    aria-label="实时天气"
  >
    <div v-if="!props.compact" class="widget-title widget-title-mac weather-widget__title">实时天气</div>

    <div v-if="loading" class="weather-widget__skeleton">
      <div class="weather-widget__skeleton-row" />
      <div class="weather-widget__skeleton-row weather-widget__skeleton-row--short" />
    </div>

    <div v-else-if="failed || !state" class="weather-widget__failed">
      <span>{{ failReason || '天气加载失败' }}</span>
      <button type="button" @click="loadWeather">重试</button>
    </div>

    <template v-else>
      <div class="weather-widget__main" :data-sky="skyKey">
        <span class="weather-widget__sky" aria-hidden="true" />
        <span class="weather-widget__scrim" aria-hidden="true" />

        <div class="weather-widget__topline">
          <span v-if="state.location" class="weather-widget__loc">{{ state.location }}</span>
          <span v-if="state.updateAt" class="weather-widget__update">{{ state.updateAt }}</span>
        </div>

        <div class="weather-widget__body">
          <div class="weather-widget__iconwrap">
            <img
              v-if="state.iconCode"
              class="weather-widget__icon"
              :src="iconUrl(state.iconCode)"
              :alt="state.text"
              loading="lazy"
              decoding="async"
            />
          </div>
          <div class="weather-widget__now">
            <div class="weather-widget__temp">
              {{ state.temperature }}<span class="weather-widget__temp-unit">°</span>
            </div>
            <div class="weather-widget__desc">
              <span class="weather-widget__text">{{ state.text }}</span>
              <span v-if="state.todayLow || state.todayHigh" class="weather-widget__range-temp">
                {{ state.todayLow }} ~ {{ state.todayHigh }}
              </span>
            </div>
          </div>
        </div>

        <div v-if="state.sunrise || state.sunset" class="weather-widget__sun">
          <span v-if="state.sunrise" class="weather-widget__sun-item">
            <span class="weather-widget__sun-ico" aria-hidden="true">☀</span>{{ state.sunrise }}
          </span>
          <span class="weather-widget__sun-arc" aria-hidden="true" />
          <span v-if="state.sunset" class="weather-widget__sun-item">
            <span class="weather-widget__sun-ico" aria-hidden="true">☾</span>{{ state.sunset }}
          </span>
        </div>

        <!-- 三栏并入 hero 主图同级：今日天气图标 / 今日气温 / 明日气温，保持原三栏布局 -->
        <div v-if="state.tiles.length" class="weather-widget__tiles">
          <div v-for="(tile, index) in state.tiles" :key="index" class="weather-widget__tile">
            <span class="weather-widget__tile-header">{{ tile.header }}</span>
            <span v-if="tile.icons" class="weather-widget__tile-icons">
              <template v-for="(ic, i) in tile.icons" :key="i">
                <img :src="iconUrl(ic.code)" alt="" class="weather-widget__tile-icon" loading="lazy" />
                <span v-if="i < tile.icons.length - 1" class="weather-widget__tile-sep">/</span>
              </template>
            </span>
            <span v-else class="weather-widget__tile-value">{{ tile.value }}</span>
          </div>
        </div>
      </div>

      <p v-if="state.suggestion && isWide" class="weather-widget__suggestion">{{ state.suggestion }}</p>
    </template>
  </section>
</template>

<style scoped lang="scss">
.weather-widget {
  overflow: hidden;
  padding: 14px;
  border-radius: 5px;
  background: var(--bg-card);
}

.weather-widget__title {
  margin-bottom: 10px;
}

/* 主区 hero：按天气现象着色的渐变头部。顶栏(地点+发布时间) + 主体(图标+温度/现象) + 日出日落。
   文字统一白色，叠一层从底部渐深的 scrim 保证任何天空色下都清晰可读（修复原「看不清」）。 */
.weather-widget__main {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 14px;
  overflow: hidden;
  color: #fff;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.14);
  background: linear-gradient(135deg, #4a90d9, #67a9e6);
  isolation: isolate;
}

/* 动效层：柔和高光随天气缓慢流动，营造「活」的天空感。 */
.weather-widget__sky {
  position: absolute;
  inset: 0;
  z-index: -2;
  background:
    radial-gradient(120px 80px at 78% 18%, rgba(255, 255, 255, 0.35), transparent 70%),
    radial-gradient(90px 60px at 20% 82%, rgba(255, 255, 255, 0.18), transparent 70%);
  animation: weather-drift 9s ease-in-out infinite alternate;
}

/* 可读性遮罩：顶/底各压一层暗，保证浅色天空(雪/晴)下白字与图标依然清晰。 */
.weather-widget__scrim {
  position: absolute;
  inset: 0;
  z-index: -1;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.16) 0%, transparent 32%, transparent 62%, rgba(0, 0, 0, 0.26) 100%);
}

@keyframes weather-drift {
  from { transform: translate3d(-6px, -3px, 0) scale(1.02); }
  to   { transform: translate3d(6px, 4px, 0) scale(1.06); }
}

.weather-widget__topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.weather-widget__body {
  display: flex;
  align-items: center;
  gap: 14px;
}

/* 图标托盘：半透明玻璃底 + 模糊，给彩色天气图标一个统一底板，避免融进背景看不清。 */
.weather-widget__iconwrap {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 66px;
  height: 66px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.28);
  backdrop-filter: blur(3px);
}

.weather-widget__icon {
  width: 50px;
  height: 50px;
  filter: drop-shadow(0 3px 6px rgba(0, 0, 0, 0.28));
  animation: weather-float 3.6s ease-in-out infinite;
}

@keyframes weather-float {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-4px); }
}

.weather-widget__now {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.weather-widget__temp {
  display: flex;
  align-items: flex-start;
  font-size: 40px;
  font-weight: 700;
  line-height: 1;
  color: #fff;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
}

.weather-widget__temp-unit {
  font-size: 22px;
  font-weight: 600;
  margin-top: 2px;
}

.weather-widget__desc {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.95);
}

.weather-widget__text {
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
}

.weather-widget__loc {
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.24);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  backdrop-filter: blur(2px);
}

.weather-widget__range-temp {
  padding: 2px 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.2);
  font-weight: 600;
  white-space: nowrap;
}

.weather-widget__update {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.82);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}

/* 日出日落：两端时间，中间虚线弧示意太阳轨迹。 */
.weather-widget__sun {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.9);
}

.weather-widget__sun-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}

.weather-widget__sun-ico {
  font-size: 12px;
}

.weather-widget__sun-arc {
  flex: 1;
  height: 0;
  border-top: 1px dashed rgba(255, 255, 255, 0.5);
  transform: translateY(-1px);
}

/* 各天气主题渐变：晴(暖蓝)/夜(深蓝紫)/多云(灰蓝)/雨(青灰)/雪(冷白蓝)/雾霾(灰) */
.weather-widget__main[data-sky='sunny'] {
  background: linear-gradient(135deg, #2f8fe0 0%, #56b7f0 55%, #8fd3f4 100%);
}
.weather-widget__main[data-sky='night'] {
  background: linear-gradient(135deg, #1e2a52 0%, #3a4a80 60%, #55639e 100%);
}
.weather-widget__main[data-sky='cloudy'] {
  background: linear-gradient(135deg, #5c7089 0%, #7c93ab 55%, #a8bccf 100%);
}
.weather-widget__main[data-sky='rain'] {
  background: linear-gradient(135deg, #3a5a6e 0%, #4f7d92 55%, #6fa0b3 100%);
}
.weather-widget__main[data-sky='snow'] {
  background: linear-gradient(135deg, #6d8bb0 0%, #9db6d4 55%, #cdddec 100%);
}
.weather-widget__main[data-sky='haze'] {
  background: linear-gradient(135deg, #7a7466 0%, #9a9384 55%, #bdb6a6 100%);
}

/* 三栏并入 hero：今日天气 / 今日气温 / 明日气温，保持原三栏布局，玻璃底板保证浅色图标可读 */
.weather-widget__tiles {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 2px;
}

.weather-widget__tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 10px 6px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  background: rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(3px);
  transition: transform 0.2s ease, background 0.2s ease, border-color 0.2s ease;
}

.weather-widget__tile:hover {
  transform: translateY(-2px);
  background: rgba(255, 255, 255, 0.22);
  border-color: rgba(255, 255, 255, 0.4);
}

.weather-widget__tile-header {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.86);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.22);
}

.weather-widget__tile-value {
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.28);
}

.weather-widget__tile-icons {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  min-height: 24px;
}

.weather-widget__tile-icon {
  width: 24px;
  height: 24px;
  filter: drop-shadow(0 1px 3px rgba(0, 0, 0, 0.3));
}

.weather-widget__tile-sep {
  color: rgba(255, 255, 255, 0.8);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.weather-widget__suggestion {
  margin: 12px 0 0;
  padding-top: 10px;
  border-top: 1px solid var(--border-color);
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-secondary);
}

/* 宽容器：主区温度更大 + 图标更大，建议文案才显示（见模板 isWide 判断） */
.weather-widget--wide .weather-widget__temp {
  font-size: 40px;
}

.weather-widget--wide .weather-widget__icon {
  width: 64px;
  height: 64px;
}

/* compact（移动端抽屉/窄侧栏）：更紧凑。hero 是独立圆角面板，只需收紧内边距。 */
.weather-widget--compact {
  margin-bottom: 12px;
  padding: 10px;
  border: 1px solid color-mix(in srgb, var(--border-color) 72%, transparent);
}

.weather-widget--compact .weather-widget__main {
  padding: 12px;
}

.weather-widget--compact .weather-widget__icon {
  width: 46px;
  height: 46px;
}

.weather-widget--compact .weather-widget__temp {
  font-size: 28px;
}

.weather-widget--compact .weather-widget__tiles {
  gap: 6px;
}

.weather-widget--compact .weather-widget__tile {
  padding: 8px 4px;
}

.weather-widget--compact .weather-widget__tile-icon {
  width: 20px;
  height: 20px;
}

.weather-widget--compact .weather-widget__tile-value {
  font-size: 12px;
}

.weather-widget__skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 6px 0;
}

.weather-widget__skeleton-row {
  height: 20px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--bg-soft) 25%, color-mix(in srgb, var(--bg-soft) 60%, transparent) 50%, var(--bg-soft) 75%);
  background-size: 200% 100%;
  animation: weather-shimmer 1.3s ease infinite;
}

.weather-widget__skeleton-row--short {
  width: 60%;
}

@keyframes weather-shimmer {
  to { background-position: -200% 0; }
}

.weather-widget__failed {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 0;
  font-size: 13px;
  color: var(--text-muted);
}

.weather-widget__failed button {
  padding: 4px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--theme-color);
  font-size: 12px;
  cursor: pointer;
}
</style>
