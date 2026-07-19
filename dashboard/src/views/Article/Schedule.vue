<template>
  <div class="admin-page">
    <a-card :bordered="false">
      <template #title>定时发布队列</template>
      <template #extra>
        <a-space>
          <a-radio-group v-model:value="viewMode" button-style="solid" size="small">
            <a-radio-button value="list">列表</a-radio-button>
            <a-radio-button value="calendar">日历</a-radio-button>
          </a-radio-group>
          <a-button @click="loadArticles">刷新</a-button>
          <a-button @click="openDraftPicker">从草稿选择</a-button>
          <a-button type="primary" @click="router.push('/articles/edit')">新建定时文章</a-button>
        </a-space>
      </template>

      <div v-if="viewMode === 'calendar'" class="schedule-calendar">
        <div class="schedule-calendar__toolbar">
          <a-button size="small" @click="shiftMonth(-1)">上月</a-button>
          <strong>{{ calendarLabel }}</strong>
          <a-button size="small" @click="shiftMonth(1)">下月</a-button>
        </div>
        <div class="schedule-calendar__grid">
          <div v-for="name in weekNames" :key="name" class="schedule-calendar__weekday">{{ name }}</div>
          <div
            v-for="cell in calendarCells"
            :key="cell.key"
            class="schedule-calendar__cell"
            :class="{ muted: !cell.inMonth, today: cell.isToday }"
          >
            <div class="schedule-calendar__day">{{ cell.day }}</div>
            <button
              v-for="item in cell.items"
              :key="item.id"
              type="button"
              class="schedule-calendar__item"
              :title="item.title"
              @click="router.push(`/articles/edit?id=${item.id}`)"
            >
              {{ formatTimeOnly(item.scheduledPublishAt) }} {{ item.title }}
            </button>
          </div>
        </div>
      </div>

      <a-table
        v-else
        row-key="id"
        :columns="columns"
        :data-source="articles"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'title'">
            <strong>{{ record.title }}</strong>
            <div class="muted">{{ record.slug }}</div>
          </template>
          <template v-if="column.dataIndex === 'scheduledPublishAt'">
            <a-tag color="blue">{{ formatDateTime(record.scheduledPublishAt) }}</a-tag>
          </template>
          <template v-if="column.dataIndex === 'action'">
            <a-space>
              <a @click="previewArticle(record)">预览</a>
              <a @click="router.push(`/articles/edit?id=${record.id}`)">编辑</a>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="draftPickerOpen"
      title="从草稿选择并定时发布"
      width="720px"
      :confirm-loading="scheduling"
      ok-text="加入定时队列"
      @ok="confirmSchedule"
    >
      <a-form layout="vertical">
        <a-form-item label="选择草稿文章" required>
          <a-select
            v-model:value="selectedDraftId"
            show-search
            placeholder="搜索并选择一篇草稿文章"
            :filter-option="false"
            :loading="draftLoading"
            :options="draftOptions"
            style="width: 100%"
            @search="onDraftSearch"
            @dropdown-visible-change="open => open && loadDrafts()"
          />
          <div class="muted">仅列出状态为「草稿」的文章。</div>
        </a-form-item>
        <a-form-item label="定时发布时间" required>
          <a-input v-model:value="scheduledPublishAt" type="datetime-local" style="width: 100%" />
          <div class="muted">到点后系统自动将其发布（每分钟检查一次）。时间须晚于当前。</div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { useRouter } from 'vue-router';
import { createArticlePreviewLink, getArticleList, updateArticleFlags } from '@/api/Article';
import { formatDateTime } from '@/utils/date';

const router = useRouter();
const loading = ref(false);
const articles = ref([]);
const viewMode = ref('list');
const calendarCursor = ref(new Date());
const weekNames = ['一', '二', '三', '四', '五', '六', '日'];
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: total => `共 ${total} 条`
});
const columns = [
  { title: 'ID', dataIndex: 'id', width: 90 },
  { title: '标题', dataIndex: 'title' },
  { title: '分类', dataIndex: 'categoryName', width: 140 },
  { title: '计划发布时间', dataIndex: 'scheduledPublishAt', width: 210 },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, customRender: ({ text }) => formatDateTime(text) },
  { title: '操作', dataIndex: 'action', width: 140, fixed: 'right' }
];

