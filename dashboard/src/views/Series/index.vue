<template>
  <div class="admin-page">
    <a-card :bordered="false">
      <template #title>系列管理</template>
      <template #extra>
        <a-button type="primary" @click="openCreate">新建系列</a-button>
      </template>
      <a-table row-key="id" :columns="columns" :data-source="items" :loading="loading" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'visible'">
            <a-tag :color="record.visible ? 'green' : 'default'">{{ record.visible ? '显示' : '隐藏' }}</a-tag>
          </template>
          <template v-if="column.dataIndex === 'action'">
            <a-space>
              <a @click="openEdit(record)">编辑</a>
              <a-popconfirm title="确认删除该系列？文章会解除关联。" @confirm="remove(record)">
                <a class="danger-link">删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalOpen" :title="form.id ? '编辑系列' : '新建系列'" :confirm-loading="saving" @ok="submit">
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" maxlength="80" />
        </a-form-item>
        <a-form-item label="标识 slug">
          <a-input v-model:value="form.slug" placeholder="留空按名称生成" maxlength="80" />
        </a-form-item>
        <a-form-item label="简介">
          <a-textarea v-model:value="form.description" :rows="3" maxlength="500" />
        </a-form-item>
        <a-form-item label="封面 URL">
          <a-input v-model:value="form.coverImageUrl" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="form.displayOrder" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="前台显示">
          <a-switch v-model:checked="form.visible" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { createArticleSeries, deleteArticleSeries, getArticleSeriesList, updateArticleSeries } from '@/api/Series';

const loading = ref(false);
const saving = ref(false);
const items = ref([]);
const modalOpen = ref(false);
const form = reactive({
  id: null,
  name: '',
  slug: '',
  description: '',
  coverImageUrl: '',
  displayOrder: 0,
  visible: true
});
const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '名称', dataIndex: 'name' },
  { title: '标识', dataIndex: 'slug' },
  { title: '文章数', dataIndex: 'articleCount', width: 100 },
  { title: '排序', dataIndex: 'displayOrder', width: 90 },
  { title: '显示', dataIndex: 'visible', width: 90 },
  { title: '操作', dataIndex: 'action', width: 140 }
];

const load = async () => {
  loading.value = true;
  try {
    items.value = await getArticleSeriesList();
  } finally {
    loading.value = false;
  }
};
const resetForm = () => {
  form.id = null;
  form.name = '';
  form.slug = '';
  form.description = '';
  form.coverImageUrl = '';
  form.displayOrder = 0;
  form.visible = true;
};
const openCreate = () => {
  resetForm();
  modalOpen.value = true;
};
const openEdit = record => {
  form.id = record.id;
  form.name = record.name || '';
  form.slug = record.slug || '';
  form.description = record.description || '';
  form.coverImageUrl = record.coverImageUrl || '';
  form.displayOrder = record.displayOrder ?? 0;
  form.visible = record.visible !== false;
  modalOpen.value = true;
};
const submit = async () => {
  if (!form.name.trim()) {
    message.warning('请填写系列名称');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      name: form.name.trim(),
      slug: form.slug?.trim() || '',
      description: form.description?.trim() || '',
      coverImageUrl: form.coverImageUrl?.trim() || '',
      displayOrder: Number(form.displayOrder) || 0,
      visible: form.visible
    };
    if (form.id) await updateArticleSeries(form.id, payload);
    else await createArticleSeries(payload);
    message.success('已保存');
    modalOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
};
const remove = async record => {
  await deleteArticleSeries(record.id);
  message.success('已删除');
  await load();
};
onMounted(load);
</script>

<style scoped>
.admin-page { padding: 16px; }
.danger-link { color: #ff4d4f; }
</style>
