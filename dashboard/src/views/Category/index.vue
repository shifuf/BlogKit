<template>
  <div class="category-container">
    <a-card :bordered="false" class="nav-dynamic-card">
      <template #title>导航项（主页 / 动态 / 友链 / 归档）</template>
      <div class="nav-fixed-grid">
        <div v-for="item in fixedNavItems" :key="item.key" class="nav-fixed-item">
          <div class="nav-fixed-item__title">{{ item.label }}</div>
          <div class="nav-dynamic-item">
            <span class="nav-dynamic-label">显示</span>
            <a-switch v-model:checked="item.visible" />
          </div>
          <div class="nav-dynamic-item">
            <span class="nav-dynamic-label">排序</span>
            <a-input-number v-model:value="item.order" :precision="0" style="width: 120px" />
          </div>
        </div>
      </div>
      <div class="nav-dynamic-row" style="margin-top: 16px">
        <a-button type="primary" :loading="navDynamicSaving" @click="saveNavDynamic">保存导航项</a-button>
      </div>
      <a-alert
        class="nav-dynamic-tip"
        type="info"
        show-icon
        message="主页/动态/友链/归档为固定导航，可在此控制显隐与排序；下方分类也支持「导航显示」与拖拽排序。排序值越小越靠前，与分类 displayOrder 合并计算。"
      />
    </a-card>

    <a-card :bordered="false">
      <div class="table-page-search-wrapper">
        <a-form layout="inline" @submit.prevent>
          <a-form-item label="分类">
            <a-input v-model:value="queryParams.keyword" placeholder="名称、slug 或父级" allow-clear />
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
          新增分类
        </a-button>
        <a-popconfirm
          v-if="selectedRowKeys.length > 0"
          title="确定要删除选中的分类吗？已被文章使用或包含二级分类的分类不能删除。"
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

      <a-alert
        class="sort-tip"
        type="info"
        show-icon
        message="清空筛选后可拖动同级分类行调整排序；父子级关系请通过编辑分类设置。"
      />

      <a-table
        :columns="columns"
        :data-source="categoryList"
        :row-key="record => record.id"
        :pagination="pagination"
        :loading="loading || sorting"
        :custom-row="customRow"
        :scroll="{ x: 1350 }"
        bordered
        @change="handleTableChange"
        :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'drag'">
            <a-tooltip title="拖动排序">
              <menu-outlined
                :class="['drag-handle', { 'drag-handle--disabled': !canDrag(record) }]"
                @mousedown.stop="startPointerDrag(record, $event)"
              />
            </a-tooltip>
          </template>
          <template v-else-if="column.dataIndex === 'name'">
            <span class="category-name" :style="{ paddingLeft: `${(record.level - 1) * 18}px` }">
              {{ record.name }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'level'">
            <a-tag :color="record.level === 1 ? 'blue' : record.level === 2 ? 'purple' : 'cyan'">
              {{ record.level === 1 ? '顶级' : record.level === 2 ? '二级' : '三级' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'externalUrl'">
            <a-tooltip v-if="record.externalUrl" :title="record.externalUrl">
              <a :href="record.externalUrl" target="_blank" rel="noopener noreferrer" class="external-link">外链</a>
            </a-tooltip>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.dataIndex === 'navVisible'">
            <a-tag :color="record.navVisible !== false ? 'green' : 'default'">
              {{ record.navVisible !== false ? '显示' : '隐藏' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'parentName'">
            {{ record.parentName || '-' }}
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
              <a-tooltip title="上移">
                <a-button type="link" size="small" :disabled="!canMove(record, -1)" @click="moveByOffset(record, -1)">
                  <template #icon><up-outlined /></template>
                </a-button>
              </a-tooltip>
              <a-tooltip title="下移">
                <a-button type="link" size="small" :disabled="!canMove(record, 1)" @click="moveByOffset(record, 1)">
                  <template #icon><down-outlined /></template>
                </a-button>
              </a-tooltip>
              <a v-if="record.level < 3" @click="handleAddChild(record)">新增子分类</a>
              <a-divider v-if="record.level < 3" type="vertical" />
              <a @click="viewArticles(record)">查看文章</a>
              <a-divider type="vertical" />
              <a @click="handleEdit(record)">编辑</a>
              <a-divider type="vertical" />
              <a-popconfirm
                title="确定要删除此分类吗？已被文章使用或包含二级分类的分类不能删除。"
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
          <span>暂无分类</span>
        </template>
      </a-table>

      <category-modal
        v-model:visible="modalVisible"
        :category-info="currentCategory"
        :categories="allCategoryList"
        @success="getList"
      />

      <bound-articles-modal
        v-model:open="articlesModalVisible"
        type="category"
        :taxonomy="currentArticlesTaxonomy"
      />
    </a-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { Grid, message } from 'ant-design-vue';
import { PlusOutlined, DeleteOutlined, MenuOutlined, UpOutlined, DownOutlined } from '@ant-design/icons-vue';
import { getAdminCategoryList, deleteCategory, reorderCategories } from '@/api/Category';
import { listSettings, saveSettingGroup } from '@/api/Settings';
import CategoryModal from './components/CategoryModal.vue';
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
const dragCell = record => ({
  class: draggingRecord.value && canDropOn(record) ? 'category-drop-cell' : '',
  onDragover: event => handleDragOver(record, event),
  onDrop: event => handleDrop(record, event)
});
const screens = Grid.useBreakpoint();
const columns = computed(() => [{
  title: '分类名称',
  dataIndex: 'name',
  width: 180
}, {
  title: '',
  dataIndex: 'drag',
  width: 54,
  align: 'center'
}, {
  title: 'ID',
  dataIndex: 'id',
  width: 80,
  align: 'center'
}, {
  title: '层级',
  dataIndex: 'level',
  width: 100,
  align: 'center'
}, {
  title: '外链',
  dataIndex: 'externalUrl',
  width: 90,
  align: 'center'
}, {
  title: '导航',
  dataIndex: 'navVisible',
  width: 90,
  align: 'center'
}, {
  title: '父级',
  dataIndex: 'parentName',
  width: 140,
  ellipsis: true
}, {
  title: 'Slug',
  dataIndex: 'slug',
  width: 160,
  ellipsis: true
}, {
  title: '排序',
  dataIndex: 'displayOrder',
  width: 100,
  align: 'center'
}, {
  title: '文章数',
  dataIndex: 'articleCount',
  width: 100,
  align: 'center'
}, {
  title: '操作',
  dataIndex: 'action',
  width: 220,
  align: 'center',
  fixed: screens.value.md ? 'right' : undefined
}].map(column => ({
  ...column,
  customCell: dragCell
})));
const categoryList = ref([]);
const allCategoryList = ref([]);
const loading = ref(false);
const sorting = ref(false);
const selectedRowKeys = ref([]);
const modalVisible = ref(false);
const currentCategory = ref({});
// 「查看文章」：弹窗按分类精确过滤展示其绑定的文章。
const articlesModalVisible = ref(false);
const currentArticlesTaxonomy = ref({});
const viewArticles = record => {
  currentArticlesTaxonomy.value = { id: record.id, name: record.name };
  articlesModalVisible.value = true;
};
const draggingRecord = ref(null);
const normalizeList = data => Array.isArray(data) ? data.map(item => ({
  ...item
})) : [];
const sortRows = rows => rows.slice().sort((a, b) => {
  const orderDiff = Number(a.displayOrder || 0) - Number(b.displayOrder || 0);
  return orderDiff || Number(a.id || 0) - Number(b.id || 0);
});
const buildDisplayRows = () => {
  const rows = normalizeList(allCategoryList.value);
  const ids = new Set(rows.map(item => item.id));
  const byParent = new Map();
  rows.forEach(item => {
    const parentKey = item.parentId && ids.has(item.parentId) ? item.parentId : 0;
    if (!byParent.has(parentKey)) {
      byParent.set(parentKey, []);
    }
    byParent.get(parentKey).push(item);
  });
  const result = [];
  const appendRows = (parentKey, level, parentName = '') => {
    sortRows(byParent.get(parentKey) || []).forEach(item => {
      result.push({
        ...item,
        level,
        parentName
      });
      appendRows(item.id, level + 1, item.name);
    });
  };
  appendRows(0, 1);
  return result;
};
const applyFilter = () => {
  const keyword = queryParams.keyword.trim().toLowerCase();
  const rows = buildDisplayRows();
  const filtered = keyword ? rows.filter(item => (item.name || '').toLowerCase().includes(keyword) || (item.slug || '').toLowerCase().includes(keyword) || (item.parentName || '').toLowerCase().includes(keyword)) : rows;
  categoryList.value = filtered;
  pagination.total = filtered.length;
  pagination.current = 1;
};
const getList = async () => {
  loading.value = true;
  try {
    allCategoryList.value = normalizeList(await getAdminCategoryList());
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
  currentCategory.value = {};
  modalVisible.value = true;
};
const handleAddChild = record => {
  currentCategory.value = {
    parentId: record.id
  };
  modalVisible.value = true;
};
const handleEdit = record => {
  currentCategory.value = {
    ...record
  };
  modalVisible.value = true;
};
const handleDelete = async record => {
  await deleteCategory(record.id);
  message.success('删除成功');
  getList();
};
const handleBatchDelete = async () => {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请至少选择一条记录');
    return;
  }
  const selectedRows = categoryList.value.filter(item => selectedRowKeys.value.includes(item.id)).sort((a, b) => Number(b.level || 1) - Number(a.level || 1));
  for (const record of selectedRows) {
    await deleteCategory(record.id);
  }
  message.success('批量删除成功');
  getList();
};
const parentKey = record => record?.parentId || null;
const canDrag = record => !queryParams.keyword.trim() && record?.id && !sorting.value;
const getSiblings = record => sortRows(allCategoryList.value.filter(item => parentKey(item) === parentKey(record)));
const canDropOn = record => canDrag(record) && draggingRecord.value && parentKey(draggingRecord.value) === parentKey(record);
const canMove = (record, offset) => {
  if (!canDrag(record)) {
    return false;
  }
  const siblings = getSiblings(record);
  const index = siblings.findIndex(item => item.id === record.id);
  return index >= 0 && index + offset >= 0 && index + offset < siblings.length;
};
const moveRecord = async (source, target) => {
  if (!source || !target || source.id === target.id) {
    return;
  }
  const sourceParent = parentKey(source);
  const targetParent = parentKey(target);
  if (sourceParent !== targetParent) {
    message.warning('只能在同级分类内拖动排序');
    return;
  }
  const siblings = getSiblings(source);
  const fromIndex = siblings.findIndex(item => item.id === source.id);
  const toIndex = siblings.findIndex(item => item.id === target.id);
  if (fromIndex < 0 || toIndex < 0) {
    return;
  }
  const [moved] = siblings.splice(fromIndex, 1);
  siblings.splice(toIndex, 0, moved);
  siblings.forEach((item, index) => {
    item.displayOrder = (index + 1) * 10;
    const raw = allCategoryList.value.find(category => category.id === item.id);
    if (raw) {
      raw.displayOrder = item.displayOrder;
    }
  });
  sorting.value = true;
  try {
    await reorderCategories(siblings.map(item => ({
      id: item.id,
      displayOrder: item.displayOrder
    })));
    message.success('分类排序已保存');
    applyFilter();
  } finally {
    sorting.value = false;
  }
};
const moveByOffset = async (record, offset) => {
  const siblings = getSiblings(record);
  const index = siblings.findIndex(item => item.id === record.id);
  const target = siblings[index + offset];
  await moveRecord(record, target);
};
// 指针拖拽排序：自行用 mousedown/mouseup 实现，避免原生 HTML5 drag 被浏览器扩展(SuperDrag)全局劫持报错。
let pointerSource = null;
const rowIdUnderPointer = (x, y) => {
  const el = document.elementFromPoint(x, y);
  const row = el && el.closest ? el.closest('[data-cat-id]') : null;
  return row ? Number(row.getAttribute('data-cat-id')) : null;
};
const onPointerUp = async event => {
  document.removeEventListener('mouseup', onPointerUp);
  document.body.style.userSelect = '';
  const source = pointerSource;
  pointerSource = null;
  draggingRecord.value = null;
  if (!source) return;
  const targetId = rowIdUnderPointer(event.clientX, event.clientY);
  if (!targetId || targetId === source.id) return;
  const target = allCategoryList.value.find(item => item.id === targetId);
  if (target) await moveRecord(source, target);
};
const startPointerDrag = (record, event) => {
  if (!canDrag(record)) return;
  event.preventDefault();
  pointerSource = record;
  draggingRecord.value = record;
  document.body.style.userSelect = 'none';
  document.addEventListener('mouseup', onPointerUp);
};
const customRow = record => ({
  class: canDrag(record) ? 'category-row is-draggable' : 'category-row',
  // 给每行打 data-cat-id，指针拖拽 mouseup 时用 elementFromPoint 定位落点行。
  ['data-cat-id']: record.id
});
// 固定导航项：主页/动态/友链/归档，均可在此显隐与排序（存 home 设置组）。
const fixedNavItems = reactive([
  { key: 'home', label: '主页', visibleKey: 'homeNavVisible', orderKey: 'homeNavOrder', visible: true, order: -100 },
  { key: 'dynamics', label: '动态', visibleKey: 'dynamicsNavVisible', orderKey: 'dynamicsNavOrder', visible: true, order: 0 },
  { key: 'friends', label: '友链', visibleKey: 'friendsNavVisible', orderKey: 'friendsNavOrder', visible: true, order: 800 },
  { key: 'archive', label: '归档', visibleKey: 'archiveNavVisible', orderKey: 'archiveNavOrder', visible: true, order: 900 }
]);
const navDynamicSaving = ref(false);
const loadNavDynamic = async () => {
  try {
    const home = await listSettings('home');
    const map = Object.fromEntries((home || []).map(item => [item.settingKey, item.settingValue]));
    fixedNavItems.forEach(item => {
      item.visible = map[item.visibleKey] !== 'false';
      const raw = Number(map[item.orderKey]);
      item.order = Number.isFinite(raw) ? raw : item.order;
    });
  } catch {
    // 读取失败保持默认
  }
};
const saveNavDynamic = async () => {
  navDynamicSaving.value = true;
  try {
    const payload = {};
    fixedNavItems.forEach(item => {
      payload[item.visibleKey] = String(item.visible);
      payload[item.orderKey] = String(item.order ?? 0);
    });
    await saveSettingGroup('home', payload);
    message.success('导航项设置已保存');
  } finally {
    navDynamicSaving.value = false;
  }
};
onMounted(() => {
  getList();
  loadNavDynamic();
});
</script>

<style lang="scss" scoped>
.category-container {
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

  .sort-tip {
    margin-bottom: 16px;
  }

  .drag-handle {
    color: #8c8c8c;
    cursor: grab;
  }

  .drag-handle--disabled {
    color: #d9d9d9;
    cursor: not-allowed;
  }

  .category-name {
    display: inline-block;
    white-space: normal;
    overflow-wrap: anywhere;
  }

  .text-danger {
    color: #ff4d4f;
  }

  .external-link {
    color: #1677ff;
    text-decoration: underline;
  }
}

:deep(.category-row.is-draggable) {
  cursor: move;
}

:deep(.category-drop-cell) {
  background: #e6f4ff;
}

.nav-dynamic-card {
  margin-bottom: 16px;
}
.nav-fixed-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}
.nav-fixed-item {
  padding: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  background: #fafafa;
  display: grid;
  gap: 10px;
}
.nav-fixed-item__title {
  font-weight: 600;
}
.nav-dynamic-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 24px;
}
.nav-dynamic-item {
  display: flex;
  align-items: center;
  gap: 10px;
}
.nav-dynamic-label {
  font-size: 13px;
  color: #555;
}
.nav-dynamic-tip {
  margin-top: 14px;
}
</style>
