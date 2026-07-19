<template>
  <a-modal
    :open="open"
    :title="imagesOnly ? '从图库选择' : '从附件库选择'"
    :width="pickerWidth"
    :confirm-loading="confirming"
    :ok-text="okText"
    cancel-text="取消"
    :ok-button-props="{ disabled: !selected.length }"
    destroy-on-close
    @ok="handleConfirm"
    @cancel="handleCancel"
  >
    <div class="picker-toolbar">
      <a-input-search
        v-model:value="keyword"
        allow-clear
        :placeholder="imagesOnly ? '搜索文件名 / URL' : '搜索附件'"
        style="max-width: 320px"
        @search="search"
      />
      <a-space wrap>
        <a-checkbox v-if="imagesOnly && allowCrop" v-model:checked="cropAfterPick">
          选择后裁剪
        </a-checkbox>
        <span class="muted">已选 {{ selected.length }} {{ imagesOnly ? '张' : '项' }}</span>
      </a-space>
    </div>
    <a-spin :spinning="loading">
      <div v-if="items.length" class="picker-grid">
        <button
          v-for="item in items"
          :key="item.id"
          type="button"
          class="picker-card"
          :class="{ selected: selectedIds.has(item.id) }"
          @click="toggle(item)"
        >
          <img v-if="isImage(item)" :src="assetUrl(item.url)" :alt="item.fileName" loading="eager" decoding="async" />
          <div v-else class="picker-card__file">
            <span class="picker-card__ext">{{ fileExt(item) }}</span>
          </div>
          <span class="picker-card__name" :title="item.fileName">{{ item.fileName }}</span>
        </button>
      </div>
      <a-empty v-else :description="imagesOnly ? '暂无图片附件' : '暂无附件'" />
    </a-spin>
    <div class="picker-pagination">
      <a-pagination
        v-model:current="page"
        v-model:page-size="pageSize"
        size="small"
        :total="total"
        :show-size-changer="true"
        :page-size-options="['12', '24', '48']"
        @change="load"
      />
    </div>
  </a-modal>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { getAttachmentList } from '@/api/Attachment';
import { cropLibraryImageUrl } from '@/utils/imageCrop';

const props = defineProps({
  open: { type: Boolean, default: false },
  multiple: { type: Boolean, default: true },
  imagesOnly: { type: Boolean, default: true },
  allowCrop: { type: Boolean, default: true },
  defaultCrop: { type: Boolean, default: true },
  cropPreset: { type: String, default: 'free' },
  cropTitle: { type: String, default: '裁剪图片' }
});
const emit = defineEmits(['update:open', 'pick']);

const loading = ref(false);
const confirming = ref(false);
const items = ref([]);
const selected = ref([]);
const keyword = ref('');
const page = ref(1);
const pageSize = ref(12);
const total = ref(0);
const cropAfterPick = ref(true);

const selectedIds = computed(() => new Set(selected.value.map(item => item.id)));
const pickerWidth = computed(() => (typeof window !== 'undefined' && window.innerWidth < 720 ? window.innerWidth - 16 : 860));
const okText = computed(() => {
  if (props.imagesOnly && props.allowCrop && cropAfterPick.value) return '裁剪并使用';
  return '插入选中';
});

const assetUrl = url => {
  if (!url) return '';
  if (/^https?:\/\//i.test(url)) return url;
  if (import.meta.env.DEV && url.startsWith('/uploads')) {
    return `http://localhost:8080${url}`;
  }
  return url;
};

const isImage = record => String(record.contentType || '').toLowerCase().startsWith('image/')
  || /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(record.fileName || record.url || '');

const fileExt = record => {
  const name = record.fileName || record.url || '';
  const ext = String(name).split('.').pop();
  return ext && ext !== name ? ext.toUpperCase().slice(0, 6) : 'FILE';
};

const load = async () => {
  loading.value = true;
  try {
    const result = await getAttachmentList({
      pageNum: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim()
    });
    const rows = result?.items || [];
    items.value = props.imagesOnly ? rows.filter(isImage) : rows;
    total.value = result?.totalItems || 0;
  } catch (error) {
    message.error(error?.response?.data?.message || error?.message || '加载图库失败');
  } finally {
    loading.value = false;
  }
};

const search = () => {
  page.value = 1;
  load();
};

const toggle = item => {
  const index = selected.value.findIndex(row => row.id === item.id);
  if (index >= 0) {
    selected.value.splice(index, 1);
    return;
  }
  if (!props.multiple) {
    selected.value = [item];
    return;
  }
  selected.value.push(item);
};

const reset = () => {
  selected.value = [];
  keyword.value = '';
  page.value = 1;
  cropAfterPick.value = props.defaultCrop;
};

const handleCancel = () => {
  emit('update:open', false);
};

const handleConfirm = async () => {
  if (!selected.value.length) {
    message.warning(props.imagesOnly ? '请先选择图片' : '请先选择附件');
    return;
  }
  confirming.value = true;
  try {
    if (props.imagesOnly && props.allowCrop && cropAfterPick.value) {
      const finalUrls = [];
      for (let i = 0; i < selected.value.length; i += 1) {
        const item = selected.value[i];
        // UFM 直链：只拼 ?x&y&w&h 裁切参数，不重传；其它图床仍走裁剪上传
        // eslint-disable-next-line no-await-in-loop
        const url = await cropLibraryImageUrl(assetUrl(item.url), {
          fileName: item.fileName,
          defaultPreset: props.cropPreset,
          title: props.cropTitle
        });
        if (!url) return;
        finalUrls.push(url);
      }
      emit('pick', finalUrls);
      emit('update:open', false);
      return;
    }
    emit('pick', selected.value.map(item => item.url).filter(Boolean));
    emit('update:open', false);
  } catch (error) {
    message.error(error?.message || '处理选中附件失败');
  } finally {
    confirming.value = false;
  }
};

watch(() => props.open, value => {
  if (value) {
    reset();
    load();
  }
});
</script>

<style lang="scss" scoped>
.picker-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.picker-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  min-height: 220px;
  max-height: 460px;
  overflow: auto;
  padding: 2px;
}

.picker-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  background: #fafafa;
  cursor: pointer;
  text-align: left;
  transition: border-color .15s, box-shadow .15s;

  &:hover {
    border-color: #91caff;
  }

  &.selected {
    border-color: #1677ff;
    box-shadow: 0 0 0 2px rgba(22, 119, 255, .15);
  }

  img {
    width: 100%;
    height: 96px;
    object-fit: cover;
    border-radius: 6px;
    background: #eee;
  }
}

.picker-card__file {
  height: 96px;
  border-radius: 6px;
  background: linear-gradient(135deg, #eef2ff, #e0e7ff);
  display: flex;
  align-items: center;
  justify-content: center;
}

.picker-card__ext {
  font-size: 14px;
  font-weight: 700;
  color: #4338ca;
}

.picker-card__name {
  font-size: 12px;
  color: #595959;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker-pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.muted {
  color: #8c8c8c;
  font-size: 12px;
}

@media (max-width: 640px) {
  .picker-grid {
    grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
    max-height: 50vh;
  }
}
</style>
