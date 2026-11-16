<template>
  <div class="comment-admin-page">
    <a-card :bordered="false">
      <template #title>评论管理</template>

      <div class="comment-admin-page__switch">
        <a-radio-group v-model:value="commentType" button-style="solid" @change="onTypeChange">
          <a-radio-button value="article">文章评论</a-radio-button>
          <a-radio-button value="dynamic">动态评论</a-radio-button>
        </a-radio-group>
      </div>

      <div v-if="commentType === 'article'" class="comment-summary">
        <button
          v-for="item in summaryItems"
          :key="item.key"
          class="comment-summary__item"
          type="button"
          @click="applySummaryFilter(item.key)"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </button>
      </div>

      <a-form layout="inline" class="comment-admin-page__filters">
        <a-form-item label="关键词">
          <a-input v-model:value="queryParams.keyword" placeholder="评论内容或用户" allow-clear />
        </a-form-item>
        <a-form-item v-if="commentType === 'article'" label="文章 ID">
          <a-input-number v-model:value="queryParams.articleId" :min="1" placeholder="可选" />
        </a-form-item>
        <a-form-item v-if="commentType === 'article'" label="状态">
          <a-select v-model:value="queryParams.status" allow-clear placeholder="全部" style="width: 140px">
            <a-select-option v-for="item in statusOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="commentType === 'article'" label="举报">
          <a-select v-model:value="queryParams.reported" allow-clear placeholder="全部" style="width: 120px">
            <a-select-option :value="true">仅举报</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleQuery">查询</a-button>
            <a-button @click="resetQuery">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <div class="comment-admin-page__ops">
        <a-space v-if="selectedRowKeys.length" wrap>
          <a-button v-if="commentType === 'article'" @click="handleBatchStatus('APPROVED')">批量通过（{{ selectedRowKeys.length }}）</a-button>
          <a-button v-if="commentType === 'article'" @click="handleBatchStatus('REJECTED')">批量驳回</a-button>
          <a-button v-if="commentType === 'article'" danger @click="handleBatchStatus('SPAM')">批量垃圾</a-button>
          <a-popconfirm
            :title="`确认删除选中的 ${selectedRowKeys.length} 条评论？${commentType === 'article' ? '其回复也会一并删除。' : '根评论的回复会一并删除。'}`"
            @confirm="handleBatchDelete"
          >
            <a-button danger>批量删除</a-button>
          </a-popconfirm>
        </a-space>
        <span v-else class="muted">勾选行后可批量删除</span>
      </div>

      <a-table
        row-key="id"
        :columns="columns"
        :data-source="commentList"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{ selectedRowKeys, onChange: keys => (selectedRowKeys = keys) }"
        bordered
        size="middle"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'type'">
            <a-tag :color="commentType === 'article' ? 'blue' : 'purple'">
              {{ commentType === 'article' ? '文章' : '动态' }}
            </a-tag>
          </template>
          <template v-if="column.dataIndex === 'content'">
            <div class="comment-content">
              <a-tag v-if="record.pinned" color="gold" class="reply-tag">置顶</a-tag>
              <a-tag v-if="record.parentId" color="cyan" class="reply-tag">回复</a-tag>
              {{ record.content }}
            </div>
            <div v-if="record.parentId" class="muted">
              回复 #{{ record.parentId }}
              <template v-if="record.replyTo"> · 回复 @{{ record.replyTo }}（#{{ record.replyToCommentId }}）</template>
              <blockquote v-if="record.replyToContent">{{ record.replyToContent }}</blockquote>
            </div>
          </template>
          <template v-if="column.dataIndex === 'status'">
            <a-tag :color="statusMap[record.status]?.color">
              {{ statusMap[record.status]?.label || record.status || '已通过' }}
            </a-tag>
            <a-tag v-if="record.reported" color="magenta">举报 {{ record.reportCount || 1 }}</a-tag>
          </template>
          <template v-if="column.dataIndex === 'authorName'">
            <strong>{{ record.authorName }}</strong>
            <div v-if="record.authorUsername" class="muted">@{{ record.authorUsername }}</div>
            <div v-if="record.notifyOnReply" class="muted">已开回复邮件</div>
          </template>
          <template v-if="column.dataIndex === 'authorEmail'">
            <span v-if="record.authorEmail">{{ record.authorEmail }}</span>
            <span v-else class="muted">—</span>
          </template>
          <template v-if="column.dataIndex === 'articleTitle'">
            <a class="article-title-link" :title="record.articleTitle" @click="openArticle(record.articleId)">{{ record.articleTitle }}</a>
            <div class="muted">ID: {{ record.articleId }}</div>
          </template>
          <template v-if="column.dataIndex === 'dynamicSummary'">
            <span class="article-title-link" :title="record.dynamicSummary">{{ record.dynamicSummary || '（无内容）' }}</span>
            <div class="muted">ID: {{ record.dynamicId }}</div>
          </template>
          <template v-if="column.dataIndex === 'metrics'">
            {{ record.likeCount }} 赞 / {{ record.replyCount }} 回复
          </template>
          <template v-if="column.dataIndex === 'action'">
            <a-space>
              <template v-if="commentType === 'article'">
                <a v-if="record.status !== 'APPROVED'" @click="approveComment(record)">通过</a>
                <a v-if="record.status !== 'REJECTED'" @click="rejectComment(record)">驳回</a>
                <a v-if="record.status !== 'SPAM'" class="danger-link" @click="spamComment(record)">垃圾</a>
                <a v-if="!record.parentId && !record.pinned" @click="pinComment(record)">置顶</a>
                <a v-if="!record.parentId && record.pinned" @click="unpinComment(record)">取消置顶</a>
                <a @click="viewReplies(record)">{{ record.parentId ? '查看上下文' : '查看回复' }}</a>
              </template>
              <a-popconfirm title="确认删除该评论？回复也会一并删除。" @confirm="removeComment(record)">
                <a class="danger-link">删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="replyVisible" title="评论上下文与回复" width="760px" :footer="null" destroy-on-close>
      <a-comment v-if="currentComment">
        <template #author>{{ currentComment.authorName }}</template>
        <template #avatar>
          <a-avatar>{{ currentComment.authorName?.charAt(0) }}</a-avatar>
        </template>
        <template #content>
          <blockquote v-if="currentComment.parentId" class="reply-context">
            回复 @{{ currentComment.replyTo || '原评论作者' }}
            <p>{{ currentComment.replyToContent || '原评论已不可用' }}</p>
          </blockquote>
          <p>{{ currentComment.content }}</p>
        </template>
        <template #datetime>{{ formatDateTime(currentComment.createdAt) }}</template>
      </a-comment>

      <div class="admin-reply-box">
        <a-textarea
          v-model:value="adminReplyContent"
          :rows="3"
          maxlength="2000"
          show-count
          placeholder="以管理员身份回复该评论（发布后自动通过）"
        />
        <div class="admin-reply-box__actions">
          <a-button type="primary" :loading="adminReplyLoading" :disabled="!String(adminReplyContent ?? '').trim()" @click="submitAdminReply">
            发布回复
          </a-button>
        </div>
      </div>

      <a-divider orientation="left">回复列表</a-divider>
      <a-list :data-source="replyList" :loading="replyLoading" item-layout="horizontal">
        <template #renderItem="{ item }">
          <a-list-item>
            <template #actions>
              <a v-if="item.status !== 'APPROVED'" @click="approveReply(item)">通过</a>
              <a v-if="item.status !== 'REJECTED'" @click="rejectReply(item)">驳回</a>
              <a v-if="item.status !== 'SPAM'" class="danger-link" @click="spamReply(item)">垃圾</a>
              <a-popconfirm title="确认删除该回复？" @confirm="removeReply(item)">
                <a class="danger-link">删除</a>
              </a-popconfirm>
            </template>
            <a-list-item-meta>
              <template #avatar>
                <a-avatar>{{ item.authorName?.charAt(0) }}</a-avatar>
              </template>
              <template #title>
                {{ item.authorName }}
                <span class="muted">@{{ item.authorUsername }}</span>
                <span v-if="item.authorEmail" class="muted"> · {{ item.authorEmail }}</span>
              </template>
              <template #description>
                <blockquote v-if="item.replyTo">回复 @{{ item.replyTo }}（#{{ item.replyToCommentId }}）<p>{{ item.replyToContent || '原评论已不可用' }}</p></blockquote>
                <p class="comment-content">{{ item.content }}</p>
                <span>
                  <a-tag :color="statusMap[item.status]?.color">{{ statusMap[item.status]?.label || item.status || '已通过' }}</a-tag>
                  <a-tag v-if="item.reported" color="magenta">举报 {{ item.reportCount || 1 }}</a-tag>
                  {{ formatDateTime(item.createdAt) }} / {{ item.likeCount }} 赞
                </span>
              </template>
            </a-list-item-meta>
          </a-list-item>
        </template>
        <template #emptyText>
          <a-empty description="暂无回复" />
        </template>
      </a-list>
      <a-button v-if="replyList.length < replyTotal" :loading="replyLoading" @click="loadReplies(currentComment, true)">加载更多回复</a-button>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted, watch } from 'vue';