const calendarLabel = computed(() => {
  const d = calendarCursor.value;
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月`;
});
const formatTimeOnly = value => {
  if (!value) return '';
  return String(value).replace('T', ' ').slice(11, 16);
};
const shiftMonth = delta => {
  const d = new Date(calendarCursor.value);
  d.setMonth(d.getMonth() + delta);
  calendarCursor.value = d;
};
const calendarCells = computed(() => {
  const cursor = calendarCursor.value;
  const year = cursor.getFullYear();
  const month = cursor.getMonth();
  const first = new Date(year, month, 1);
  const startOffset = (first.getDay() + 6) % 7; // Monday first
  const start = new Date(year, month, 1 - startOffset);
  const today = new Date();
  const todayKey = `${today.getFullYear()}-${today.getMonth()}-${today.getDate()}`;
  const itemsByDay = {};
  for (const item of articles.value || []) {
    if (!item.scheduledPublishAt) continue;
    const d = new Date(item.scheduledPublishAt);
    if (Number.isNaN(d.getTime())) continue;
    const key = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
    (itemsByDay[key] ||= []).push(item);
  }
  const cells = [];
  for (let i = 0; i < 42; i += 1) {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    const key = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
    cells.push({
      key: `${key}-${i}`,
      day: d.getDate(),
      inMonth: d.getMonth() === month,
      isToday: key === todayKey,
      items: itemsByDay[key] || []
    });
  }
  return cells;
});

const loadArticles = async () => {
  loading.value = true;
  try {
    // 日历需要更全的数据，列表仍分页展示。
    const page = await getArticleList({
      page: viewMode.value === 'calendar' ? 0 : pagination.current - 1,
      size: viewMode.value === 'calendar' ? 100 : pagination.pageSize,
      status: 'SCHEDULED',
      deleted: false
    });
    articles.value = page.items || [];
    pagination.total = page.totalItems || 0;
  } finally {
    loading.value = false;
  }
};

const handleTableChange = page => {
  pagination.current = page.current;
  pagination.pageSize = page.pageSize;
  loadArticles();
};

const previewArticle = async record => {
  const preview = await createArticlePreviewLink(record.id);
  try {
    await navigator.clipboard?.writeText(`${window.location.origin}${preview.previewPath}`);
    message.success('预览链接已复制');
  } catch {
    message.success('预览链接已生成');
  }
  window.open(preview.previewPath, '_blank');
};

// ===== 从草稿选择并定时发布 =====
const draftPickerOpen = ref(false);
const draftLoading = ref(false);
const draftOptions = ref([]);
const selectedDraftId = ref(undefined);
const scheduledPublishAt = ref('');
const scheduling = ref(false);
let draftSearchTimer = null;

const openDraftPicker = () => {
  selectedDraftId.value = undefined;
  scheduledPublishAt.value = '';
  draftPickerOpen.value = true;
  loadDrafts();
};

const loadDrafts = async (keyword = '') => {
  draftLoading.value = true;
  try {
    const page = await getArticleList({ page: 0, size: 50, status: 'DRAFT', deleted: false, keyword });
    draftOptions.value = (page.items || []).map(item => ({
      value: item.id,
      label: `${item.title}${item.categoryName ? `（${item.categoryName}）` : ''}`
    }));
  } finally {
    draftLoading.value = false;
  }
};

const onDraftSearch = keyword => {
  if (draftSearchTimer) clearTimeout(draftSearchTimer);
  draftSearchTimer = setTimeout(() => loadDrafts(keyword?.trim() || ''), 300);
};

// 本地 datetime-local（无时区）补上本机时区偏移，转成带偏移的 ISO，避免后端按 UTC 解析导致差 8 小时。
const toOffsetDateTime = local => {
  if (!local) return '';
  const date = new Date(local);
  if (Number.isNaN(date.getTime())) return '';
  const tzOffsetMs = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - tzOffsetMs).toISOString().replace('Z', '+08:00');
};

const confirmSchedule = async () => {
  if (!selectedDraftId.value) {
    message.warning('请选择一篇草稿文章');
    return;
  }
  const scheduled = toOffsetDateTime(scheduledPublishAt.value);
  if (!scheduled) {
    message.warning('请设置定时发布时间');
    return;
  }
  if (new Date(scheduledPublishAt.value).getTime() <= Date.now()) {
    message.warning('定时发布时间必须晚于当前时间');
    return;
  }
  scheduling.value = true;
  try {
    await updateArticleFlags(selectedDraftId.value, { status: 'SCHEDULED', scheduledPublishAt: scheduled });
    message.success('已加入定时发布队列');
    draftPickerOpen.value = false;
    await loadArticles();
  } finally {
    scheduling.value = false;
  }
};

onMounted(loadArticles);
</script>

<style scoped lang="scss">
.admin-page {
  padding: 16px;
}

.muted {
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
}

.schedule-calendar {
  margin-bottom: 8px;
}
.schedule-calendar__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.schedule-calendar__grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
}
.schedule-calendar__weekday {
  text-align: center;
  font-size: 12px;
  color: #8c8c8c;
  padding: 4px 0;
}
.schedule-calendar__cell {
  min-height: 96px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 6px;
  background: #fafafa;
}
.schedule-calendar__cell.muted {
  opacity: .45;
}
.schedule-calendar__cell.today {
  border-color: #91caff;
  background: #e6f4ff;
}
.schedule-calendar__day {
  font-size: 12px;
  color: #595959;
  margin-bottom: 4px;
}
.schedule-calendar__item {
  display: block;
  width: 100%;
  text-align: left;
  border: 0;
  background: #1677ff;
  color: #fff;
  border-radius: 4px;
  font-size: 11px;
  line-height: 1.3;
  padding: 3px 4px;
  margin-bottom: 3px;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
