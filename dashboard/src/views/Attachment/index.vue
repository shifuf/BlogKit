<template>
  <div class="admin-page">
    <a-card :bordered="false">
      <template #title>附件管理</template>
      <template #extra>
        <a-space wrap>
          <a-upload :show-upload-list="false" :custom-request="handleUpload" :disabled="uploading">
            <a-button type="primary" :loading="uploading">{{ uploading ? '上传中…' : '上传附件' }}</a-button>
          </a-upload>
          <a-upload :show-upload-list="false" :custom-request="handleImageUpload" accept="image/*" :disabled="uploading">
            <a-button :loading="uploading">上传图片（可裁剪）</a-button>
          </a-upload>
          <a-popconfirm
            v-if="selectedRowKeys.length"
            :title="`确认删除选中的 ${selectedRowKeys.length} 个附件？`"
            @confirm="handleBatchDelete"
          >
            <a-button danger :loading="batchDeleting">批量删除（{{ selectedRowKeys.length }}）</a-button>
          </a-popconfirm>
          <a-input-search
            v-model:value="keywordInput"
            allow-clear
            placeholder="搜索文件名、URL、上传人"
            style="width: 280px"
            @search="searchAttachments"
          />
          <a-button @click="resetFilters">重置</a-button>
        </a-space>
      </template>

      <a-table
        row-key="id"
        :columns="columns"
        :data-source="attachments"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{ selectedRowKeys, onChange: keys => (selectedRowKeys = keys) }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'preview'">
            <a-image
              v-if="isImage(record)"
              class="asset-preview"
              :src="assetUrl(record.url)"
              :width="72"
              :height="48"
              :preview="{ src: assetUrl(record.url) }"
            />
            <a-tag v-else class="asset-file-tag">{{ fileTypeLabel(record) }}</a-tag>
          </template>
          <template v-if="column.dataIndex === 'fileName'">
            <strong>{{ record.fileName }}</strong>
            <div class="muted">{{ record.storedFilename }}</div>
          </template>
          <template v-if="column.dataIndex === 'url'">
            <a-typography-paragraph class="url-cell" :ellipsis="{ rows: 2 }">
              {{ record.url }}
            </a-typography-paragraph>
          </template>
          <template v-if="column.dataIndex === 'sizeBytes'">
            {{ formatFileSize(record.sizeBytes) }}
          </template>
          <template v-if="column.dataIndex === 'uploaderName'">
            {{ record.uploaderName || '-' }}
          </template>
          <template v-if="column.dataIndex === 'action'">
            <a-space>
              <a @click="copyUrl(record)">复制</a>
              <a @click="openAsset(record)">打开</a>
              <a-popconfirm title="确认删除该附件？文件会移入统一文件管理回收站。" @confirm="removeAttachment(record)">
                <a class="danger-link">删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { deleteAttachment, getAttachmentList, batchDeleteAttachments } from '@/api/Attachment';