import { message } from 'ant-design-vue';
import {
  approveMessage, deleteMessage, getMessageList, rejectMessage, batchDeleteMessages,
  getDynamicCommentList, deleteDynamicComment, batchDeleteDynamicComments,
  getCommentSummary, markMessageSpam, batchUpdateMessageStatus, pinMessage, unpinMessage,
  replyMessage
} from '@/api/Message';
import { formatDateTime } from '@/utils/date';
const commentType = ref('article');
const selectedRowKeys = ref([]);
const queryParams = reactive({
  keyword: '',
  articleId: undefined,
  status: undefined,
  reported: undefined
});
const summary = reactive({
  pending: 0,
  approved: 0,
  rejected: 0,
  spam: 0,
  reported: 0
});
const summaryItems = computed(() => [
  { key: 'PENDING', label: '待审核', value: summary.pending },
  { key: 'REPORTED', label: '举报', value: summary.reported },
  { key: 'SPAM', label: '垃圾', value: summary.spam },
  { key: 'APPROVED', label: '已通过', value: summary.approved },
  { key: 'REJECTED', label: '已驳回', value: summary.rejected }
]);
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: total => `共 ${total} 条`
});
const articleColumns = [
  { title: 'ID', dataIndex: 'id', width: 70, align: 'center' },
  { title: '类型', dataIndex: 'type', width: 70, align: 'center' },
  { title: '评论内容', dataIndex: 'content', ellipsis: true },
  { title: '状态', dataIndex: 'status', width: 90, align: 'center' },
  { title: '用户', dataIndex: 'authorName', width: 150 },
  { title: '邮箱', dataIndex: 'authorEmail', width: 180, ellipsis: true },
  { title: '所属文章', dataIndex: 'articleTitle', width: 200 },
  { title: '数据', dataIndex: 'metrics', width: 130, align: 'center' },
  { title: '创建时间', dataIndex: 'createdAt', width: 170, customRender: ({ text }) => formatDateTime(text) },
  { title: '操作', dataIndex: 'action', width: 260, align: 'center', fixed: 'right' }
];
const dynamicColumns = [
  { title: 'ID', dataIndex: 'id', width: 70, align: 'center' },
  { title: '类型', dataIndex: 'type', width: 70, align: 'center' },
  { title: '评论内容', dataIndex: 'content', ellipsis: true },
  { title: '用户', dataIndex: 'authorName', width: 150 },
  { title: '所属动态', dataIndex: 'dynamicSummary', width: 260 },
  { title: '创建时间', dataIndex: 'createdAt', width: 170, customRender: ({ text }) => formatDateTime(text) },
  { title: '操作', dataIndex: 'action', width: 120, align: 'center', fixed: 'right' }
];
const columns = computed(() => commentType.value === 'article' ? articleColumns : dynamicColumns);
const statusOptions = [
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'SPAM', label: '垃圾' }
];
const statusMap = {
  PENDING: { label: '待审核', color: 'orange' },
  APPROVED: { label: '已通过', color: 'green' },
  REJECTED: { label: '已驳回', color: 'red' },
  SPAM: { label: '垃圾', color: 'volcano' }
};
const commentList = ref([]);
const loading = ref(false);
const replyVisible = ref(false);
const currentComment = ref(null);
const replyList = ref([]);
const replyPage = ref(1);
const replyTotal = ref(0);
const replyLoading = ref(false);
const adminReplyContent = ref('');
const adminReplyLoading = ref(false);
let replyRequestId = 0;
let replyContextId = 0;
watch(replyVisible, visible => {
  if (!visible) { replyRequestId += 1; replyContextId += 1; replyLoading.value = false; }
});
const getList = async () => {
  loading.value = true;
  selectedRowKeys.value = [];
  try {
    if (commentType.value === 'article') {
      await loadSummary();
      const page = await getMessageList({
        pageNum: pagination.current,
        pageSize: pagination.pageSize,
        keyword: queryParams.keyword?.trim(),
        articleId: queryParams.articleId,
        status: queryParams.status,
        reported: queryParams.reported
      });
      commentList.value = page.items;
      pagination.total = page.totalItems;
    } else {
      const page = await getDynamicCommentList({
        pageNum: pagination.current,
        pageSize: pagination.pageSize,
        keyword: queryParams.keyword?.trim()
      });
      commentList.value = page.items;
      pagination.total = page.totalItems;
    }
  } finally {
    loading.value = false;
  }
};
const loadSummary = async () => {
  const data = await getCommentSummary();
  summary.pending = data?.pending || 0;
  summary.approved = data?.approved || 0;
  summary.rejected = data?.rejected || 0;
  summary.spam = data?.spam || 0;
  summary.reported = data?.reported || 0;
};
const applySummaryFilter = key => {
  queryParams.keyword = '';
  queryParams.articleId = undefined;
  if (key === 'REPORTED') {
    queryParams.status = undefined;
    queryParams.reported = true;
  } else {
    queryParams.status = key;
    queryParams.reported = undefined;
  }
  pagination.current = 1;
  getList();
};
const onTypeChange = () => {
  pagination.current = 1;
  queryParams.keyword = '';
  queryParams.articleId = undefined;
  queryParams.status = undefined;
  queryParams.reported = undefined;
  getList();
};
const handleQuery = () => {
  pagination.current = 1;
  getList();
};
const resetQuery = () => {
  queryParams.keyword = '';
  queryParams.articleId = undefined;
  queryParams.status = undefined;
  queryParams.reported = undefined;
  pagination.current = 1;
  getList();
};
const handleTableChange = pag => {
  pagination.current = pag.current;
  pagination.pageSize = pag.pageSize;
  getList();
};
const handleBatchDelete = async () => {
  const ids = [...selectedRowKeys.value];
  if (!ids.length) return;
  if (commentType.value === 'article') {
    await batchDeleteMessages(ids);
  } else {
    await batchDeleteDynamicComments(ids);
  }
  message.success(`已删除 ${ids.length} 条评论`);
  await getList();
};
const handleBatchStatus = async status => {
  const ids = [...selectedRowKeys.value];
  if (!ids.length) return;
  await batchUpdateMessageStatus(ids, status);
  message.success(`已更新 ${ids.length} 条评论`);
  await getList();
};
const viewReplies = async record => {
  replyContextId += 1;
  adminReplyContent.value = '';
  replyList.value = [];
  replyTotal.value = 0;
  currentComment.value = record;
  replyVisible.value = true;
  await loadReplies(record);
};
const loadReplies = async (record, append = false) => {
  if (!record || (append && replyLoading.value)) return;
  const requestId = ++replyRequestId;
  const selectedId = record.id;
  if (!append) replyPage.value = 1;
  const pageNumber = replyPage.value;
  replyLoading.value = true;
  try {
    const page = await getMessageList({ pageNum: pageNumber, pageSize: 20, parentId: record.parentId || record.id });
    if (requestId !== replyRequestId || !replyVisible.value || currentComment.value?.id !== selectedId) return;
    replyList.value = append ? [...replyList.value, ...(page.items || [])] : (page.items || []);
    replyTotal.value = page.totalItems || 0;
    replyPage.value = pageNumber + 1;
  } catch (error) {
    if (requestId === replyRequestId) message.error(error?.message || '回复加载失败，请重试');
  } finally {
    if (requestId === replyRequestId) replyLoading.value = false;
  }
};
const submitAdminReply = async () => {
  const target = currentComment.value;
  const draft = String(adminReplyContent.value ?? '');
  const content = draft.trim();
  if (!target || !content || adminReplyLoading.value) return;
  const contextId = replyContextId;
  adminReplyLoading.value = true;
  try {
    await replyMessage(target.id, content);
    message.success('回复已发布');
    if (replyVisible.value && replyContextId === contextId && currentComment.value?.id === target.id) {
      if (adminReplyContent.value === draft) adminReplyContent.value = '';
      await loadReplies(target);
    }
    await getList();
  } catch (error) {
    message.error(error?.message || '回复发布失败');
  } finally {
    adminReplyLoading.value = false;
  }
};
const removeComment = async record => {
  if (commentType.value === 'article') {
    await deleteMessage(record.id);
  } else {
    await deleteDynamicComment(record.id);
  }
  message.success('评论已删除');
  await getList();
};
const approveComment = async record => {
  await approveMessage(record.id);
  message.success('评论已通过');
  await getList();
};
const rejectComment = async record => {
  await rejectMessage(record.id);
  message.success('评论已驳回');
  await getList();
};
const spamComment = async record => {
  await markMessageSpam(record.id);
  message.success('已标记为垃圾评论');
  await getList();
};
const pinComment = async record => {
  await pinMessage(record.id);
  message.success('评论已置顶');
  await getList();
};
const unpinComment = async record => {
  await unpinMessage(record.id);
  message.success('已取消置顶');
  await getList();
};
const approveReply = async record => {
  await approveMessage(record.id);
  message.success('回复已通过');
  if (currentComment.value) {
    await loadReplies(currentComment.value);
    await getList();
  }
};
const rejectReply = async record => {
  await rejectMessage(record.id);
  message.success('回复已驳回');
  if (currentComment.value) {
    await loadReplies(currentComment.value);
    await getList();
  }
};
const spamReply = async record => {
  await markMessageSpam(record.id);
  message.success('已标记为垃圾评论');
  if (currentComment.value) {
    await loadReplies(currentComment.value);
    await getList();
  }
};
const removeReply = async record => {
  await deleteMessage(record.id);
  message.success('回复已删除');
  if (currentComment.value) {
    await loadReplies(currentComment.value);
    await getList();
  }
};
const openArticle = articleId => {
  window.open(`/article/${articleId}`, '_blank');
};
onMounted(getList);
</script>

