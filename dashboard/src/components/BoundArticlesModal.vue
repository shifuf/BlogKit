<template>
  <a-modal
    :open="open"
    :title="modalTitle"
    width="760px"
    :footer="null"
    @cancel="handleClose"
  >
    <a-table
      row-key="id"
      :columns="columns"
      :data-source="articleList"
      :loading="loading"
      :pagination="pagination"
      size="middle"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'title'">
          <a @click="openArticle(record.id)">{{ record.title }}</a>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="statusMap[record.status]?.color">
            {{ statusMap[record.status]?.label || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a @click="editArticle(record.id)">编辑</a>
        </template>
      </template>
      <template #emptyText>
        <a-empty :description="`该${typeLabel}下暂无文章`" />
      </template>
    </a-table>
  </a-modal>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { getArticleList } from '@/api/Article';
import { formatDateTime } from '@/utils/date';
const props = defineProps({
  open: { type: Boolean, default: false },
  // 'category' | 'tag'
  type: { type: String, default: 'category' },
  // { id, name }
  taxonomy: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['update:open']);
const router = useRouter();
const typeLabel = computed(() => (props.type === 'tag' ? '标签' : '分类'));
const modalTitle = computed(() => `「${props.taxonomy?.name || ''}」绑定的文章`);
const statusMap = {
  PUBLISHED: { label: '已发布', color: 'green' },
  DRAFT: { label: '草稿', color: 'default' },
  PENDING_REVIEW: { label: '待审核', color: 'orange' }
};
const columns = [
  { title: 'ID', dataIndex: 'id', width: 70, align: 'center' },
  { title: '标题', dataIndex: 'title', ellipsis: true },
  { title: '状态', dataIndex: 'status', width: 90, align: 'center' },
  { title: '更新时间', dataIndex: 'updatedAt', width: 170, customRender: ({ text }) => formatDateTime(text) },
  { title: '操作', dataIndex: 'action', width: 80, align: 'center' }
];
const articleList = ref([]);
const loading = ref(false);
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: total => `共 ${total} 篇`
});
const loadArticles = async () => {
  if (!props.taxonomy?.id) return;
  loading.value = true;
  try {
    const page = await getArticleList({
      page: pagination.current - 1,
      size: pagination.pageSize,
      categoryId: props.type === 'category' ? props.taxonomy.id : undefined,
      tagId: props.type === 'tag' ? props.taxonomy.id : undefined,
      deleted: false
    });
    articleList.value = page.items || [];
    pagination.total = page.totalItems || 0;
  } finally {
    loading.value = false;
  }
};
const handleTableChange = pag => {
  pagination.current = pag.current;
  pagination.pageSize = pag.pageSize;
  loadArticles();
};
const handleClose = () => emit('update:open', false);
const openArticle = id => window.open(`/article/${id}`, '_blank');
const editArticle = id => {
  emit('update:open', false);
  router.push(`/articles/edit?id=${id}`);
};
watch(
  () => props.open,
  value => {
    if (value) {
      pagination.current = 1;
      loadArticles();
    }
  }
);
</script>
