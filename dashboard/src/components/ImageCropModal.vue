<template>
  <a-modal
    :open="visible"
    :title="modalTitle"
    :mask-closable="false"
    :keyboard="false"
    :width="modalWidth"
    :z-index="1200"
    :centered="true"
    :wrap-class-name="'image-crop-modal-wrap'"
    :destroy-on-close="true"
    @cancel="handleCancel"
    @after-open-change="onAfterOpenChange"
  >
    <div class="crop-toolbar">
      <a-segmented v-model:value="preset" :options="presetOptions" size="small" @change="applyPreset" />
    </div>
    <div ref="stageRef" class="crop-stage">
      <!-- loading=eager 避免 Chromium 把弹窗内图片延迟加载，导致 load 永不触发 -->
      <img
        v-show="objectUrl && !loadError"
        ref="imgRef"
        class="crop-image"
        alt="裁剪预览"
        loading="eager"
        decoding="async"
        fetchpriority="high"
        draggable="false"
      />
      <div v-if="loading && !loadError" class="crop-loading">
        <a-spin />
        <span>{{ loadingText }}</span>
      </div>
      <div v-if="loadError" class="crop-error">{{ loadErrorText }}</div>
    </div>
    <template #footer>
      <a-space wrap>
        <a-button @click="handleCancel">取消</a-button>
        <a-button :disabled="!canSkip" @click="handleSkip">使用原图</a-button>
        <a-button type="primary" :loading="confirming" :disabled="loadError || !cropperReady" @click="handleConfirm">
          确认裁剪
        </a-button>
      </a-space>
    </template>
  </a-modal>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import Cropper from 'cropperjs';
import 'cropperjs/dist/cropper.css';
import { registerCropHost } from '@/utils/imageCrop';

const PRESET_RATIO = {
  free: NaN,
  '1:1': 1,
  '16:9': 16 / 9,
  '4:3': 4 / 3
};

const presetOptions = [
  { label: '自由', value: 'free' },
  { label: '1:1', value: '1:1' },
  { label: '16:9', value: '16:9' },
  { label: '4:3', value: '4:3' }
];

const visible = ref(false);
const confirming = ref(false);
const cropperReady = ref(false);
const loading = ref(false);
const loadingText = ref('图片加载中…');
const loadError = ref(false);
const loadErrorText = ref('图片加载失败，请使用原图或重新选择');
const preset = ref('free');
const objectUrl = ref('');
const imgRef = ref(null);
const stageRef = ref(null);
const sourceFile = ref(null);
const index = ref(0);
const total = ref(1);
const customTitle = ref('');
const returnCropParams = ref(false);
const modalWidth = ref(Math.min(720, typeof window !== 'undefined' ? window.innerWidth - 24 : 720));

const canSkip = computed(() => returnCropParams.value || Boolean(sourceFile.value));
const modalTitle = computed(() => {
  const base = customTitle.value || '裁剪图片';
  if (total.value > 1) return `${base} (${index.value + 1}/${total.value})`;
  return base;
});

let cropper = null;
let resolver = null;
let ownedObjectUrl = false;
let sessionId = 0;
let bootTimer = null;
let readyFallbackTimer = null;
let loadWatchdog = null;

const clearTimers = () => {
  if (bootTimer) {
    clearTimeout(bootTimer);
    bootTimer = null;
  }
  if (readyFallbackTimer) {
    clearTimeout(readyFallbackTimer);
    readyFallbackTimer = null;
  }
  if (loadWatchdog) {
    clearTimeout(loadWatchdog);
    loadWatchdog = null;
  }
};

const destroyCropper = () => {
  clearTimers();
  if (cropper) {
    try {
      cropper.destroy();
    } catch {
      // ignore
    }
    cropper = null;
  }
  cropperReady.value = false;
};

const revokeUrl = () => {
  if (ownedObjectUrl && objectUrl.value) {
    try {
      URL.revokeObjectURL(objectUrl.value);
    } catch {
      // ignore
    }
  }
  objectUrl.value = '';
  ownedObjectUrl = false;
};

const markReady = current => {
  if (current !== sessionId) return;
  cropperReady.value = true;
  loading.value = false;
};

