<template>
  <div class="admin-page">
    <a-card :bordered="false">
      <template #title>搜索统计</template>
      <template #extra>
        <a-space wrap>
          <a-input-search
            v-model:value="keywordInput"
            allow-clear
            placeholder="搜索关键词"
            style="width: 260px"
            @search="search"
          />
          <a-button @click="reset">重置</a-button>
        </a-space>
      </template>

      <a-table
        row-key="id"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'keyword'">
            <strong>{{ record.keyword }}</strong>
          </template>
          <template v-if="column.dataIndex === 'noResultRate'">
            <a-progress
              :percent="noResultRate(record)"
              size="small"
              :stroke-color="noResultRate(record) > 50 ? '#ff4d4f' : '#1677ff'"
            />
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { getSearchStats } from '@/api/SearchStats';
import { formatDateTime } from '@/utils/date';

const loading = ref(false);
const rows = ref([]);
const keywordInput = ref('');
const keyword = ref('');
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: total => `共 ${total} 条`
});
const columns = [
  { title: '关键词', dataIndex: 'keyword' },
  { title: '搜索次数', dataIndex: 'totalCount', width: 120, sorter: false },
  { title: '无结果次数', dataIndex: 'noResultCount', width: 130 },
  { title: '无结果率', dataIndex: 'noResultRate', width: 180 },
  { title: '最近命中', dataIndex: 'lastResultCount', width: 110 },
  { title: '最近搜索', dataIndex: 'lastSearchedAt', width: 180, customRender: ({ text }) => formatDateTime(text) }
];

const noResultRate = record => {
  const total = Number(record.totalCount || 0);
  if (!total) return 0;
  return Math.round(Number(record.noResultCount || 0) * 100 / total);
};

const loadRows = async () => {
  loading.value = true;
  try {
    const page = await getSearchStats({
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      keyword: keyword.value
    });
    rows.value = page.items || [];
    pagination.total = page.totalItems || 0;
  } finally {
    loading.value = false;
  }
};

const search = value => {
  keywordInput.value = (value ?? keywordInput.value).trim();
  keyword.value = keywordInput.value;
  pagination.current = 1;
  loadRows();
};

const reset = () => {
  keywordInput.value = '';
  keyword.value = '';
  pagination.current = 1;
  loadRows();
};

const handleTableChange = page => {
  pagination.current = page.current;
  pagination.pageSize = page.pageSize;
  loadRows();
};

onMounted(loadRows);
</script>

<style scoped lang="scss">
.admin-page {
  padding: 16px;
}
</style>
