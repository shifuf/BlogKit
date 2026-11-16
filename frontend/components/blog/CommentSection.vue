<script setup lang="ts">
import { BadgeCheck, ChevronDown, Flag, Image, MessageCircle, RefreshCw, SendHorizontal, Trash2, UserRound } from '@lucide/vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import MarkdownIt from 'markdown-it';
import type { CommentItem } from '~/types/blog';
import { buildAvatarUrl, toBlogComment } from '~/utils/blog-adapter';
import { formatDateTime, formatRelativeTime } from '~/utils/date';
import { sanitizeCommentHtml } from '~/utils/sanitize-html';
const maxCommentLength = 2000;
// html:false — 用户输入中的 <img onerror=...> 等原生 HTML 一律当文本，不进 DOM。
const md = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true
});
const stripLegacyEmoji = (content: string) => String(content || '').replace(/!\[([^\]]*)\]\([^)]*\/emoji\/fluent\/[^)]*\)/g, (_match, alt) => alt || '');
const renderCommentContent = (content: string) => {
  const trusted = sanitizeCommentHtml(md.render(stripLegacyEmoji(content)));
  return trusted
    .replace(/<img\b/g, '<img class="comment-image" loading="lazy"')
    .replace(/<a\b(?![^>]*\brel=)/g, '<a rel="nofollow noopener noreferrer" target="_blank"');
};
const props = withDefaults(defineProps<{
  articleId?: number;
  commentsEnabled?: boolean;
  /** 文章 commentCount（根评论+回复）；列表分页 total 只含根评论，互动数用这个。 */
  interactionCount?: number;
}>(), {
  commentsEnabled: true,
  interactionCount: 0
});
const userStore = useStore.user();
const {
  openLogin
} = useLoginModal();
const {
  createComment,
  deleteComment,
  fetchComments,
  fetchCommentReplies,
  reportComment,
  uploadCommentImage
} = useApiClient();
const {
  siteSettings,
  loadSiteSettings
} = useSiteSettings();
const isAdmin = computed(() => String(userStore.roleCode || '').toUpperCase() === 'ADMIN');
interface CommentFormState {
  author: string;
  email: string;
  website: string;
  content: string;
  notifyOnReply: boolean;
}
interface ActiveReplyState {
  rootId: number;
  targetId: number;
  targetAuthor: string;
}
const createForm = (): CommentFormState => ({
  author: '',
  email: '',
  website: '',
  content: '',
  notifyOnReply: false
});
const GUEST_PROFILE_KEY = 'bd-guest-comment-profile';
const readGuestProfile = (): Pick<CommentFormState, 'author' | 'email' | 'website'> => {
  if (!import.meta.client) return { author: '', email: '', website: '' };
  try {
    const raw = localStorage.getItem(GUEST_PROFILE_KEY);
    if (!raw) return { author: '', email: '', website: '' };
    const parsed = JSON.parse(raw) as Partial<CommentFormState>;
    return {
      author: String(parsed.author || '').slice(0, 64),
      email: String(parsed.email || '').slice(0, 128),
      website: String(parsed.website || '').slice(0, 256)
    };
  } catch {
    return { author: '', email: '', website: '' };
  }
};
const saveGuestProfile = (target: CommentFormState) => {
  if (!import.meta.client || userStore.isAuthenticated) return;
  try {
    localStorage.setItem(GUEST_PROFILE_KEY, JSON.stringify({
      author: target.author.trim().slice(0, 64),
      email: target.email.trim().slice(0, 128),
      website: target.website.trim().slice(0, 256)
    }));
  } catch {
    // ignore quota / private mode
  }
};
const applyGuestProfile = (target: CommentFormState) => {
  if (userStore.isAuthenticated) return;
  const saved = readGuestProfile();
  if (saved.author && !target.author) target.author = saved.author;
  if (saved.email && !target.email) target.email = saved.email;
  if (saved.website && !target.website) target.website = saved.website;
};
const comments = ref<CommentItem[]>([]);
const commentsLoading = ref(false);
const commentsPage = ref(0);
const commentsPageSize = 20;
/** 根评论分页总数（仅用于加载更多）。 */
const commentsTotal = ref(0);
/** 本地增减：发表/删除后文章 prop 可能尚未刷新。 */
const interactionDelta = ref(0);
const expandedThreads = ref<Set<number>>(new Set());
const activeReply = ref<ActiveReplyState | null>(null);
const form = reactive<CommentFormState>(createForm());
const replyForm = reactive<CommentFormState>(createForm());
// 记住自己的游客昵称，用于高亮本人评论（与 localStorage 同步）
const rememberedGuestName = ref('');
const useRemoteComments = computed(() => Number.isInteger(props.articleId) && Number(props.articleId) > 0);
// 文章级评论开关：关闭时前台直接展示「评论已关闭」，不再显示编辑器（避免填写后提交才被后端拒绝）。
const commentsClosed = computed(() => props.commentsEnabled === false);
const currentUserName = computed(() => userStore.displayName || userStore.username || '当前用户');
const isSelfComment = (comment: CommentItem) => {
  if (userStore.isAuthenticated) {
    return comment.author === currentUserName.value
      || comment.author === userStore.username
      || comment.author === userStore.displayName;
  }
  const mine = (rememberedGuestName.value || form.author || '').trim();
  return Boolean(mine) && comment.author === mine;
};
// 邮件通知：后台总开关决定是否展示勾选；真正发信还需要平台已配置（后台 notificationPlatformEnabled）
const replyEmailFeatureEnabled = computed(() => Boolean(siteSettings.value.commentReplyEmailEnabled));
const notificationPlatformReady = computed(() => Boolean(siteSettings.value.notificationPlatformEnabled));
// 勾选展示只看功能开关，避免平台 Key 未填时勾选被隐藏导致用户以为功能坏了
const replyEmailAvailable = computed(() => replyEmailFeatureEnabled.value);
const hasMoreComments = computed(() => comments.value.filter(c => !c.status || c.status === 'APPROVED').length < commentsTotal.value);
const replyCount = (comment: CommentItem) => comment.replyCount ?? comment.replies?.length ?? 0;
// 互动 = 根评论 + 回复。优先文章 commentCount；并用已加载树校验，避免漏计回复。
const interactionTotal = computed(() => {
  const fromLoaded = comments.value.reduce(
    (sum, item) => sum + ((!item.status || item.status === 'APPROVED') ? 1 + Number(replyCount(item) || 0) : 0),
    0
  );
  const fromArticle = Math.max(0, Number(props.interactionCount || 0) + interactionDelta.value);
  return Math.max(fromLoaded, fromArticle);
});
const isExpanded = (id: number) => expandedThreads.value.has(id);
const visibleReplies = (comment: CommentItem) => {
  const replies = comment.replies ?? [];
  return isExpanded(comment.id) ? replies : replies.slice(0, 2);
};
const replyPages = ref<Record<number, number>>({});
const repliesLoading = ref<Set<number>>(new Set());
const loadMoreReplies = async (comment: CommentItem) => {
  if (repliesLoading.value.has(comment.id)) return;
  repliesLoading.value = new Set(repliesLoading.value).add(comment.id);
  try {
    const page = replyPages.value[comment.id] ?? 0;
    const response = await fetchCommentReplies(comment.id, page, 10);
    if (response.success) {
      const incoming = response.data.items.map(toBlogComment);
      const existing = page === 0 ? [] : (comment.replies || []);
      comment.replies = [...existing, ...incoming.filter(item => !existing.some(c => c.id === item.id))];
      comment.replyCount = response.data.totalItems;
      replyPages.value[comment.id] = page + 1;
    }
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '回复加载失败'));
  } finally {
    const next = new Set(repliesLoading.value); next.delete(comment.id); repliesLoading.value = next;
  }
};
const toggleReplies = (id: number) => {
  const next = new Set(expandedThreads.value);
  if (next.has(id)) {
    next.delete(id);
  } else {
    next.add(id);
    const root = findRootComment(id);
    if (root && (root.replies?.length || 0) < replyCount(root)) void loadMoreReplies(root);
  }
  expandedThreads.value = next;
};
const metaTags = (comment: CommentItem) => [comment.location, comment.os, comment.browser].filter(Boolean) as string[];
const commentTimeSource = (comment: CommentItem) => comment.createdAt || comment.date;
const displayCommentTime = (comment: CommentItem) => formatRelativeTime(commentTimeSource(comment));
const absoluteCommentTime = (comment: CommentItem) => comment.createdAt ? formatDateTime(comment.createdAt) : comment.date;
const initial = (author: string) => author.slice(0, 1).toUpperCase();
const resetForm = (target: CommentFormState) => {
  // 保留游客昵称/邮箱/网站，只清正文与通知勾选
  const keepAuthor = target.author;
  const keepEmail = target.email;
  const keepWebsite = target.website;
  target.content = '';
  target.notifyOnReply = false;
  if (target === form) {
    uploadedImages.value.main = [];
    if (!userStore.isAuthenticated) {
      target.author = keepAuthor;
      target.email = keepEmail;
      target.website = keepWebsite;
    } else {
      target.author = '';
      target.email = '';
      target.website = '';
    }
  } else {
    uploadedImages.value.reply = [];
    if (!userStore.isAuthenticated) {
      target.author = keepAuthor || form.author;
      target.email = keepEmail || form.email;
      target.website = keepWebsite || form.website;
    } else {
      target.author = '';
      target.email = '';
      target.website = '';
    }
  }
};
const validateForm = (target: CommentFormState) => {
  if (!target.content.trim()) {
    ElMessage.warning('请填写评论内容');
    return false;
  }
  if (!useRemoteComments.value) {
    ElMessage.warning('当前页面未绑定文章，不能发表评论');
    return false;
  }
  if (!userStore.isAuthenticated && !target.author.trim()) {
    ElMessage.warning('请填写昵称');
    return false;
  }
  if (target.content.trim().length > maxCommentLength) {
    ElMessage.warning(`评论内容不能超过 ${maxCommentLength} 字`);
    return false;
  }
  const email = target.email.trim();
  const emailRe = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  // 填写了邮箱就校验格式；勾选通知时必填且格式正确。
  if (email && !emailRe.test(email)) {
    ElMessage.warning('邮箱格式不正确');
    return false;
  }
  if (target.notifyOnReply && replyEmailAvailable.value) {
    if (!userStore.isAuthenticated && !email) {
      ElMessage.warning('勾选邮件通知时请填写邮箱');
      return false;
    }
  }
  return true;
};
const guestPayloadOf = (target: CommentFormState) => {
  if (userStore.isAuthenticated) return undefined;
  return {
    guestName: target.author.trim(),
    guestEmail: target.email.trim() || undefined,
    guestWebsite: target.website.trim() || undefined
  };
};
const apiErrorMessage = (error: unknown, fallback: string) => {
  const apiError = error as {
    data?: {
      message?: string;
    };
    response?: {
      _data?: {
        message?: string;
      };
    };
    message?: string;
  };
  return apiError?.data?.message || apiError?.response?._data?.message || apiError?.message || fallback;
};
const imageUploadTarget = ref<'main' | 'reply'>('main');
const imageUploading = ref(false);
const uploadedImages = ref<{
  main: string[];
  reply: string[];
}>({
  main: [],
  reply: []
});
const mainFileInput = ref<HTMLInputElement | null>(null);
const replyFileInput = ref<HTMLInputElement | null>(null);
const nestedReplyFileInput = ref<HTMLInputElement | null>(null);
const triggerImageUpload = (target: 'main' | 'reply') => {
  if (!userStore.isAuthenticated) {
    ElMessage.warning('登录后可上传图片');
    openLogin();
    return;
  }
  imageUploadTarget.value = target;
  if (target === 'main') {
    mainFileInput.value?.click();
  } else {
    (replyFileInput.value || nestedReplyFileInput.value)?.click();
  }
};
const maxCommentImages = 3;
const handleImageFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  if (!files.length) return;
  input.value = '';
  const target = imageUploadTarget.value;
  const targetForm = target === 'main' ? form : replyForm;
  const currentImages = uploadedImages.value[target];
  if (currentImages.length >= maxCommentImages) {
    ElMessage.warning(`每条评论最多上传 ${maxCommentImages} 张图片`);
    return;
  }
  const remainingSlots = maxCommentImages - currentImages.length;
  const batch = files.slice(0, remainingSlots);
  for (const file of batch) {
    if (!file.type.startsWith('image/')) {
      ElMessage.warning('请选择图片文件');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      ElMessage.warning('图片大小不能超过 5MB');
      return;
    }
  }
  const placeholderLength = batch.reduce(sum => sum + '![图片](url)\n'.length, 0);
  if (targetForm.content.length + placeholderLength > maxCommentLength) {
    ElMessage.warning(`添加图片后内容将超过 ${maxCommentLength} 字限制`);
    return;
  }
  imageUploading.value = true;
  try {
    const results = await Promise.allSettled(batch.map(file => uploadCommentImage(file)));
    let successCount = 0;
    results.forEach((result, index) => {
      if (result.status === 'fulfilled') {
        const response = result.value;
        if (response.success) {
          const imageUrl = response.data.url;
          const markdown = `![图片](${imageUrl})`;
          targetForm.content += (targetForm.content ? '\n' : '') + markdown;
          uploadedImages.value[target].push(imageUrl);
          successCount += 1;
        } else {
          ElMessage.warning(response.message || `第 ${index + 1} 张图片上传失败`);
        }
      } else {
        ElMessage.error(apiErrorMessage(result.reason, `第 ${index + 1} 张图片上传失败`));
      }
    });
    if (successCount > 0) {
      ElMessage.success(`成功上传 ${successCount} 张图片`);
    }
  } finally {
    imageUploading.value = false;
  }
};
const removeUploadedImage = (target: 'main' | 'reply', index: number) => {
  const url = uploadedImages.value[target][index];
  const targetForm = target === 'main' ? form : replyForm;
  const markdownPattern = `![图片](${url})`;
  targetForm.content = targetForm.content.replace(markdownPattern, '').replace(/\n{2,}/g, '\n').trim();
  uploadedImages.value[target].splice(index, 1);
};
const findRootComment = (rootId: number) => comments.value.find(item => item.id === rootId);
const notifyCommentPosted = () => {
  if (import.meta.client && props.articleId) {
    window.dispatchEvent(new CustomEvent('bd:comment-posted', {
      detail: {
        articleId: props.articleId
      }
    }));
  }
};
const openReply = (root: CommentItem, target: CommentItem) => {
  activeReply.value = {
    rootId: root.id,
    targetId: target.id,
    targetAuthor: target.author
  };
  applyGuestProfile(replyForm);
  replyForm.author = form.author || replyForm.author;
  replyForm.email = form.email || replyForm.email;
  replyForm.website = form.website || replyForm.website;
  replyForm.content = '';
};
const closeReply = () => {
  activeReply.value = null;
  replyForm.content = '';
  uploadedImages.value.reply = [];
};
const isReplyOpen = (rootId: number, targetId: number) => activeReply.value?.rootId === rootId && activeReply.value.targetId === targetId;
const loadRemoteComments = async (append = false) => {
  if (!import.meta.client || commentsClosed.value) return;
  if (!useRemoteComments.value || !props.articleId) {
    comments.value = [];
    commentsTotal.value = 0;
    interactionDelta.value = 0;
    return;
  }
  if (!append) {
    commentsPage.value = 0;
  }
  commentsLoading.value = true;
  try {
    const response = await fetchComments(props.articleId, commentsPage.value, commentsPageSize);
    if (response.success) {
      const items = response.data.items.map(toBlogComment);
      comments.value = append ? [...comments.value, ...items] : items;
      if (!append) replyPages.value = {};
      commentsTotal.value = response.data.totalItems ?? 0;
      commentsPage.value += 1;
    } else {
      if (!append) {
        comments.value = [];
        commentsTotal.value = 0;
      }
    }
  } catch (error) {
    if (!append) {
      comments.value = [];
      commentsTotal.value = 0;
    }
    ElMessage.error(apiErrorMessage(error, '评论加载失败'));
  } finally {
    commentsLoading.value = false;
  }
};
const loadMoreComments = () => {
  if (commentsLoading.value || !hasMoreComments.value) return;
  void loadRemoteComments(true);
};
const submitComment = async () => {
  if (commentsClosed.value) {
    ElMessage.warning('该文章已关闭评论');
    return;
  }
  if (!validateForm(form)) return;
  if (useRemoteComments.value && props.articleId) {
    try {
      const response = await createComment(props.articleId, form.content.trim(), undefined, guestPayloadOf(form), form.notifyOnReply);
      if (response.success) {
        const posted = toBlogComment(response.data);
        comments.value.unshift(posted);
        if (response.data.status === 'APPROVED') {
          commentsTotal.value += 1;
          interactionDelta.value += 1;
        }
        if (!userStore.isAuthenticated) {
          saveGuestProfile(form);
          rememberedGuestName.value = form.author.trim();
        }
        resetForm(form);
        notifyCommentPosted();
        ElMessage.success(response.data.status === 'APPROVED' ? '评论已发布' : '评论已提交，审核通过后公开显示');
      }
    } catch (error) {
      ElMessage.error(apiErrorMessage(error, '评论发布失败'));
    }
    return;
  }
  ElMessage.warning('当前页面未绑定文章，不能发表评论');
};
const submitReply = async () => {
  if (commentsClosed.value) {
    ElMessage.warning('该文章已关闭评论');
    return;
  }
  if (!activeReply.value || !validateForm(replyForm)) return;
  const root = findRootComment(activeReply.value.rootId);
  if (!root) return;
  if (useRemoteComments.value && props.articleId) {
    try {
      const response = await createComment(props.articleId, replyForm.content.trim(), activeReply.value.targetId, guestPayloadOf(replyForm), replyForm.notifyOnReply);
      if (response.success) {
        const reply = toBlogComment(response.data);
        root.replies ??= [];
        root.replies.push(reply);
        const approved = reply.status === 'APPROVED';
        root.replyCount = (root.replyCount ?? root.replies.length - 1) + (approved ? 1 : 0);
        interactionDelta.value += approved ? 1 : 0;
        expandedThreads.value = new Set(expandedThreads.value).add(root.id);
        if (!userStore.isAuthenticated) {
          saveGuestProfile(replyForm);
          rememberedGuestName.value = replyForm.author.trim();
          // 同步到主表单，下次根评论也带上
          form.author = replyForm.author;
          form.email = replyForm.email;
          form.website = replyForm.website;
        }
        closeReply();
        notifyCommentPosted();
        ElMessage.success(response.data.status === 'APPROVED' ? '回复已发布' : '回复已提交，审核通过后公开显示');
      }
    } catch (error) {
      ElMessage.error(apiErrorMessage(error, '回复发布失败'));
    }
    return;
  }
  ElMessage.warning('当前页面未绑定文章，不能回复评论');
};
const deletingCommentId = ref<number | null>(null);
const reportingCommentId = ref<number | null>(null);
const removeComment = async (comment: CommentItem) => {
  try {
    await ElMessageBox.confirm('确认删除这条评论吗？删除后不可恢复。', '删除评论', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    });
  } catch {
    return;
  }
  deletingCommentId.value = comment.id;
  try {
    const response = await deleteComment(comment.id);
    if (response.success) {
      // 先按本地树扣减，刷新后若根评论已全部加载则以树为准。
      const isRoot = comments.value.some(item => item.id === comment.id);
      const removed = isRoot ? 1 + Number(replyCount(comment) || 0) : 1;
      interactionDelta.value -= removed;
      ElMessage.success('评论已删除');
      await loadRemoteComments();
    }
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '评论删除失败'));
  } finally {
    deletingCommentId.value = null;
  }
};
const reportCurrentComment = async (comment: CommentItem) => {
  if (reportingCommentId.value) return;
  try {
    await ElMessageBox.confirm('确认举报这条评论吗？', '举报评论', {
      confirmButtonText: '举报',
      cancelButtonText: '取消',
      type: 'warning'
    });
  } catch {
    return;
  }
  reportingCommentId.value = comment.id;
  try {
    const response = await reportComment(comment.id);
    if (response.success) {
      ElMessage.success(response.message || '已收到举报');
    }
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '举报失败'));
  } finally {
    reportingCommentId.value = null;
  }
};
watch(() => props.articleId, () => {
  interactionDelta.value = 0;
  void loadRemoteComments(false);
}, {
  immediate: true
});
watch(() => props.interactionCount, () => {
  // 文章侧 commentCount 刷新后，本地增量归零，避免重复累加。
  interactionDelta.value = 0;
});
onMounted(() => {
  void loadSiteSettings();
  if (import.meta.client) {
    applyGuestProfile(form);
    applyGuestProfile(replyForm);
    rememberedGuestName.value = form.author.trim();
  }
});
</script>