const createCropper = current => {
  if (current !== sessionId || cropper) return;
  const el = imgRef.value;
  const stage = stageRef.value;
  if (!el || !stage || !el.naturalWidth || !el.naturalHeight) return;
  if (stage.clientWidth < 40 || stage.clientHeight < 40) return;

  const src = el.currentSrc || el.src || '';
  const isBlob = /^blob:|^data:/i.test(src);

  try {
    loadingText.value = '初始化裁剪框…';
    cropper = new Cropper(el, {
      viewMode: 1,
      dragMode: 'move',
      autoCropArea: 0.9,
      responsive: true,
      restore: false,
      // 只读坐标时无需 canvas 导出；远程图不强制 CORS，避免 CORS 失败整图空白
      checkCrossOrigin: false,
      checkOrientation: false,
      background: true,
      modal: true,
      guides: true,
      center: true,
      highlight: true,
      cropBoxMovable: true,
      cropBoxResizable: true,
      toggleDragModeOnDblclick: true,
      movable: true,
      zoomable: true,
      zoomOnTouch: true,
      zoomOnWheel: true,
      wheelZoomRatio: 0.1,
      minContainerWidth: 100,
      minContainerHeight: 100,
      aspectRatio: Number.isNaN(PRESET_RATIO[preset.value]) ? NaN : PRESET_RATIO[preset.value],
      ready() {
        markReady(current);
        try {
          this.resize?.();
        } catch {
          // ignore
        }
      }
    });

    // ready 偶发不触发时，只要实例在就放行
    readyFallbackTimer = setTimeout(() => {
      if (current === sessionId && cropper && !cropperReady.value) {
        markReady(current);
        try {
          cropper.resize?.();
        } catch {
          // ignore
        }
      }
    }, isBlob ? 300 : 600);
  } catch (error) {
    console.error('[ImageCropModal] init failed', error);
    loadError.value = true;
    loadErrorText.value = '裁剪组件初始化失败，请使用原图或重新选择';
    loading.value = false;
  }
};

/**
 * 轮询：img 解码完成 + stage 有尺寸 → 建 cropper。
 * 不依赖单一 load 事件（Chromium lazy intervention / 缓存 complete 时 load 可能已错过）。
 */
const bootCropper = (current, attempt = 0) => {
  if (current !== sessionId || !visible.value || loadError.value) return;
  if (cropper) return;

  const el = imgRef.value;
  const stage = stageRef.value;
  if (!objectUrl.value) {
    if (attempt < 80) {
      bootTimer = setTimeout(() => bootCropper(current, attempt + 1), 50);
    }
    return;
  }

  if (!el || !stage) {
    if (attempt < 80) {
      bootTimer = setTimeout(() => bootCropper(current, attempt + 1), 50);
    }
    return;
  }

  // 确保 src 已挂上（不用 :src 绑定，避免 Vue 与 cropper 抢 src）
  if (el.getAttribute('src') !== objectUrl.value) {
    el.removeAttribute('crossorigin');
    el.src = objectUrl.value;
  }

  if (el.complete && el.naturalWidth > 0 && el.naturalHeight > 0
    && stage.clientWidth >= 40 && stage.clientHeight >= 40) {
    createCropper(current);
    return;
  }

  if (attempt >= 100) {
    // 约 5s：仍无尺寸 → 失败
    if (!el.naturalWidth) {
      loadError.value = true;
      loadErrorText.value = '图片加载超时，请使用原图或重新选择';
      loading.value = false;
    } else {
      // 有图但 stage 还没尺寸，再硬试一次
      createCropper(current);
      if (!cropper) {
        loadError.value = true;
        loadErrorText.value = '裁剪区域初始化失败，请关闭后重试';
        loading.value = false;
      }
    }
    return;
  }

  bootTimer = setTimeout(() => bootCropper(current, attempt + 1), 50);
};

const bindImageHandlers = current => {
  const el = imgRef.value;
  if (!el) return;
  el.onload = () => {
    if (current !== sessionId) return;
    bootCropper(current, 0);
  };
  el.onerror = () => {
    if (current !== sessionId) return;
    loadError.value = true;
    loadErrorText.value = '图片加载失败，请使用原图或重新选择';
    loading.value = false;
    destroyCropper();
  };
};

const applyPreset = value => {
  if (!cropper) return;
  const ratio = PRESET_RATIO[value];
  cropper.setAspectRatio(Number.isNaN(ratio) ? NaN : ratio);
};

