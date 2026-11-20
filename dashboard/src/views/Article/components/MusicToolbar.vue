<template>
  <NormalToolbar title="插入音乐" @onClick="open">
    <template #trigger><CustomerServiceOutlined /></template>
  </NormalToolbar>

  <Teleport to="body">
    <div v-if="visible" class="music-modal-mask" @click.self="close">
      <div class="music-modal" role="dialog" aria-modal="true" aria-label="插入音乐">
        <div class="music-modal-header">
          <div class="header-copy">
            <div class="header-kicker">
              <span class="header-kicker-dot"></span>
              Music Insert
            </div>
            <h3>插入音乐</h3>
            <p>搜索曲目、试听预览，一键插入播放器（文章 / 动态通用）</p>
          </div>
          <button type="button" class="close-btn" aria-label="关闭" @click="close">
            <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
              <line x1="6" y1="6" x2="18" y2="18"></line>
              <line x1="18" y1="6" x2="6" y2="18"></line>
            </svg>
          </button>
        </div>

        <div class="music-modal-search">
          <div class="search-field">
            <svg class="search-icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="11" cy="11" r="7"></circle>
              <line x1="16.5" y1="16.5" x2="21" y2="21"></line>
            </svg>
            <input
              ref="searchInputRef"
              v-model="keyword"
              placeholder="搜索歌曲名、歌手..."
              @keydown.enter="doSearch"
            />
            <button type="button" class="search-btn" :disabled="loading || !keyword.trim()" @click="doSearch">
              <span v-if="loading" class="search-spinner"></span>
              <span v-else>搜索</span>
            </button>
          </div>
          <div class="music-help">选择封面、歌名和歌手即可插入，播放失败时自动切换备用源。</div>
        </div>

        <div class="music-modal-body">
          <div v-if="loading" class="music-hint">
            <div class="hint-loader"><span></span><span></span><span></span></div>
            <div class="hint-title">正在搜索</div>
            <div class="hint-desc">正在搜索可用音乐源…</div>
          </div>
          <div v-else-if="list.length === 0 && searched" class="music-hint">
            <div class="hint-icon">♪</div>
            <div class="hint-title">未找到结果</div>
            <div class="hint-desc">换个关键词再试一次</div>
          </div>
          <div v-else-if="list.length === 0" class="music-hint">
            <div class="hint-icon">♫</div>
            <div class="hint-title">搜索你喜欢的音乐</div>
            <div class="hint-desc">输入歌名或歌手，支持回车快速搜索</div>
          </div>

          <div
            v-for="(item, i) in list"
            :key="item.rid || item.id || i"
            class="music-item"
            :class="{ active: selectedIndex === i, playing: previewIndex === i }"
            @click="selectItem(i)"
          >
            <div class="music-index">{{ String(i + 1).padStart(2, '0') }}</div>
            <div class="music-cover-wrap">
              <img v-if="item.cover" :src="item.cover" class="music-cover" alt="" />
              <div v-else class="music-cover music-cover--empty">♪</div>
              <div v-if="previewIndex === i" class="cover-equalizer" aria-hidden="true">
                <span></span><span></span><span></span>
              </div>
            </div>
            <div class="music-info">
              <div class="music-name">{{ item.name }}</div>
              <div class="music-artist">
                <span class="artist-text">{{ item.artist || '未知歌手' }}</span>
                <span class="source-badge">自动切换</span>
              </div>
            </div>
            <button
              type="button"
              class="preview-btn"
              :title="previewIndex === i ? '停止' : '试听'"
              :aria-label="previewIndex === i ? '停止试听' : '试听'"
              :disabled="previewLoading && previewIndex !== i"
              @click.stop="togglePreview(i)"
            >
              <svg v-if="previewIndex === i" class="btn-icon" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
                <rect x="7" y="7" width="10" height="10" rx="1.5" fill="currentColor"></rect>
              </svg>
              <svg v-else class="btn-icon" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
                <polygon points="8 5 19 12 8 19 8 5" fill="currentColor"></polygon>
              </svg>
            </button>
          </div>
        </div>

        <div v-show="previewIndex >= 0" ref="previewContainer" class="music-preview"></div>

        <div v-if="selected" class="music-modal-footer">
          <div class="selected-card">
            <img v-if="selected.cover" :src="selected.cover" class="selected-cover" alt="" />
            <div v-else class="selected-cover selected-cover--empty">M</div>
            <div class="selected-info">
              <div class="selected-label">已选择</div>
              <div class="selected-title">
                <strong>{{ selected.name }}</strong>
                <span>{{ selected.artist || '未知歌手' }}</span>
              </div>
              <div v-if="previewError" class="selected-warning">{{ previewError }}</div>
            </div>
          </div>
          <div class="insert-actions">
            <button type="button" class="insert-btn player-btn" :disabled="!selected?.rid" @click="insert">
              插入播放器
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from 'vue';
import { message } from 'ant-design-vue';
import { CustomerServiceOutlined } from '@ant-design/icons-vue';
import { NormalToolbar } from 'md-editor-v3';
import { resolveMusic, searchMusic } from '@/api/Music';