<template>
  <section v-if="useRemoteComments" id="comments" class="comments-section comments-section--aerocore">
    <header class="comment-panel-header">
      <h2><MessageCircle :size="20" />评论</h2>
      <span>{{ interactionTotal }} 条互动</span>
    </header>

    <div v-if="commentsClosed" class="comment-closed-banner" role="status">
      <MessageCircle class="comment-closed-banner__icon" :size="18" />
      <span>该文章已关闭评论</span>
    </div>

    <div v-if="!commentsClosed" class="comment-editor comment-editor--main">
      <input
        ref="mainFileInput"
        type="file"
        accept="image/jpeg,image/png,image/gif,image/webp"
        style="display: none"
        @change="handleImageFileChange"
      />
      <div v-if="!userStore.isAuthenticated" class="comment-guest-fields">
        <input v-model="form.author" class="comment-guest-fields__input" type="text" maxlength="64" placeholder="昵称（必填）" autocomplete="nickname" />
        <input v-model="form.email" class="comment-guest-fields__input" type="email" maxlength="128" placeholder="邮箱（选填）" autocomplete="email" />
        <input v-model="form.website" class="comment-guest-fields__input" type="url" maxlength="256" placeholder="网站（选填）" autocomplete="url" />
      </div>
      <div class="comment-editor__input-wrap">
        <textarea
          v-model="form.content"
          class="comment-editor__textarea"
          :maxlength="maxCommentLength"
          placeholder="说点什么吧..."
          @keydown.ctrl.enter.prevent="submitComment"
        />
        <div v-if="uploadedImages.main.length" class="comment-editor__image-previews">
          <div v-for="(url, index) in uploadedImages.main" :key="index" class="comment-editor__image-preview">
            <img :src="url" :alt="`已上传图片 ${index + 1}`" />
            <button type="button" class="comment-editor__image-remove" @click="removeUploadedImage('main', index)">
              <Trash2 :size="12" />
            </button>
          </div>
        </div>
        <div class="comment-editor__tools">
          <div class="comment-editor__tool-group" aria-label="评论工具">
            <button type="button" aria-label="图片" :disabled="imageUploading" @click="triggerImageUpload('main')"><Image :size="17" /></button>
            <button type="button" aria-label="刷新" @click="loadRemoteComments(false)"><RefreshCw :size="17" /></button>
          </div>
          <span>{{ form.content.length }}/{{ maxCommentLength }}</span>
        </div>
      </div>

      <div class="comment-editor__meta">
        <UserRound class="comment-editor__user-icon" :size="22" />
        <span class="comment-editor__field comment-editor__field--identity">{{ userStore.isAuthenticated ? currentUserName : '以游客身份评论' }}</span>
        <label
          v-if="replyEmailAvailable"
          class="comment-notify"
          title="收到回复时发送邮件到您的邮箱"
        >
          <input v-model="form.notifyOnReply" type="checkbox" />
          <span>有回复时邮件通知</span>
        </label>
        <button class="comment-editor__cancel" type="button" @click="resetForm(form)">取消</button>
        <button class="comment-editor__submit" type="button" @click="submitComment">
          发送 Ctrl ↵
        </button>
      </div>
      <p v-if="replyEmailAvailable && form.notifyOnReply && !userStore.isAuthenticated && !form.email.trim()" class="comment-notify-hint">勾选后请填写邮箱，以便接收回复通知。</p>
      <p v-else-if="replyEmailAvailable && form.notifyOnReply && !notificationPlatformReady" class="comment-notify-hint">邮件通道尚未配置完成，勾选会保留，管理员补齐消息平台后即可生效。</p>
    </div>

    <div v-if="!commentsClosed && !userStore.isAuthenticated" class="comment-auth-actions" aria-label="评论方式">
      <span class="comment-auth-actions__hint">游客可直接评论</span>
      <button class="comment-auth-actions__item comment-auth-actions__item--primary" type="button" @click="openLogin">
        <UserRound :size="15" aria-hidden="true" />
        登录后以账号发表
      </button>
    </div>

    <el-skeleton v-if="commentsLoading && comments.length === 0" :rows="4" animated />
    <el-empty v-else-if="!commentsLoading && comments.length === 0" description="暂无评论" />

    <div v-if="comments.length" class="comment-thread-list">
      <article v-for="comment in comments" :key="comment.id" class="comment-thread-card" :class="{ 'comment-thread-card--mine': isSelfComment(comment) }">
        <div class="aero-comment aero-comment--root" :class="{ 'aero-comment--mine': isSelfComment(comment) }">
          <div class="aero-comment__avatar">
            <img v-if="comment.avatar" :src="buildAvatarUrl(comment.avatar)" :alt="comment.author" loading="lazy" decoding="async">
            <span v-else>{{ initial(comment.author) }}</span>
          </div>

          <div class="aero-comment__body">
            <div class="aero-comment__head">
              <div class="aero-comment__identity">
                <span class="aero-comment__name" :class="{ 'aero-comment__name--mine': isSelfComment(comment) }">{{ comment.author }}</span>
                <span v-if="isSelfComment(comment)" class="aero-comment__badge aero-comment__badge--mine">我</span>
                <BadgeCheck v-if="comment.verified" class="aero-comment__verified" :size="15" aria-label="已认证" />
                <span v-if="comment.badge" class="aero-comment__badge">{{ comment.badge }}</span>
              </div>

              <div class="aero-comment__actions">
                <button class="aero-comment__reply-toggle" type="button" :aria-label="`回复 ${comment.author}`" @click="openReply(comment, comment)">
                  <MessageCircle :size="16" />
                  <span v-if="replyCount(comment)">{{ replyCount(comment) }}</span>
                </button>
                <button class="aero-comment__report" type="button" :disabled="reportingCommentId === comment.id" :aria-label="`举报 ${comment.author} 的评论`" @click="reportCurrentComment(comment)">
                  <Flag :size="15" />
                </button>
                <button v-if="isAdmin" class="aero-comment__delete" type="button" :disabled="deletingCommentId === comment.id" :aria-label="`删除 ${comment.author} 的评论`" @click="removeComment(comment)">
                  <Trash2 :size="15" />
                </button>
              </div>
            </div>

            <time class="aero-comment__meta-line" :datetime="commentTimeSource(comment)" :title="absoluteCommentTime(comment)">{{ displayCommentTime(comment) }}</time>
            <p v-if="comment.status && comment.status !== 'APPROVED'" role="status">待审核，暂未公开</p>
          <div class="aero-comment__content" v-html="renderCommentContent(comment.content)" />

            <div class="aero-comment__tags">
              <span v-for="tag in metaTags(comment)" :key="tag">{{ tag }}</span>
            </div>
          </div>
        </div>

        <div v-if="isReplyOpen(comment.id, comment.id)" class="comment-reply-popover comment-reply-popover--root">
          <div class="comment-editor comment-editor--reply">
            <div class="comment-editor__reply-title">回复 @{{ activeReply?.targetAuthor }}</div>
            <input
              ref="replyFileInput"
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              style="display: none"
              @change="handleImageFileChange"
            />
            <div v-if="!userStore.isAuthenticated" class="comment-guest-fields">
              <input v-model="replyForm.author" class="comment-guest-fields__input" type="text" maxlength="64" placeholder="昵称（必填）" />
              <input v-model="replyForm.email" class="comment-guest-fields__input" type="email" maxlength="128" placeholder="邮箱（选填）" />
              <input v-model="replyForm.website" class="comment-guest-fields__input" type="url" maxlength="256" placeholder="网站（选填）" />
            </div>
            <div class="comment-editor__input-wrap">
              <textarea
                v-model="replyForm.content"
                class="comment-editor__textarea"
                :maxlength="maxCommentLength"
                :placeholder="`回复 @${activeReply?.targetAuthor || ''}`"
                @keydown.ctrl.enter.prevent="submitReply"
              />
              <div v-if="uploadedImages.reply.length" class="comment-editor__image-previews">
                <div v-for="(url, index) in uploadedImages.reply" :key="index" class="comment-editor__image-preview">
                  <img :src="url" :alt="`已上传图片 ${index + 1}`" />
                  <button type="button" class="comment-editor__image-remove" @click="removeUploadedImage('reply', index)">
                    <Trash2 :size="12" />
                  </button>
                </div>
              </div>
              <div class="comment-editor__tools">
                <div class="comment-editor__tool-group" aria-label="回复工具">
                  <button type="button" aria-label="图片" :disabled="imageUploading" @click="triggerImageUpload('reply')"><Image :size="17" /></button>
                  <button type="button" aria-label="刷新" @click="loadRemoteComments(false)"><RefreshCw :size="17" /></button>
                </div>
                <span>{{ replyForm.content.length }}/{{ maxCommentLength }}</span>
              </div>
            </div>
            <div class="comment-editor__meta">
              <UserRound class="comment-editor__user-icon" :size="22" />
              <span class="comment-editor__field comment-editor__field--identity">{{ userStore.isAuthenticated ? currentUserName : '以游客身份回复' }}</span>
              <label
                v-if="replyEmailAvailable"
                class="comment-notify"
                title="收到回复时发送邮件到您的邮箱"
              >
                <input v-model="replyForm.notifyOnReply" type="checkbox" />
                <span>有回复时邮件通知</span>
              </label>
              <button class="comment-editor__cancel" type="button" @click="closeReply">取消</button>
              <button class="comment-editor__submit" type="button" @click="submitReply">
                回复 <SendHorizontal :size="14" />
              </button>
            </div>
            <p v-if="replyEmailAvailable && replyForm.notifyOnReply && !userStore.isAuthenticated && !replyForm.email.trim()" class="comment-notify-hint">勾选后请填写邮箱，以便接收回复通知。</p>
            <p v-else-if="replyEmailAvailable && replyForm.notifyOnReply && !notificationPlatformReady" class="comment-notify-hint">邮件通道尚未配置完成，勾选会保留，管理员补齐消息平台后即可生效。</p>
          </div>
        </div>

        <div v-if="comment.replies?.length" class="aero-comment__children">
          <template v-for="reply in visibleReplies(comment)" :key="reply.id">
            <article class="aero-comment aero-comment--reply" :class="{ 'aero-comment--mine': isSelfComment(reply) }">
              <div class="aero-comment__avatar">
                <img v-if="reply.avatar" :src="buildAvatarUrl(reply.avatar)" :alt="reply.author" loading="lazy" decoding="async">
                <span v-else>{{ initial(reply.author) }}</span>
              </div>

              <div class="aero-comment__body">
                <div class="aero-comment__head">
                  <div class="aero-comment__identity">
                    <span class="aero-comment__name" :class="{ 'aero-comment__name--mine': isSelfComment(reply) }">{{ reply.author }}</span>
                    <span v-if="isSelfComment(reply)" class="aero-comment__badge aero-comment__badge--mine">我</span>
                    <BadgeCheck v-if="reply.verified" class="aero-comment__verified" :size="15" aria-label="已认证" />
                    <span v-if="reply.badge" class="aero-comment__badge">{{ reply.badge }}</span>
                  </div>

                  <div class="aero-comment__actions">
                    <button class="aero-comment__reply-toggle aero-comment__reply-toggle--icon" type="button" :aria-label="`回复 ${reply.author}`" @click="openReply(comment, reply)">
                      <MessageCircle :size="16" />
                    </button>
                    <button class="aero-comment__report" type="button" :disabled="reportingCommentId === reply.id" :aria-label="`举报 ${reply.author} 的评论`" @click="reportCurrentComment(reply)">
                      <Flag :size="15" />
                    </button>
                    <button v-if="isAdmin" class="aero-comment__delete" type="button" :disabled="deletingCommentId === reply.id" :aria-label="`删除 ${reply.author} 的评论`" @click="removeComment(reply)">
                      <Trash2 :size="15" />
                    </button>
                  </div>
                </div>

                <div class="aero-comment__meta-line">
                  <time :datetime="commentTimeSource(reply)" :title="absoluteCommentTime(reply)">{{ displayCommentTime(reply) }}</time>
                </div>
                <blockquote v-if="reply.replyTo" class="comment-reply-context">
                回复 @{{ reply.replyTo }}<span v-if="reply.replyToUnavailable">（原评论已删除或暂不可见）</span>
                <p v-else-if="reply.replyToContent">{{ reply.replyToContent }}</p>
              </blockquote>
              <p v-if="reply.status && reply.status !== 'APPROVED'" role="status">待审核，暂未公开</p>
              <div class="aero-comment__content" v-html="renderCommentContent(reply.content)" />

                <div class="aero-comment__tags">
                  <span v-for="tag in metaTags(reply)" :key="tag">{{ tag }}</span>
                </div>
              </div>
            </article>

            <div v-if="isReplyOpen(comment.id, reply.id)" class="comment-reply-popover">
              <div class="comment-editor comment-editor--reply">
                <div class="comment-editor__reply-title">回复 @{{ activeReply?.targetAuthor }}</div>
                <input
                  ref="nestedReplyFileInput"
                  type="file"
                  accept="image/jpeg,image/png,image/gif,image/webp"
                  style="display: none"
                  @change="handleImageFileChange"
                />
                <div v-if="!userStore.isAuthenticated" class="comment-guest-fields">
                  <input v-model="replyForm.author" class="comment-guest-fields__input" type="text" maxlength="64" placeholder="昵称（必填）" />
                  <input v-model="replyForm.email" class="comment-guest-fields__input" type="email" maxlength="128" placeholder="邮箱（选填）" />
                  <input v-model="replyForm.website" class="comment-guest-fields__input" type="url" maxlength="256" placeholder="网站（选填）" />
                </div>
                <div class="comment-editor__input-wrap">
                  <textarea
                    v-model="replyForm.content"
                    class="comment-editor__textarea"
                    :maxlength="maxCommentLength"
                    :placeholder="`回复 @${activeReply?.targetAuthor || ''}`"
                    @keydown.ctrl.enter.prevent="submitReply"
                  />
                  <div v-if="uploadedImages.reply.length" class="comment-editor__image-previews">
                    <div v-for="(url, idx) in uploadedImages.reply" :key="idx" class="comment-editor__image-preview">
                      <img :src="url" :alt="`已上传图片 ${idx + 1}`" />
                      <button type="button" class="comment-editor__image-remove" @click="removeUploadedImage('reply', idx)">
                        <Trash2 :size="12" />
                      </button>
                    </div>
                  </div>
                  <div class="comment-editor__tools">
                    <div class="comment-editor__tool-group" aria-label="回复工具">
                      <button type="button" aria-label="图片" :disabled="imageUploading" @click="triggerImageUpload('reply')"><Image :size="17" /></button>
                      <button type="button" aria-label="刷新" @click="loadRemoteComments(false)"><RefreshCw :size="17" /></button>
                    </div>
                    <span>{{ replyForm.content.length }}/{{ maxCommentLength }}</span>
                  </div>
                </div>
                <div class="comment-editor__meta">
                  <UserRound class="comment-editor__user-icon" :size="22" />
                  <span class="comment-editor__field comment-editor__field--identity">{{ userStore.isAuthenticated ? currentUserName : '以游客身份回复' }}</span>
                  <label
                    v-if="replyEmailAvailable"
                    class="comment-notify"
                    title="收到回复时发送邮件到您的邮箱"
                  >
                    <input v-model="replyForm.notifyOnReply" type="checkbox" />
                    <span>有回复时邮件通知</span>
                  </label>
                  <button class="comment-editor__cancel" type="button" @click="closeReply">取消</button>
                  <button class="comment-editor__submit" type="button" @click="submitReply">
                    回复 <SendHorizontal :size="14" />
                  </button>
                </div>
                <p v-if="replyEmailAvailable && replyForm.notifyOnReply && !userStore.isAuthenticated && !replyForm.email.trim()" class="comment-notify-hint">勾选后请填写邮箱，以便接收回复通知。</p>
              </div>
            </div>
          </template>

          <button v-if="replyCount(comment) > 2" class="aero-comment__expand" type="button" @click="toggleReplies(comment.id)">
            <ChevronDown :class="{ 'is-open': isExpanded(comment.id) }" :size="16" />
            {{ isExpanded(comment.id) ? '收起回复' : `展开回复（${replyCount(comment)}）` }}
          </button>
          <button v-if="isExpanded(comment.id) && (comment.replies?.length || 0) < replyCount(comment)" class="aero-comment__expand" type="button" :disabled="repliesLoading.has(comment.id)" @click="loadMoreReplies(comment)">
            {{ repliesLoading.has(comment.id) ? '加载中...' : '加载更多回复' }}
          </button>
        </div>
      </article>
    </div>

    <div v-if="hasMoreComments" class="comment-load-more">
      <button
        type="button"
        class="comment-load-more__button"
        :disabled="commentsLoading"
        @click="loadMoreComments"
      >
        <RefreshCw v-if="commentsLoading" :size="15" class="comment-load-more__icon spin" />
        <span v-if="commentsLoading">加载中...</span>
        <span v-else>加载更多评论</span>
      </button>
    </div>

  </section>