import { uploadAnyWithOptionalCrop, uploadImageWithCrop } from '@/utils/imageCrop';
import { formatDateTime } from '@/utils/date';
const loading = ref(false);
const uploading = ref(false);
const batchDeleting = ref(false);
const selectedRowKeys = ref([]);
const attachments = ref([]);
const keywordInput = ref('');
const keyword = ref('');
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: total => `共 ${total} 条`
});
const columns = [{
  title: 'ID',
  dataIndex: 'id',
  width: 80
}, {
  title: '预览',
  dataIndex: 'preview',
  width: 100
}, {
  title: '文件名',
  dataIndex: 'fileName',
  ellipsis: true
}, {
  title: 'URL',
  dataIndex: 'url',
  width: 260
}, {
  title: '类型',
  dataIndex: 'contentType',
  width: 130
}, {
  title: '大小',
  dataIndex: 'sizeBytes',
  width: 100,
  align: 'right'
}, {
  title: '上传人',
  dataIndex: 'uploaderName',
  width: 120
}, {
  title: '上传时间',
  dataIndex: 'createdAt',
  width: 180,
  customRender: ({
    text
  }) => formatDateTime(text)
}, {
  title: '操作',
  dataIndex: 'action',
  width: 150,
  fixed: 'right'
}];
const loadAttachments = async () => {
  loading.value = true;
  try {
    const page = await getAttachmentList({
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      keyword: keyword.value
    });
    attachments.value = page.items;
    pagination.total = page.totalItems;
  } finally {
    loading.value = false;
  }
};
// 通用附件上传（图片可裁剪，其它类型直传）。
const handleUpload = async ({ file, onSuccess, onError }) => {
  uploading.value = true;
  try {
    const result = await uploadAnyWithOptionalCrop(file);
    if (!result) {
      onError?.(new Error('cancelled'));
      return;
    }
    message.success('已上传');
    onSuccess?.(result);
    pagination.current = 1;
    await loadAttachments();
  } catch (error) {
    message.error(error?.response?.data?.message || error?.message || '上传失败');
    onError?.(error);
  } finally {
    uploading.value = false;
  }
};
// 仅图片 + 强制裁剪入口。
const handleImageUpload = async ({ file, onSuccess, onError }) => {
  uploading.value = true;
  try {
    const result = await uploadImageWithCrop(file);
    if (!result) {
      onError?.(new Error('cancelled'));
      return;
    }
    message.success('已上传');
    onSuccess?.(result);
    pagination.current = 1;
    await loadAttachments();
  } catch (error) {
    message.error(error?.response?.data?.message || error?.message || '上传失败');
    onError?.(error);
  } finally {
    uploading.value = false;
  }
};
const searchAttachments = value => {
  keywordInput.value = (value ?? keywordInput.value).trim();
  keyword.value = keywordInput.value;
  pagination.current = 1;
  loadAttachments();
};
const resetFilters = () => {
  keywordInput.value = '';
  keyword.value = '';
  pagination.current = 1;
  loadAttachments();
};
const handleTableChange = page => {
  pagination.current = page.current;
  pagination.pageSize = page.pageSize;
  loadAttachments();
};
const assetUrl = url => {
  const normalized = url;
  if (!normalized) return '';
  if (/^https?:\/\//i.test(normalized)) return normalized;
  if (import.meta.env.DEV && normalized.startsWith('/uploads')) {
    return `http://localhost:8080${normalized}`;
  }
  return normalized;
};
const formatFileSize = value => {
  const size = Number(value || 0);
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(2)} MB`;
};
const isImage = record => String(record.contentType || '').toLowerCase().startsWith('image/');
const fileTypeLabel = record => {
  const extension = String(record.fileName || '').split('.').pop();
  return extension && extension !== record.fileName ? extension.toUpperCase() : 'FILE';
};
const copyUrl = async record => {
  await navigator.clipboard.writeText(record.url);
  message.success('URL 已复制');
};
const openAsset = record => {
  window.open(assetUrl(record.url), '_blank');
};
const removeAttachment = async record => {
  await deleteAttachment(record.id);
  message.success('附件已删除');
  selectedRowKeys.value = selectedRowKeys.value.filter(id => id !== record.id);
  await loadAttachments();
};
const handleBatchDelete = async () => {
  if (!selectedRowKeys.value.length) return;
  batchDeleting.value = true;
  try {
    const result = await batchDeleteAttachments(selectedRowKeys.value);
    message.success(`已删除 ${result?.deleted ?? selectedRowKeys.value.length} 个附件`);
    selectedRowKeys.value = [];
    await loadAttachments();
  } catch (error) {
    message.error(error?.response?.data?.message || error?.message || '批量删除失败');
  } finally {
    batchDeleting.value = false;
  }
};
onMounted(loadAttachments);
</script>

<style lang="scss" scoped>
.admin-page {
  padding: 16px;
}

.asset-preview {
  object-fit: cover;
  border-radius: 6px;
  background: #f5f5f5;
}

.asset-file-tag {
  min-width: 64px;
  text-align: center;
}

.url-cell {
  margin-bottom: 0;
  word-break: break-all;
}

.muted {
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
}

.danger-link {
  color: #ff4d4f;
}
</style>
