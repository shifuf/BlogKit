<template>
  <div class="banner-list-editor">
    <div v-for="(slide, index) in slides" :key="index" class="banner-row">
      <div class="banner-row__head">
        <span class="banner-row__idx">Banner {{ index + 1 }}</span>
        <a-space>
          <a-button size="small" :disabled="index === 0" @click="move(index, -1)">
            <template #icon><up-outlined /></template>
          </a-button>
          <a-button size="small" :disabled="index === slides.length - 1" @click="move(index, 1)">
            <template #icon><down-outlined /></template>
          </a-button>
          <a-button size="small" danger @click="remove(index)">
            <template #icon><delete-outlined /></template>
          </a-button>
        </a-space>
      </div>
      <div class="banner-row__body">
        <div class="banner-row__img">
          <img v-if="slide.imageUrl" :src="slide.imageUrl" alt="banner 预览" />
          <div v-else class="banner-row__img-empty">未设置图片</div>
          <div class="banner-row__img-actions">
            <a-upload :show-upload-list="false" :custom-request="opts => uploadFor(slide, opts)">
              <a-button size="small" block :loading="slide.__uploading">{{ slide.__uploading ? '上传中…' : '上传图片' }}</a-button>
            </a-upload>
            <a-button size="small" block @click="openPicker(index)">从图库选择</a-button>
          </div>
        </div>
        <div class="banner-row__fields">
          <a-input v-model:value="slide.imageUrl" placeholder="图片 URL 或 /uploads 路径" />
          <div class="banner-row__line">
            <span class="banner-row__label">点击链接</span>
            <a-select v-model:value="slide.linkType" style="flex: 1">
              <a-select-option value="none">不使用链接</a-select-option>
              <a-select-option value="auto">自动（首页第一篇文章）</a-select-option>
              <a-select-option value="article">指定文章 ID</a-select-option>
              <a-select-option value="custom">自定义链接</a-select-option>
            </a-select>
          </div>
          <a-input v-if="slide.linkType === 'article'" v-model:value="slide.articleId" placeholder="文章 ID" />
          <a-input v-if="slide.linkType === 'custom'" v-model:value="slide.linkUrl" placeholder="完整链接，如 /about 或 https://..." />
          <div class="banner-row__line">
            <span class="banner-row__label">签名</span>
            <a-switch v-model:checked="slide.signEnabled" checked-children="开" un-checked-children="关" />
          </div>
          <a-textarea
            v-if="slide.signEnabled"
            v-model:value="slide.signTitle"
            :rows="2"
            :placeholder="slide.linkType === 'article' ? '签名标题（留空则使用文章标题）' : '签名标题（支持换行）'"
            maxlength="120"
          />
          <a-input
            v-if="slide.signEnabled"
            v-model:value="slide.signSubtitle"
            placeholder="签名副标题（可选）"
            maxlength="120"
          />
        </div>
      </div>
    </div>
    <a-button type="dashed" block class="banner-add" @click="add">
      <template #icon><plus-outlined /></template>
      添加 Banner
    </a-button>
    <AttachmentPicker
      v-model:open="pickerOpen"
      :multiple="false"
      :allow-crop="true"
      crop-preset="16:9"
      crop-title="裁剪 Banner"
      @pick="applyPicked"
    />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { PlusOutlined, DeleteOutlined, UpOutlined, DownOutlined } from '@ant-design/icons-vue';
import { uploadImageWithCrop } from '@/utils/imageCrop';
import AttachmentPicker from '@/components/AttachmentPicker.vue';

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  }
});
const emit = defineEmits(['update:modelValue']);

const slides = ref([]);

const parse = raw => {
  if (!raw) return [];
  try {
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    return arr.map(item => ({
      imageUrl: item.imageUrl || '',
      // 历史 auto 保留；新默认 none（可不跳转）
      linkType: item.linkType || 'none',
      linkUrl: item.linkUrl || '',
      articleId: item.articleId ? String(item.articleId) : '',
      signEnabled: item.signEnabled === true || item.signEnabled === 'true',
      signTitle: item.signTitle || '',
      signSubtitle: item.signSubtitle || '',
      __uploading: false
    }));
  } catch {
    return [];
  }
};
const serialize = list => JSON.stringify((list || []).map(s => ({
  imageUrl: s.imageUrl || '',
  linkType: s.linkType || 'none',
  linkUrl: s.linkUrl || '',
  articleId: s.articleId || '',
  signEnabled: !!s.signEnabled,
  signTitle: s.signTitle || '',
  signSubtitle: s.signSubtitle || ''
})));

slides.value = parse(props.modelValue);

watch(slides, () => emit('update:modelValue', serialize(slides.value)), { deep: true });
watch(() => props.modelValue, val => {
  if (val !== serialize(slides.value)) {
    slides.value = parse(val);
  }
});

const add = () => {
  slides.value.push({
    imageUrl: '',
    linkType: 'none',
    linkUrl: '',
    articleId: '',
    signEnabled: false,
    signTitle: '',
    signSubtitle: '',
    __uploading: false
  });
};
const remove = index => {
  slides.value.splice(index, 1);
};
const move = (index, offset) => {
  const target = index + offset;
  if (target < 0 || target >= slides.value.length) return;
  const list = slides.value;
  [list[index], list[target]] = [list[target], list[index]];
};
const pickerOpen = ref(false);
const pickerIndex = ref(-1);
const openPicker = index => {
  pickerIndex.value = index;
  pickerOpen.value = true;
};
const applyPicked = urls => {
  const url = (urls || []).filter(Boolean)[0];
  if (!url || pickerIndex.value < 0 || !slides.value[pickerIndex.value]) return;
  slides.value[pickerIndex.value].imageUrl = url;
  message.success('已选择图片');
};
const uploadFor = async (slide, { file, onSuccess, onError }) => {
  slide.__uploading = true;
  try {
    const result = await uploadImageWithCrop(file, { defaultPreset: '16:9', title: '裁剪 Banner' });
    if (!result) {
      onError?.(new Error('cancelled'));
      return;
    }
    slide.imageUrl = result.url;
    message.success('已上传');
    onSuccess?.(result);
  } catch (error) {
    message.error('上传失败');
    onError?.(error);
  } finally {
    slide.__uploading = false;
  }
};
</script>

<style lang="scss" scoped>
.banner-list-editor {
  display: grid;
  gap: 12px;
}
.banner-row {
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  padding: 12px;
  background: #fafafa;
}
.banner-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.banner-row__idx {
  font-weight: 600;
  color: #1f2937;
}
.banner-row__body {
  display: flex;
  gap: 12px;
}
.banner-row__img {
  flex: 0 0 180px;
  display: grid;
  gap: 8px;
}
.banner-row__img img {
  width: 180px;
  height: 100px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #fff;
}
.banner-row__img-empty {
  width: 180px;
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  border: 1px dashed #d9d9d9;
  color: #bfbfbf;
  font-size: 13px;
  background: #fff;
}
.banner-row__img-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.banner-row__fields {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 10px;
  align-content: start;
}
.banner-row__line {
  display: flex;
  align-items: center;
  gap: 12px;
}
.banner-row__label {
  flex: 0 0 64px;
  color: #64748b;
  font-size: 13px;
}
.banner-add {
  margin-top: 4px;
}
@media (max-width: 640px) {
  .banner-row__body {
    flex-direction: column;
  }
  .banner-row__img,
  .banner-row__img img,
  .banner-row__img-empty {
    width: 100%;
  }
}
</style>