</template>

<style scoped lang="scss">
.comment-reply-context { margin: 8px 0; padding: 8px 12px; border-left: 3px solid var(--theme-color); color: var(--text-secondary); background: var(--bg-soft); overflow-wrap: anywhere; }
.comment-reply-context p { margin: 4px 0 0; font-size: 13px; }

.comment-editor__preview {
  min-height: 96px;
  padding: 12px 14px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-soft);
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}

.comment-editor__preview :deep(p) {
  margin: 0 0 8px;
}

.comment-editor__preview :deep(p:last-child) {
  margin-bottom: 0;
}

.comment-editor__preview :deep(img) {
  max-width: min(100%, 360px);
  border-radius: 8px;
}

.comment-editor__preview :deep(pre) {
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(125, 125, 125, 0.12);
  overflow: auto;
}

.comment-editor__preview :deep(code) {
  font-size: 13px;
}

.comment-editor__preview :deep(blockquote) {
  margin: 0 0 8px;
  padding-left: 12px;
  border-left: 3px solid var(--theme-color);
  color: var(--text-secondary);
}

.comment-editor__preview-empty {
  color: var(--text-muted);
}

:deep(.comment-editor__tool-group button.is-active) {
  color: var(--theme-color);
}


.comment-closed-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 18px;
  margin-bottom: 4px;
  border: 1px dashed var(--border-color);
  border-radius: 14px;
  background: var(--bg-soft);
  color: var(--text-secondary);
  font-size: 14px;
}

