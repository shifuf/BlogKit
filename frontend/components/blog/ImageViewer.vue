<script setup lang="ts">
import { FullScreen, RefreshLeft, RefreshRight, ZoomIn, ZoomOut } from '@element-plus/icons-vue';
const urls = ref<string[]>([]);
const initialIndex = ref(0);
const activeIndex = ref(0);
const viewerKey = ref(0);

type PreviewDetail = {
  urls?: string[];
  urlList?: string[];
  index?: number;
};

const normalizeUrl = (value?: string | null) => String(value || '').trim();

const openViewer = (list: string[], index = 0) => {
  const next = list.map(normalizeUrl).filter(Boolean);
  if (!next.length) return;
  const safeIndex = Math.min(Math.max(index, 0), next.length - 1);
  urls.value = next;
  initialIndex.value = safeIndex;
  activeIndex.value = safeIndex;
  viewerKey.value += 1;
};

const closeViewer = () => {
  urls.value = [];
};

const imageSrc = (image: HTMLImageElement) => normalizeUrl(image.currentSrc || image.src);

const imagesIn = (root: Element) => Array.from(root.querySelectorAll<HTMLImageElement>('img'))
  .map(imageSrc)
  .filter(Boolean);

const handlePreviewEvent = (event: Event) => {
  const detail = (event as CustomEvent<PreviewDetail>).detail || {};
  openViewer(detail.urls || detail.urlList || [], detail.index || 0);
};

const previewRootSelector = [
  '.article-md-preview',
  '.blog-md-preview',
  '.moment__grid',
  '.aero-comment__content',
  '.comment-editor__image-previews',
  '.bd-block--gallery'
].join(',');

const handleDocumentClick = (event: MouseEvent) => {
  const target = event.target;
  if (!(target instanceof Element)) return;
  if (target.closest('.el-image-viewer__wrapper')) return;

  const more = target.closest('.bd-gallery-more');
  if (more) {
    const gallery = more.closest('.bd-block--gallery');
    if (!gallery) return;
    const list = imagesIn(gallery);
    if (!list.length) return;
    event.preventDefault();
    event.stopPropagation();
    openViewer(list, Math.min(8, list.length - 1));
    return;
  }

  const image = target.closest('img');
  if (!(image instanceof HTMLImageElement)) return;
  const root = image.closest(previewRootSelector);
  if (!root) return;
  if (image.closest('a[href]') && !image.closest('.bd-block--gallery')) return;

  const list = imagesIn(root);
  const current = imageSrc(image);
  const index = Math.max(0, list.indexOf(current));
  event.preventDefault();
  event.stopPropagation();
  openViewer(list.length ? list : [current], index);
};

onMounted(() => {
  window.addEventListener('bd:image-preview', handlePreviewEvent as EventListener);
  document.addEventListener('click', handleDocumentClick, true);
});

onUnmounted(() => {
  window.removeEventListener('bd:image-preview', handlePreviewEvent as EventListener);
  document.removeEventListener('click', handleDocumentClick, true);
});
</script>

<template>
  <ClientOnly>
    <ElImageViewer
      v-if="urls.length"
      :key="viewerKey"
      :url-list="urls"
      :initial-index="initialIndex"
      hide-on-click-modal
      teleported
      @switch="activeIndex = $event"
      @close="closeViewer"
    >
      <template #toolbar="{ actions, reset, activeIndex: current }">
        <span class="bd-image-viewer__count">第 {{ current + 1 }} 张 / 共 {{ urls.length }} 张</span>
        <i class="el-image-viewer__actions__divider"></i>
        <ElIcon @click="actions('zoomOut')"><ZoomOut /></ElIcon>
        <ElIcon @click="actions('zoomIn')"><ZoomIn /></ElIcon>
        <i class="el-image-viewer__actions__divider"></i>
        <ElIcon @click="reset()"><FullScreen /></ElIcon>
        <i class="el-image-viewer__actions__divider"></i>
        <ElIcon @click="actions('anticlockwise')"><RefreshLeft /></ElIcon>
        <ElIcon @click="actions('clockwise')"><RefreshRight /></ElIcon>
      </template>
    </ElImageViewer>
  </ClientOnly>
</template>

<style scoped>
/* 张数计数器与缩放/全屏/旋转同级显示在底部操作栏最前面 */
.bd-image-viewer__count {
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
  font-size: 14px;
  line-height: 1;
  color: #fff;
}
</style>
