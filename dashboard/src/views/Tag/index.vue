<template>
  <div class="tag-container">
    <a-card :bordered="false">
      <div class="table-page-search-wrapper">
        <a-form layout="inline" @submit.prevent>
          <a-form-item label="标签">
            <a-input v-model:value="queryParams.keyword" placeholder="名称或 slug" allow-clear />
          </a-form-item>
          <a-form-item>
            <a-space>
              <a-button type="primary" @click="handleQuery">查询</a-button>
              <a-button @click="resetQuery">重置</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </div>

      <div class="table-operator">
        <a-button type="primary" @click="handleAdd">
          <template #icon><plus-outlined /></template>
          新增标签
        </a-button>
        <a-popconfirm
          v-if="selectedRowKeys.length > 0"
          title="确定要删除选中的标签吗？已被文章使用的标签不能删除。"
          ok-text="确定"
          cancel-text="取消"
          @confirm="handleBatchDelete"
        >
          <a-button danger>
            <template #icon><delete-outlined /></template>
            批量删除
          </a-button>
        </a-popconfirm>
      </div>

      <a-table
        :columns="columns"
        :data-source="tagList"
        :row-key="record => record.id"
        :pagination="pagination"
        :loading="loading"
        :scroll="{ x: 730 }"
        bordered
        @change="handleTableChange"
        :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'name'">
            <a-tag color="blue" class="tag-name">{{ record.name }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'articleCount'">
            <a-tag
              :color="record.articleCount > 0 ? 'blue' : 'default'"
              :style="record.articleCount > 0 ? 'cursor: pointer' : ''"
              @click="record.articleCount > 0 && viewArticles(record)"
            >{{ record.articleCount }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-space>
              <a @click="viewArticles(record)">查看文章</a>
              <a-divider type="vertical" />
              <a @click="handleEdit(record)">编辑</a>
              <a-divider type="vertical" />
              <a-popconfirm
                title="确定要删除此标签吗？已被文章使用的标签不能删除。"
                ok-text="确定"
                cancel-text="取消"
                @confirm="() => handleDelete(record)"
              >
                <a class="text-danger">删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
        <template #emptyText>
          <span>暂无标签</span>
        </template>
      </a-table>

      <tag-modal
        v-model:visible="modalVisible"
        :tag-info="currentTag"
        @success="getList"
      />

      <bound-articles-modal
        v-model:open="articlesModalVisible"
        type="tag"
        :taxonomy="currentArticlesTaxonomy"
      />
    </a-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { Grid, message } from 'ant-design-vue';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue';
import { getAdminTagList, deleteTag } from '@/api/Tag';
import TagModal from './components/TagModal.vue';
import BoundArticlesModal from '@/components/BoundArticlesModal.vue';
const queryParams = reactive({
  keyword: ''
});
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: total => `共 ${total} 条`
});
const screens = Grid.useBreakpoint();
const columns = computed(() => [{
  title: '标签名称',
  dataIndex: 'name',
  width: 180
}, {
  title: 'ID',
  dataIndex: 'id',
  width: 80,
  align: 'center'
}, {
  title: 'Slug',
  dataIndex: 'slug',
  width: 180,
  ellipsis: true
}, {
  title: '文章数',
  dataIndex: 'articleCount',
  width: 100,
  align: 'center'
}, {
  title: '操作',
  dataIndex: 'action',
  width: 150,
  align: 'center',
  fixed: screens.value.md ? 'right' : undefined
}]);
const tagList = ref([]);
const allTagList = ref([]);
const loading = ref(false);
const selectedRowKeys = ref([]);
const modalVisible = ref(false);
const currentTag = ref({});
const articlesModalVisible = ref(false);
const currentArticlesTaxonomy = ref({});
const viewArticles = record => {
  currentArticlesTaxonomy.value = { id: record.id, name: record.name };
  articlesModalVisible.value = true;
};
const normalizeList = data => Array.isArray(data) ? data : [];
const applyFilter = () => {
  const keyword = queryParams.keyword.trim().toLowerCase();
  const filtered = keyword ? allTagList.value.filter(item => (item.name || '').toLowerCase().includes(keyword) || (item.slug || '').toLowerCase().includes(keyword)) : allTagList.value.slice();
  tagList.value = filtered;
  pagination.total = filtered.length;
  pagination.current = 1;
};
const getList = async () => {
  loading.value = true;
  try {
    allTagList.value = normalizeList(await getAdminTagList());
    selectedRowKeys.value = [];
    applyFilter();
  } finally {
    loading.value = false;
  }
};
const handleTableChange = pag => {
  pagination.current = pag.current;
  pagination.pageSize = pag.pageSize;
};
const handleQuery = () => applyFilter();
const resetQuery = () => {
  queryParams.keyword = '';
  applyFilter();
};
const onSelectChange = keys => {
  selectedRowKeys.value = keys;
};
const handleAdd = () => {
  currentTag.value = {};
  modalVisible.value = true;
};
const handleEdit = record => {
  currentTag.value = {
    ...record
  };
  modalVisible.value = true;
};
const handleDelete = async record => {
  await deleteTag(record.id);
  message.success('删除成功');
  getList();
};
const handleBatchDelete = async () => {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请至少选择一条记录');
    return;
  }
  await deleteTag(selectedRowKeys.value);
  message.success('批量删除成功');
  getList();
};
onMounted(getList);
</script>

<style lang="scss" scoped>
.tag-container {
  padding: 16px;
  background-color: #f0f2f5;

  .table-page-search-wrapper {
    margin-bottom: 16px;
  }

  .table-operator {
    display: flex;
    gap: 8px;
    margin-bottom: 16px;
  }

  .text-danger {
    color: #ff4d4f;
  }

  .tag-name {
    max-width: 100%;
    white-space: normal;
    overflow-wrap: anywhere;
  }
}
</style>