.comment-closed-banner__icon {
  flex-shrink: 0;
  color: var(--text-muted);
}

.comment-guest-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}

.comment-guest-fields__input {
  flex: 1 1 160px;
  min-width: 0;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-soft);
  color: var(--text-primary);
  font-size: 14px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.comment-guest-fields__input::placeholder {
  color: var(--text-muted);
}

.comment-guest-fields__input:focus {
  outline: none;
  border-color: var(--theme-color);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--theme-color) 16%, transparent);
}

/* 仅在后台启用评论回复邮件后渲染；未启用时整块不出现，不在前台明文提示。 */
.comment-notify {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-right: auto;
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
}
.comment-notify input {
  width: 15px;
  height: 15px;
  accent-color: var(--theme-color);
}
.comment-notify-hint {
  margin: 8px 0 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.4;
}

.aero-comment__head {
  align-items: center;
}

.aero-comment__body {
  padding-right: 0;
}

.aero-comment__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
  padding-left: 12px;
}

.aero-comment__reply-toggle {
  position: static;
}


.aero-comment__delete,
.aero-comment__report {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.aero-comment__delete:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.12);
}

.aero-comment__report:hover {
  color: #fa8c16;
  background: rgba(250, 140, 22, 0.12);
}

.aero-comment__delete:disabled,
.aero-comment__report:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.aero-comment__body {
  position: relative;
}