const close = result => {
  const resolve = resolver;
  resolver = null;
  sessionId += 1;
  destroyCropper();
  const el = imgRef.value;
  if (el) {
    el.onload = null;
    el.onerror = null;
    el.removeAttribute('src');
  }
  revokeUrl();
  sourceFile.value = null;
  returnCropParams.value = false;
  visible.value = false;
  confirming.value = false;
  loading.value = false;
  loadError.value = false;
  loadingText.value = '图片加载中…';
  resolve?.(result);
};

const handleCancel = () => close(null);

const handleSkip = () => {
  if (returnCropParams.value) {
    close({ skipped: true, crop: null });
    return;
  }
  if (!sourceFile.value) {
    close(null);
    return;
  }
  close(sourceFile.value);
};

const handleConfirm = () => {
  if (!cropper || loadError.value) {
    close(null);
    return;
  }
  confirming.value = true;
  try {
    if (returnCropParams.value) {
      const data = cropper.getData(true);
      const imageData = cropper.getImageData?.() || {};
      const naturalW = Math.max(1, Math.round(imageData.naturalWidth || imgRef.value?.naturalWidth || 1));
      const naturalH = Math.max(1, Math.round(imageData.naturalHeight || imgRef.value?.naturalHeight || 1));
      let x = Math.max(0, Math.round(data.x || 0));
      let y = Math.max(0, Math.round(data.y || 0));
      let width = Math.max(1, Math.round(data.width || 0));
      let height = Math.max(1, Math.round(data.height || 0));
      if (x >= naturalW) x = Math.max(0, naturalW - 1);
      if (y >= naturalH) y = Math.max(0, naturalH - 1);
      width = Math.min(width, naturalW - x);
      height = Math.min(height, naturalH - y);
      confirming.value = false;
      close({ skipped: false, crop: { x, y, width: Math.max(1, width), height: Math.max(1, height) } });
      return;
    }

    if (!sourceFile.value) {
      confirming.value = false;
      close(null);
      return;
    }
    const mime = sourceFile.value.type && sourceFile.value.type !== 'image/gif'
      ? sourceFile.value.type
      : 'image/jpeg';
    const canvas = cropper.getCroppedCanvas({
      imageSmoothingEnabled: true,
      imageSmoothingQuality: 'high',
      maxWidth: 4096,
      maxHeight: 4096
    });
    if (!canvas) {
      confirming.value = false;
      close(null);
      return;
    }
    canvas.toBlob(blob => {
      if (!blob) {
        confirming.value = false;
        close(null);
        return;
      }
      const rawName = sourceFile.value.name || `crop-${Date.now()}.jpg`;
      const name = /\.(jpe?g|png|webp)$/i.test(rawName)
        ? rawName
        : rawName.replace(/\.[^.]+$/, '') + (mime === 'image/png' ? '.png' : '.jpg');
      close(new File([blob], name, { type: mime, lastModified: Date.now() }));
    }, mime, 0.92);
  } catch {
    confirming.value = false;
    close(null);
  }
};

const startWithUrl = (current, url, { file = null, own = false } = {}) => {
  if (current !== sessionId) return;
  if (file) sourceFile.value = file;
  objectUrl.value = url;
  ownedObjectUrl = own;
  loading.value = true;
  loadingText.value = '图片加载中…';
  loadError.value = false;
  cropperReady.value = false;

  nextTick(() => {
    if (current !== sessionId) return;
    bindImageHandlers(current);
    const el = imgRef.value;
    if (el) {
      el.removeAttribute('crossorigin');
      // 先清空再赋值，强制触发加载（含缓存命中）
      el.removeAttribute('src');
      // eslint-disable-next-line no-unused-expressions
      el.offsetWidth;
      el.src = url;
    }
    bootCropper(current, 0);
    // 总超时
    loadWatchdog = setTimeout(() => {
      if (current !== sessionId || cropperReady.value || loadError.value) return;
      if (imgRef.value?.naturalWidth) {
        createCropper(current);
        if (cropper) markReady(current);
        return;
      }
      loadError.value = true;
      loadErrorText.value = '图片加载超时，请使用原图或重新选择';
      loading.value = false;
    }, 12000);
  });
};

