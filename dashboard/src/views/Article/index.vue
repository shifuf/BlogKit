<template>
  <div class="admin-page">
    <a-card :bordered="false">
      <template #title>文章管理</template>
      <template #extra>
        <a-space wrap>
          <a-input-search
            v-model:value="keywordInput"
            allow-clear
            placeholder="搜索标题、摘要、标签、作者"
            style="width: 260px"
            @search="searchArticles"
          />
          <a-select v-model:value="statusFilter" allow-clear placeholder="状态筛选" style="width: 140px" @change="reload">
            <a-select-option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
          </a-select>
          <a-select v-model:value="visibilityFilter" allow-clear placeholder="可见性" style="width: 130px" @change="reload">
            <a-select-option value="PUBLIC">公开</a-select-option>
            <a-select-option value="LOGIN">登录可见</a-select-option>
            <a-select-option value="PASSWORD">密码可见</a-select-option>
            <a-select-option value="PRIVATE">指定用户</a-select-option>
            <a-select-option value="UNLISTED">隐藏(直链)</a-select-option>
          </a-select>
          <a-select v-model:value="stickyFilter" allow-clear placeholder="置顶" style="width: 100px" @change="reload">
            <a-select-option :value="true">仅置顶</a-select-option>
            <a-select-option :value="false">非置顶</a-select-option>
          </a-select>
          <a-radio-group v-model:value="listMode" button-style="solid" @change="reload">
            <a-radio-button value="active">正常文章</a-radio-button>
            <a-radio-button value="deleted">回收站</a-radio-button>
          </a-radio-group>
          <a-button @click="resetFilters">重置</a-button>
          <a-dropdown :disabled="!selectedRowKeys.length || listMode !== 'active'">
            <a-button :disabled="!selectedRowKeys.length || listMode !== 'active'">批量改状态/可见性</a-button>
            <template #overlay>
              <a-menu @click="onBatchFlagsMenu">
                <a-menu-item key="status:PUBLISHED">批量发布</a-menu-item>
                <a-menu-item key="status:DRAFT">批量改草稿</a-menu-item>
                <a-menu-item key="visibility:PUBLIC">批量设公开</a-menu-item>
                <a-menu-item key="visibility:LOGIN">批量设登录可见</a-menu-item>
                <a-menu-item key="visibility:UNLISTED">批量设隐藏</a-menu-item>
                <a-menu-item key="sticky:true">批量置顶</a-menu-item>
                <a-menu-item key="sticky:false">取消置顶</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <a-popconfirm :title="batchActionTitle" :disabled="!selectedRowKeys.length" @confirm="removeSelectedArticles">
            <a-button danger :disabled="!selectedRowKeys.length">{{ batchActionText }}</a-button>
          </a-popconfirm>
          <a-button :disabled="!selectedRowKeys.length" :loading="exporting" @click="exportSelected">导出 Markdown</a-button>
          <a-button :disabled="!selectedRowKeys.length" :loading="scanning" @click="scanSelectedLinks">扫描坏链</a-button>
          <a-button v-if="listMode === 'active'" @click="openStickyOrder">置顶排序</a-button>
          <a-button v-if="listMode === 'active'" type="primary" @click="createArticle">新建文章</a-button>
        </a-space>
      </template>

      <a-table
        row-key="id"
        :columns="columns"
        :data-source="articles"
        :pagination="pagination"
        :loading="loading"
        :row-selection="rowSelection"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'title'">
            <a-space>
              <a-tag v-if="record.sticky" color="red">置顶</a-tag>
              <strong>{{ record.title }}</strong>
            </a-space>
            <div class="muted">{{ record.slug }}</div>
            <div v-if="record.status === 'SCHEDULED' && record.scheduledPublishAt" class="muted">
              定时 {{ formatDateTime(record.scheduledPublishAt) }}
            </div>
          </template>
          <template v-if="column.dataIndex === 'sticky'">
            <a-switch
              :checked="record.sticky === true"
              size="small"
              :disabled="listMode !== 'active'"
              checked-children="置顶"
              un-checked-children="普通"
              @change="(val) => quickUpdate(record, 'sticky', val)"
            />
          </template>
          <template v-if="column.dataIndex === 'status'">
            <div class="article-quick-control">
              <a-select
                :value="record.status"
                size="small"
                class="inline-select inline-select--status"
                popup-class-name="article-quick-dropdown"
                style="width: 112px"
                :disabled="listMode !== 'active'"
                @change="(val) => quickUpdate(record, 'status', val)"
              >
                <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
              </a-select>
            </div>
          </template>
          <template v-if="column.dataIndex === 'visibility'">
            <div class="article-quick-control">
              <a-select
                :value="record.visibility || 'PUBLIC'"
                size="small"
                class="inline-select inline-select--visibility"
                popup-class-name="article-quick-dropdown"
                style="width: 136px"
                :disabled="listMode !== 'active'"
                @change="(val) => quickUpdate(record, 'visibility', val)"
              >
                <a-select-option value="PUBLIC">公开</a-select-option>
                <a-select-option value="LOGIN">登录可见</a-select-option>
                <a-select-option value="PASSWORD">密码可见</a-select-option>
                <a-select-option value="PRIVATE">指定用户</a-select-option>
                <a-select-option value="UNLISTED">隐藏(直链)</a-select-option>
              </a-select>
            </div>
          </template>
          <template v-if="column.dataIndex === 'categoryName'">
            <a-select
              :value="record.categoryId"
              size="small"
              class="inline-select"
              style="width: 120px"
              :options="categoryOptions"
              :disabled="listMode !== 'active'"
              placeholder="未分类"
              show-search
              option-filter-prop="label"
              @change="(val) => quickUpdate(record, 'categoryId', val)"
            />
          </template>
          <template v-if="column.dataIndex === 'tagNames'">
            <a-select
              :value="record.tagIds || []"
              mode="multiple"
              size="small"
              class="inline-select"
              style="min-width: 160px; width: 100%"
              :options="tagOptions"
              :disabled="listMode !== 'active'"
              placeholder="未设标签"
              :max-tag-count="3"
              show-search
              option-filter-prop="label"
              @change="(val) => quickUpdate(record, 'tagIds', val)"
            />
          </template>
          <template v-if="column.dataIndex === 'metrics'">
            {{ record.viewCount }} 阅读 / {{ record.likeCount }} 赞 / {{ record.favoriteCount }} 收藏
          </template>
          <template v-if="column.dataIndex === 'commentsEnabled'">
            <div class="article-quick-control article-quick-control--switch">
              <a-switch
                :checked="record.commentsEnabled !== false"
                size="small"
                :disabled="listMode !== 'active'"
                checked-children="允许"
                un-checked-children="关闭"
                @change="(val) => quickUpdate(record, 'commentsEnabled', val)"
              />
            </div>
          </template>
          <template v-if="column.dataIndex === 'action'">
            <a-space v-if="listMode === 'active'">
              <a @click="editArticle(record)">编辑</a>
              <a @click="previewArticle(record)">预览</a>
              <a-popconfirm title="确认将该文章移入回收站？前台将不可见。" @confirm="removeArticle(record)">
                <a class="danger-link">移入回收站</a>
              </a-popconfirm>
            </a-space>
            <a-space v-else>
              <a @click="restoreArticleRecord(record)">恢复</a>
              <a-popconfirm title="确认彻底删除该文章？评论、点赞、收藏和标签关系会一并删除，无法恢复。" @confirm="purgeArticleRecord(record)">
                <a class="danger-link">彻底删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="stickyModalVisible"
      title="置顶文章排序"
      :confirm-loading="stickySaving"
      ok-text="保存顺序"
      cancel-text="取消"
      @ok="saveStickyOrder"
    >
      <a-spin :spinning="stickyLoading">
        <p class="sticky-sort-tip">拖动调整顺序，越靠上越靠前；保存后立即生效。也可在文章编辑里手填「置顶序号」。</p>
        <ul v-if="stickyList.length" class="sticky-sort-list">
          <li
            v-for="(item, index) in stickyList"
            :key="item.id"
            class="sticky-sort-item"
            draggable="true"
            @dragstart="onStickyDragStart(index)"
            @dragover.prevent
            @drop="onStickyDrop(index)"
          >
            <holder-outlined class="sticky-drag-handle" />
            <span class="sticky-order-no">{{ index + 1 }}</span>
            <span class="sticky-sort-title">{{ item.title }}</span>
          </li>
        </ul>
        <a-empty v-else-if="!stickyLoading" description="暂无置顶文章，可在文章编辑里开启置顶" />
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted, onActivated } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { HolderOutlined } from '@ant-design/icons-vue';
import { useRouter } from 'vue-router';
import { batchDeleteArticle, batchPurgeArticle, batchUpdateArticleFlags, createArticlePreviewLink, deleteArticle, exportArticles, getArticleList, purgeArticle, reorderStickyArticles, restoreArticle, scanArticleLinks, updateArticleFlags } from '@/api/Article';
import { getAdminCategoryList } from '@/api/Category';
import { getAdminTagList } from '@/api/Tag';
import { formatDateTime } from '@/utils/date';
const router = useRouter();
const loading = ref(false);
const articles = ref([]);
const categoryOptions = ref([]);
const tagOptions = ref([]);
const loadCategoryTagOptions = async () => {
  try {
    const [cats, tags] = await Promise.all([getAdminCategoryList(), getAdminTagList()]);
    categoryOptions.value = (cats || []).map(c => ({ value: c.id, label: c.name }));
    tagOptions.value = (tags || []).map(t => ({ value: t.id, label: t.name }));
  } catch {
    // 选项加载失败不阻塞列表
  }
};
const statusFilter = ref(undefined);
const visibilityFilter = ref(undefined);
const stickyFilter = ref(undefined);
const listMode = ref(localStorage.getItem('admin.article.listMode') || 'active');
const keywordInput = ref(localStorage.getItem('admin.article.keyword') || '');
const keyword = ref(keywordInput.value);
const selectedRowKeys = ref([]);
const exporting = ref(false);
const scanning = ref(false);
const batchUpdating = ref(false);
const pagination = reactive({
  current: Number(localStorage.getItem('admin.article.page') || 1) || 1,
  pageSize: Number(localStorage.getItem('admin.article.pageSize') || 10) || 10,
  total: 0,
  showSizeChanger: true,
  showTotal: total => `共 ${total} 条`
});
// 恢复筛选记忆
try {
  const savedStatus = localStorage.getItem('admin.article.status');
  if (savedStatus) statusFilter.value = savedStatus;
  const savedVisibility = localStorage.getItem('admin.article.visibility');
  if (savedVisibility) visibilityFilter.value = savedVisibility;
  const savedSticky = localStorage.getItem('admin.article.sticky');
  if (savedSticky === 'true') stickyFilter.value = true;
  if (savedSticky === 'false') stickyFilter.value = false;
} catch {
  // ignore
}
const persistFilters = () => {
  try {
    localStorage.setItem('admin.article.listMode', listMode.value);
    localStorage.setItem('admin.article.keyword', keyword.value || '');
    localStorage.setItem('admin.article.page', String(pagination.current));
    localStorage.setItem('admin.article.pageSize', String(pagination.pageSize));
    if (statusFilter.value) localStorage.setItem('admin.article.status', statusFilter.value);
    else localStorage.removeItem('admin.article.status');
    if (visibilityFilter.value) localStorage.setItem('admin.article.visibility', visibilityFilter.value);
    else localStorage.removeItem('admin.article.visibility');
    if (stickyFilter.value === true || stickyFilter.value === false) {
      localStorage.setItem('admin.article.sticky', String(stickyFilter.value));
    } else {
      localStorage.removeItem('admin.article.sticky');
    }
  } catch {
    // ignore
  }
};
const statusOptions = [{
  value: 'DRAFT',
  label: '草稿'
}, {
  value: 'PENDING_REVIEW',
  label: '待审核'
}, {
  value: 'SCHEDULED',
  label: '定时发布'
}, {
  value: 'PUBLISHED',
  label: '已发布'
}, {
  value: 'REJECTED',
  label: '已驳回'
}];
const statusMap = {
  DRAFT: {
    label: '草稿',
    color: 'default'
  },
  PENDING_REVIEW: {
    label: '待审核',
    color: 'orange'
  },
  SCHEDULED: {
    label: '定时发布',
    color: 'blue'
  },
  PUBLISHED: {
    label: '已发布',
    color: 'green'
  },
  REJECTED: {
    label: '已驳回',
    color: 'red'
  }
};
const visibilityMap = {
  PUBLIC: { label: '公开', color: 'green' },
  LOGIN: { label: '登录可见', color: 'blue' },
  PASSWORD: { label: '密码可见', color: 'orange' },
  PRIVATE: { label: '指定用户', color: 'purple' },
  UNLISTED: { label: '隐藏(直链)', color: 'default' }
};
const columns = computed(() => [{
  title: 'ID',
  dataIndex: 'id',
  width: 80
}, {
  title: '标题',
  dataIndex: 'title',
  ellipsis: true
}, {
  title: '分类',
  dataIndex: 'categoryName',
  width: 140
}, {
  title: '标签',
  dataIndex: 'tagNames',
  width: 240
}, {
  title: '置顶',
  dataIndex: 'sticky',
  width: 90
}, {
  title: '状态',
  dataIndex: 'status',
  width: 128
}, {
  title: '可见性',
  dataIndex: 'visibility',
  width: 152
}, {
  title: '评论',
  dataIndex: 'commentsEnabled',
  width: 112
}, {
  title: '数据',
  dataIndex: 'metrics',
  width: 240
}, {
  title: listMode.value === 'deleted' ? '删除时间' : '更新时间',
  dataIndex: listMode.value === 'deleted' ? 'deletedAt' : 'updatedAt',
  width: 220,
  customRender: ({
    text
  }) => formatDateTime(text)
}, {
  title: '操作',
  dataIndex: 'action',
  width: listMode.value === 'deleted' ? 180 : 220,
  fixed: 'right'
}]);
const batchActionTitle = computed(() => listMode.value === 'deleted' ? '确认彻底删除选中的文章？该操作无法恢复。' : '确认将选中的文章移入回收站？前台将不可见。');
const batchActionText = computed(() => listMode.value === 'deleted' ? '批量彻底删除' : '批量移入回收站');
const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: keys => {
    selectedRowKeys.value = keys;
  }
}));
const loadArticles = async () => {
  loading.value = true;
  try {
    persistFilters();
    const page = await getArticleList({
      page: pagination.current - 1,
      size: pagination.pageSize,
      status: statusFilter.value,
      deleted: listMode.value === 'deleted',
      keyword: keyword.value,
      visibility: visibilityFilter.value,
      sticky: stickyFilter.value
    });
    articles.value = page.items;
    pagination.total = page.totalItems;
    selectedRowKeys.value = selectedRowKeys.value.filter(id => articles.value.some(article => article.id === id));
  } finally {
    loading.value = false;
  }
};
const searchArticles = value => {
  keywordInput.value = (value ?? keywordInput.value).trim();
  keyword.value = keywordInput.value;
  pagination.current = 1;
  loadArticles();
};
const reload = () => {
  pagination.current = 1;
  loadArticles();
};
const resetFilters = () => {
  keywordInput.value = '';
  keyword.value = '';
  statusFilter.value = undefined;
  visibilityFilter.value = undefined;
  stickyFilter.value = undefined;
  listMode.value = 'active';
  pagination.current = 1;
  loadArticles();
};
const handleTableChange = page => {
  pagination.current = page.current;
  pagination.pageSize = page.pageSize;
  loadArticles();
};
const onBatchFlagsMenu = async ({ key }) => {
  if (!selectedRowKeys.value.length || !key) return;
  const [field, raw] = String(key).split(':');
  const payload = {};
  if (field === 'status') payload.status = raw;
  if (field === 'visibility') payload.visibility = raw;
  if (field === 'sticky') payload.sticky = raw === 'true';
  batchUpdating.value = true;
  try {
    const result = await batchUpdateArticleFlags(selectedRowKeys.value, payload);
    const updated = result?.updated ?? 0;
    const failed = result?.failed?.length || 0;
    message.success(`已更新 ${updated} 篇${failed ? `，失败 ${failed}` : ''}`);
    await loadArticles();
  } catch (error) {
    message.error(error?.message || '批量更新失败');
  } finally {
    batchUpdating.value = false;
  }
};
const createArticle = () => router.push('/articles/edit');
const exportSelected = async () => {
  if (!selectedRowKeys.value.length) return;
  exporting.value = true;
  try {
    const items = await exportArticles(selectedRowKeys.value);
    const list = Array.isArray(items) ? items : [];
    if (!list.length) {
      message.warning('没有可导出的文章');
      return;
    }
    const body = list.map(item => {
      const tags = (item.tagNames || []).join(', ');
      return [
        `---`,
        `title: ${JSON.stringify(item.title || '')}`,
        `slug: ${JSON.stringify(item.slug || '')}`,
        `summary: ${JSON.stringify(item.summary || '')}`,
        `category: ${JSON.stringify(item.categoryName || '')}`,
        `tags: ${JSON.stringify(tags)}`,
        `status: ${JSON.stringify(item.status || '')}`,
        `publishedAt: ${JSON.stringify(item.publishedAt || '')}`,
        `cover: ${JSON.stringify(item.coverImageUrl || '')}`,
        `---`,
        ``,
        item.content || '',
        ``
      ].join('\n');
    }).join('\n\n');
    const blob = new Blob([body], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `articles-export-${Date.now()}.md`;
    a.click();
    URL.revokeObjectURL(url);
    message.success(`已导出 ${list.length} 篇文章`);
  } catch (error) {
    message.error(error?.response?.data?.message || error?.message || '导出失败');
  } finally {
    exporting.value = false;
  }
};
const scanSelectedLinks = async () => {
  if (!selectedRowKeys.value.length) return;
  scanning.value = true;
  try {
    const issues = await scanArticleLinks(selectedRowKeys.value);
    const list = Array.isArray(issues) ? issues : [];
    if (!list.length) {
      message.success('未发现坏链');
      return;
    }
    Modal.warning({
      title: `发现 ${list.length} 个问题链接`,
      width: 720,
      content: list.slice(0, 30).map(item => `#${item.articleId} ${item.articleTitle}: ${item.url}（${item.reason}）`).join('\n') + (list.length > 30 ? `\n… 另有 ${list.length - 30} 条` : '')
    });
  } catch (error) {
    message.error(error?.response?.data?.message || error?.message || '扫描失败');
  } finally {
    scanning.value = false;
  }
};
const editArticle = record => router.push(`/articles/edit?id=${record.id}`);
// 置顶排序：弹窗内拖拽现有置顶文章调整先后；保存按顺序写回 stickyOrder（步长 10）。
const stickyModalVisible = ref(false);
const stickyLoading = ref(false);
const stickySaving = ref(false);
const stickyList = ref([]);
let draggingStickyIndex = -1;
const openStickyOrder = async () => {
  stickyModalVisible.value = true;
  stickyLoading.value = true;
  try {
    const page = await getArticleList({ page: 0, size: 200, deleted: false });
    stickyList.value = (page.items || [])
      .filter(item => item.sticky === true)
      .sort((a, b) => (a.stickyOrder ?? 0) - (b.stickyOrder ?? 0) || a.id - b.id);
  } finally {
    stickyLoading.value = false;
  }
};
const onStickyDragStart = index => {
  draggingStickyIndex = index;
};
const onStickyDrop = index => {
  if (draggingStickyIndex < 0 || draggingStickyIndex === index) {
    draggingStickyIndex = -1;
    return;
  }
  const list = stickyList.value.slice();
  const [moved] = list.splice(draggingStickyIndex, 1);
  list.splice(index, 0, moved);
  stickyList.value = list;
  draggingStickyIndex = -1;
};
const saveStickyOrder = async () => {
  stickySaving.value = true;
  try {
    const payload = stickyList.value.map((item, idx) => ({ id: item.id, stickyOrder: (idx + 1) * 10 }));
    await reorderStickyArticles(payload);
    message.success('置顶顺序已保存');
    stickyModalVisible.value = false;
    await loadArticles();
  } finally {
    stickySaving.value = false;
  }
};
const previewArticle = async record => {
  if (record.status === 'PUBLISHED') {
    window.open(`/article/${record.id}`, '_blank');
    return;
  }
  const preview = await createArticlePreviewLink(record.id);
  const url = preview.previewPath;
  try {
    await navigator.clipboard?.writeText(`${window.location.origin}${url}`);
    message.success('预览链接已生成并复制');
  } catch {
    message.success('预览链接已生成');
  }
  window.open(url, '_blank');
};
// 列表内联快捷修改：乐观更新 + 失败回滚，状态变化后用服务端结果同步整行（如发布时间）。
const quickUpdate = async (record, field, value) => {
  if (field === 'status' && value === 'SCHEDULED') {
    message.info('请在编辑页设置定时发布时间');
    router.push(`/articles/edit?id=${record.id}`);
    return;
  }
  const previous = record[field];
  record[field] = value;
  try {
    const updated = await updateArticleFlags(record.id, { [field]: value });
    Object.assign(record, updated);
    message.success('已更新');
  } catch (error) {
    record[field] = previous;
    message.error(error?.response?.data?.message || '更新失败');
  }
};
const removeArticle = async record => {
  await deleteArticle(record.id);
  message.success('文章已移入回收站');
  await loadArticles();
};
const restoreArticleRecord = async record => {
  await restoreArticle(record.id);
  message.success('文章已恢复');
  await loadArticles();
};
const purgeArticleRecord = async record => {
  await purgeArticle(record.id);
  message.success('文章已彻底删除');
  await loadArticles();
};
const removeSelectedArticles = async () => {
  if (!selectedRowKeys.value.length) {
    return;
  }
  const count = selectedRowKeys.value.length;
  if (listMode.value === 'deleted') {
    await batchPurgeArticle(selectedRowKeys.value);
  } else {
    await batchDeleteArticle(selectedRowKeys.value);
  }
  selectedRowKeys.value = [];
  message.success(listMode.value === 'deleted' ? `已彻底删除 ${count} 篇文章` : `已移入回收站 ${count} 篇文章`);
  await loadArticles();
};
onMounted(() => {
  loadArticles();
  loadCategoryTagOptions();
});
// 列表页被 <keep-alive> 缓存，从编辑页返回时 onMounted 不再触发。
// 用 onActivated 在每次「激活」（含返回）时刷新列表，确保置顶/状态等改动立即反映，不显示旧数据。
onActivated(() => {
  loadArticles();
});
</script>

<style lang="scss" scoped>
.admin-page {
  padding: 16px;
}

.article-quick-control {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  min-height: 36px;
  padding: 2px 0;
}
.article-quick-control--switch {
  padding-left: 2px;
}

/* 内联编辑选择框美化：圆角、hover/聚焦主题色描边，列表里更精致统一。 */
.inline-select :deep(.ant-select-selector) {
  border-radius: 8px !important;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.inline-select:hover :deep(.ant-select-selector) {
  border-color: #2f54eb !important;
}
.inline-select.ant-select-focused :deep(.ant-select-selector) {
  border-color: #2f54eb !important;
  box-shadow: 0 0 0 2px rgba(47, 84, 235, 0.12) !important;
}

.muted {
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
}

.danger-link {
  color: #ff4d4f;
}

.sticky-sort-tip {
  margin: 0 0 12px;
  color: #8c8c8c;
  font-size: 13px;
}
.sticky-sort-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 420px;
  overflow-y: auto;
}
.sticky-sort-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 8px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fafafa;
  cursor: move;
}
.sticky-sort-item:hover {
  border-color: #91caff;
}
.sticky-drag-handle {
  color: #bfbfbf;
}
.sticky-order-no {
  flex: 0 0 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: 50%;
  background: #1677ff;
  color: #fff;
  font-size: 12px;
}
.sticky-sort-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

<style lang="scss">
.article-quick-dropdown {
  padding: 6px;
}

.article-quick-dropdown .ant-select-item {
  min-height: 34px;
  padding: 7px 12px;
  border-radius: 8px;
}

.article-quick-dropdown .ant-select-item + .ant-select-item {
  margin-top: 2px;
}
</style>