.comment-auth-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.comment-auth-actions__hint {
  color: var(--text-muted);
  font-size: 13px;
}


.comment-thread-card {
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--bg-soft);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.comment-thread-card + .comment-thread-card {
  margin-top: 14px;
}

.comment-thread-card:hover {
  border-color: color-mix(in srgb, var(--theme-color) 32%, var(--border-color));
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.08);
}

.comment-thread-card--mine {
  border-color: color-mix(in srgb, var(--theme-color) 40%, var(--border-color));
  background: color-mix(in srgb, var(--theme-color) 6%, var(--bg-soft));
}

.aero-comment__name--mine {
  color: var(--theme-color) !important;
  font-weight: 700;
}

.aero-comment__badge--mine {
  background: color-mix(in srgb, var(--theme-color) 16%, transparent);
  color: var(--theme-color);
  border: 1px solid color-mix(in srgb, var(--theme-color) 28%, transparent);
}

.aero-comment--mine .aero-comment__avatar {
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--theme-color) 45%, transparent);
}

.aero-comment__content .comment-image {
  display: block;
  max-width: min(100%, 420px);
  max-height: 320px;
  margin-top: 10px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  object-fit: cover;
  cursor: zoom-in;
}

.comment-editor__textarea:focus {
  outline: none;
  border-color: var(--theme-color);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--theme-color) 14%, transparent);
}

.comment-editor__submit {
  transition: transform 0.1s ease, filter 0.2s ease;
}

.comment-editor__submit:hover {
  filter: brightness(1.06);
}

.comment-editor__submit:active {
  transform: scale(0.97);
}

.comment-load-more {
  display: flex;
  justify-content: center;
  padding: 8px 0 4px;
}

.comment-load-more__button {
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 20px;
  border: 1px solid var(--comment-card-border);
  border-radius: 999px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 13.5px;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;
}

.comment-load-more__button:hover:not(:disabled) {
  border-color: var(--theme-color);
  color: var(--theme-color);
  background: color-mix(in srgb, var(--theme-color) 8%, var(--bg-card));
}

.comment-load-more__button:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.comment-load-more__icon.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 640px) {
  .comment-guest-fields__input {
    flex-basis: 100%;
  }
}
</style>