const open = options => {
  if (resolver) close(null);
  return new Promise(resolve => {
    resolver = resolve;
    sessionId += 1;
    const current = sessionId;

    destroyCropper();
    revokeUrl();

    sourceFile.value = options.file || null;
    index.value = options.index || 0;
    total.value = options.total || 1;
    customTitle.value = options.title || '';
    returnCropParams.value = Boolean(options.returnCropParams);
    preset.value = options.defaultPreset && PRESET_RATIO[options.defaultPreset] !== undefined
      ? options.defaultPreset
      : 'free';
    loadError.value = false;
    cropperReady.value = false;
    loading.value = true;
    loadingText.value = options.loadPreview ? '正在获取图片…' : '图片加载中…';
    modalWidth.value = Math.min(720, typeof window !== 'undefined' ? window.innerWidth - 24 : 720);
    visible.value = true;

    const run = () => {
      if (typeof options.loadPreview === 'function') {
        Promise.resolve()
          .then(() => options.loadPreview())
          .then(preview => {
            if (current !== sessionId) {
              if (preview?.ownObjectUrl && preview.objectUrl) {
                try { URL.revokeObjectURL(preview.objectUrl); } catch { /* ignore */ }
              }
              return;
            }
            if (!preview?.objectUrl) {
              loadError.value = true;
              loadErrorText.value = '图片加载失败，请使用原图或重新选择';
              loading.value = false;
              return;
            }
            startWithUrl(current, preview.objectUrl, {
              file: preview.file || options.file || null,
              own: Boolean(preview.ownObjectUrl)
            });
          })
          .catch(error => {
            console.warn('[ImageCropModal] loadPreview failed', error);
            if (current !== sessionId) return;
            loadError.value = true;
            loadErrorText.value = error?.message || '图片加载失败，请使用原图或重新选择';
            loading.value = false;
          });
        return;
      }

      if (options.objectUrl) {
        startWithUrl(current, options.objectUrl, {
          file: options.file || null,
          own: Boolean(options.ownObjectUrl)
        });
        return;
      }

      if (options.file) {
        startWithUrl(current, URL.createObjectURL(options.file), {
          file: options.file,
          own: true
        });
        return;
      }

      loadError.value = true;
      loadErrorText.value = '没有可裁剪的图片';
      loading.value = false;
    };

    // 等 modal 真正挂载后再塞图
    nextTick(run);
  });
};

const onAfterOpenChange = openState => {
  if (!openState) {
    destroyCropper();
    return;
  }
  // 打开动画结束后再 boot 一次，避免 stage 尺寸为 0
  nextTick(() => bootCropper(sessionId, 0));
};

// 始终注册最新 open：HMR 热更后 onMounted 不一定重跑，用 watchEffect/立刻注册保证宿主不过期
registerCropHost({ open });
onMounted(() => {
  registerCropHost({ open });
});

onBeforeUnmount(() => {
  if (resolver) close(null);
  registerCropHost(null);
  destroyCropper();
  revokeUrl();
});
</script>

<style lang="scss">
.image-crop-modal-wrap {
  .crop-toolbar {
    margin-bottom: 12px;
    overflow-x: auto;
  }

  .crop-stage {
    position: relative;
    width: 100%;
    height: min(56vh, 460px);
    min-height: 260px;
    background: #141414;
    border-radius: 8px;
    overflow: hidden;
    touch-action: none;
  }

  .crop-image {
    display: block;
    max-width: 100%;
  }

  .crop-stage .cropper-container {
    width: 100% !important;
    height: 100% !important;
  }

  .crop-loading,
  .crop-error {
    position: absolute;
    inset: 0;
    z-index: 5;
    display: flex;
    flex-direction: column;
    gap: 10px;
    align-items: center;
    justify-content: center;
    color: #fff;
    background: rgba(0, 0, 0, 0.55);
    font-size: 14px;
    padding: 16px;
    text-align: center;
    pointer-events: none;
  }

  .ant-modal-body {
    padding-top: 12px;
    padding-bottom: 8px;
  }

  @media (max-width: 640px) {
    .crop-stage {
      height: min(50vh, 360px);
      min-height: 220px;
    }

    .ant-modal {
      max-width: calc(100vw - 16px);
      margin: 8px auto;
    }

    .ant-modal-footer .ant-space {
      width: 100%;
      justify-content: flex-end;
    }
  }
}
</style>