const emit = defineEmits(['insert']);

const visible = ref(false);
const keyword = ref('');
const loading = ref(false);
const searched = ref(false);
const list = ref([]);
const selectedIndex = ref(-1);
const previewIndex = ref(-1);
const previewLoading = ref(false);
const previewError = ref('');
const searchInputRef = ref(null);
const previewContainer = ref(null);

let previewPlayer = null;
const trackCache = new Map();

const selected = computed(() => (selectedIndex.value >= 0 ? list.value[selectedIndex.value] : null));

const escapeAttr = value => String(value || '')
  .replace(/&/g, '&amp;')
  .replace(/"/g, '&quot;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;');

const open = async () => {
  visible.value = true;
  await nextTick();
  searchInputRef.value?.focus();
};

const stopPreview = () => {
  previewIndex.value = -1;
  previewLoading.value = false;
  if (previewPlayer) {
    previewPlayer.destroy?.();
    previewPlayer = null;
  }
};

const close = () => {
  stopPreview();
  visible.value = false;
};

const doSearch = async () => {
  const q = keyword.value.trim();
  if (!q) return message.warning('请输入歌曲名或歌手名');
  loading.value = true;
  searched.value = true;
  selectedIndex.value = -1;
  previewError.value = '';
  trackCache.clear();
  stopPreview();
  try {
    list.value = (await searchMusic(q)) || [];
  } catch (error) {
    list.value = [];
    message.error(error?.message || '搜索失败');
  } finally {
    loading.value = false;
  }
};

const selectItem = i => {
  selectedIndex.value = i;
  previewError.value = '';
};

const resolveTrack = async item => {
  if (!item?.rid) throw new Error('缺少音乐资源 ID');
  if (trackCache.has(item.rid)) return trackCache.get(item.rid);
  const data = await resolveMusic(item.rid);
  if (!data?.url) throw new Error('该音乐暂时没有可播放地址');
  const track = {
    name: item.name || '未命名音乐',
    artist: item.artist || '未知歌手',
    cover: data.cover || item.cover || '',
    sourceType: 'combined',
    url: data.url,
    lyric: data.lyric || '',
    candidates: Array.isArray(data.candidates) ? data.candidates : []
  };
  trackCache.set(item.rid, track);
  return track;
};

const togglePreview = async i => {
  if (previewIndex.value === i) {
    stopPreview();
    return;
  }
  const item = list.value[i];
  if (!item) return;
  selectedIndex.value = i;
  previewError.value = '';
  previewLoading.value = true;
  previewIndex.value = i;
  try {
    const track = await resolveTrack(item);
    if (previewPlayer) {
      previewPlayer.destroy?.();
      previewPlayer = null;
    }
    await nextTick();
    const create = window.createPlainMusicPlayer;
    if (typeof create !== 'function') {
      throw new Error('播放器脚本未加载，请刷新后台页面');
    }
    if (!previewContainer.value) throw new Error('预览容器不可用');
    previewPlayer = create(previewContainer.value, track, { autoplay: true, mutex: true });
  } catch (error) {
    stopPreview();
    previewError.value = error?.message || '试听失败';
    message.error(previewError.value);
  } finally {
    previewLoading.value = false;
  }
};

const insert = () => {
  const item = selected.value;
  if (!item?.rid) return;
  const attrs = [
    ['rid', item.rid],
    ['name', item.name || ''],
    ['artist', item.artist || ''],
    ['cover', item.cover || '']
  ]
    .filter(([, value]) => value)
    .map(([name, value]) => `${name}="${escapeAttr(value)}"`)
    .join(' ');
  emit('insert', `<music-player ${attrs}></music-player>`);
  close();
  message.success('音乐组件已插入');
};

onBeforeUnmount(() => stopPreview());
</script>

<style scoped>
.music-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, .45);
}
.music-modal {
  width: 760px;
  max-width: 100%;
  max-height: min(90vh, 820px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: #1f2937;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, .18);
}
.music-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 18px 20px 14px;
  border-bottom: 1px solid #f0f0f0;
}
.header-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  color: #8c8c8c;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .08em;
  text-transform: uppercase;
}
.header-kicker-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #1677ff;
}
.music-modal-header h3 {
  margin: 0;
  color: #111827;
  font-size: 18px;
  font-weight: 700;
}
.music-modal-header p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
}
.close-btn {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fafafa;
  color: #6b7280;
  cursor: pointer;
}
.close-btn:hover {
  color: #111827;
  border-color: #d9d9d9;
  background: #f5f5f5;
}
.music-modal-search {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 20px 16px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafafa;
}
.music-help { color: #8c8c8c; font-size: 12px; }
.search-field {
  display: flex;
  align-items: center;
  min-height: 42px;
  padding: 0 6px 0 12px;
  border: 1px solid #d9d9d9;
  border-radius: 10px;
  background: #fff;
}
.search-field:focus-within {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, .12);
}
.search-icon { margin-right: 8px; color: #8c8c8c; }
.search-field input {
  flex: 1;
  min-width: 0;
  height: 40px;
  border: 0;
  outline: none;
  background: transparent;
  color: #1f2937;
  font-size: 14px;
}
.search-field input::placeholder { color: #bfbfbf; }
.search-btn {
  height: 32px;
  margin-left: 8px;
  padding: 0 14px;
  border: 0;
  border-radius: 8px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  background: #1677ff;
  cursor: pointer;
}
.search-btn:disabled {
  color: #fff;
  background: #91caff;
  cursor: not-allowed;
}
.search-spinner {
  display: block;
  width: 14px;
  height: 14px;
  margin: 0 auto;
  border: 2px solid rgba(255,255,255,.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: music-spin .7s linear infinite;
}
.music-modal-body {
  flex: 1;
  overflow-y: auto;
  min-height: 260px;
  max-height: 360px;
  padding: 10px 12px;
  background: #fff;
}
.music-hint {
  display: grid;
  place-items: center;
  gap: 6px;
  padding: 52px 16px;
  text-align: center;
}
.hint-icon {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  border: 1px solid #f0f0f0;
  background: #fafafa;
  color: #8c8c8c;
  font-size: 20px;
}
.hint-title { color: #1f2937; font-size: 15px; font-weight: 700; }
.hint-desc { color: #8c8c8c; font-size: 13px; }
.hint-loader {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 22px;
  margin-bottom: 8px;
}
.hint-loader span {
  width: 3px;
  height: 8px;
  border-radius: 999px;
  background: #1677ff;
  animation: music-eq .9s ease-in-out infinite;
}
.hint-loader span:nth-child(2) { animation-delay: .15s; height: 14px; }
.hint-loader span:nth-child(3) { animation-delay: .3s; height: 10px; }
.music-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  cursor: pointer;
}
.music-item:hover { background: #f5f5f5; }
.music-item.active { background: #e6f4ff; border-color: #91caff; }
.music-item.playing { background: #e6f4ff; border-color: #69b1ff; }
.music-index {
  width: 24px;
  color: #8c8c8c;
  font-size: 12px;
  font-weight: 700;
  text-align: center;
}
.music-cover-wrap { position: relative; width: 48px; height: 48px; flex-shrink: 0; }
.music-cover {
  width: 48px;
  height: 48px;
  object-fit: cover;
  background: #f5f5f5;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
}
.music-cover--empty {
  display: grid;
  place-items: center;
  color: #8c8c8c;
  font-size: 16px;
}
.cover-equalizer {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 3px;
  padding-bottom: 12px;
  border-radius: 10px;
  background: rgba(0,0,0,.35);
}
.cover-equalizer span {
  width: 3px;
  height: 8px;
  border-radius: 999px;
  background: #fff;
  animation: music-eq .85s ease-in-out infinite;
}
.cover-equalizer span:nth-child(2) { height: 12px; animation-delay: .12s; }
.cover-equalizer span:nth-child(3) { height: 9px; animation-delay: .24s; }
.music-info { flex: 1; min-width: 0; }
.music-name {
  overflow: hidden;
  color: #111827;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.music-artist {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}
.artist-text {
  overflow: hidden;
  color: #8c8c8c;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.source-badge {
  flex-shrink: 0;
  padding: 1px 7px;
  border-radius: 999px;
  border: 1px solid #f0f0f0;
  background: #fafafa;
  color: #8c8c8c;
  font-size: 10px;
  font-weight: 700;
}
.preview-btn {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: 1px solid #e5e7eb;
  border-radius: 50%;
  background: #fff;
  color: #4b5563;
  cursor: pointer;
}
.preview-btn:hover:not(:disabled) {
  color: #1677ff;
  border-color: #91caff;
  background: #e6f4ff;
}
.music-item.playing .preview-btn {
  color: #fff;
  background: #1677ff;
  border-color: #1677ff;
}
.music-preview {
  padding: 12px;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}
.music-preview :deep(.plain-player) {
  width: 100%;
  max-width: 420px;
  margin: 0 auto;
}
.music-modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px 16px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
}
.selected-card {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
}
.selected-cover {
  width: 42px;
  height: 42px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid #f0f0f0;
  background: #f5f5f5;
}
.selected-cover--empty {
  display: grid;
  place-items: center;
  color: #8c8c8c;
  font-weight: 700;
}
.selected-label {
  color: #8c8c8c;
  font-size: 11px;
  font-weight: 700;
}
.selected-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 2px;
  min-width: 0;
}
.selected-title strong {
  overflow: hidden;
  color: #111827;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.selected-title span {
  overflow: hidden;
  color: #8c8c8c;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.selected-warning { margin-top: 4px; color: #ff4d4f; font-size: 12px; }
.insert-actions { display: flex; gap: 8px; }
.insert-btn {
  height: 36px;
  padding: 0 16px;
  border: 0;
  border-radius: 8px;
  background: #1677ff;
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}
.insert-btn:disabled {
  background: #91caff;
  cursor: not-allowed;
}
@keyframes music-spin { to { transform: rotate(360deg); } }
@keyframes music-eq {
  0%, 100% { transform: scaleY(.45); opacity: .55; }
  50% { transform: scaleY(1); opacity: 1; }
}
@media (max-width: 640px) {
  .music-modal-mask { padding: 0; align-items: flex-end; }
  .music-modal {
    width: 100%;
    max-height: 92vh;
    border-radius: 16px 16px 0 0;
  }
  .music-modal-footer { flex-direction: column; align-items: stretch; }
  .insert-btn { width: 100%; }
}
</style>