<style lang="scss" scoped>
.comment-admin-page {
  padding: 16px;
}

.comment-admin-page__switch {
  margin-bottom: 16px;
}

.comment-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.comment-summary__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 58px;
  padding: 0 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.comment-summary__item:hover {
  border-color: #91caff;
  color: #1677ff;
}

.comment-summary__item strong {
  font-size: 22px;
}

.comment-admin-page__filters {
  margin-bottom: 16px;
  padding: 16px;
  border-radius: 8px;
  background: #fafafa;
}

.comment-admin-page__ops {
  margin-bottom: 12px;
  min-height: 24px;
}

.comment-content {
  max-height: 96px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.reply-tag {
  margin-right: 6px;
  vertical-align: baseline;
}

/* 所属文章/动态标题：最多显示两行，超出省略，避免长标题撑破单元格。 */
.article-title-link {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
  line-height: 1.4;
}
a.article-title-link {
  color: #1677ff;
  cursor: pointer;
}

.muted {
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
}

.danger-link {
  color: #ff4d4f;
}

.reply-context {
  margin: 8px 0;
  padding: 8px 12px;
  border-left: 3px solid #1677ff;
  background: #f5f7fa;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.reply-context p { margin: 4px 0 0; }
.admin-reply-box__actions { margin-top: 12px; text-align: right; }
</style>
