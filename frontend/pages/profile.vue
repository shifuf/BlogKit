<template>
  <main class="profile-page">
    <div class="profile-shell">
      <section class="profile-banner">
        <div class="profile-banner__decor" aria-hidden="true"></div>
        <div class="profile-banner__head">
          <div class="profile-banner__avatar">
            <img v-if="realProfileAvatar" :src="realProfileAvatar" :alt="displayName" />
            <span v-else class="profile-banner__avatar-initial">{{ profileInitial }}</span>
          </div>
          <div class="profile-banner__identity">
            <p class="profile-banner__eyebrow">个人中心</p>
            <h1>{{ displayName }}</h1>
            <p v-if="showUsernameHandle" class="profile-banner__username">@{{ profile?.username || userStore.username }}</p>
            <div class="profile-banner__badges">
              <span class="profile-badge profile-badge--role">
                <ShieldCheck :size="14" aria-hidden="true" />
                {{ roleLabel }}
              </span>
              <span v-if="profile?.createdAt" class="profile-badge">
                <CalendarDays :size="14" aria-hidden="true" />
                加入于 {{ formatDateTime(profile.createdAt) }}
              </span>
            </div>
          </div>
        </div>

        <div v-if="profile" class="profile-banner__stats">
          <div v-for="stat in statCards" :key="stat.label" class="profile-stat">
            <span class="profile-stat__icon" :style="{ color: stat.tint, background: `color-mix(in srgb, ${stat.tint} 14%, transparent)` }">
              <component :is="stat.icon" :size="18" aria-hidden="true" />
            </span>
            <strong>{{ stat.value }}</strong>
            <span>{{ stat.label }}</span>
          </div>
        </div>
      </section>

      <el-skeleton v-if="loading" class="profile-skeleton" :rows="6" animated />

      <div v-else-if="profile" class="profile-body">
        <aside class="profile-nav">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            class="profile-nav__item"
            :class="{ active: activeTab === tab.key }"
            type="button"
            @click="activeTab = tab.key"
          >
            <component :is="tab.icon" :size="18" aria-hidden="true" />
            <span>{{ tab.label }}</span>
            <span v-if="tab.count !== undefined" class="profile-nav__count">{{ tab.count }}</span>
          </button>
        </aside>

        <div class="profile-content">
          <template v-if="activeTab === 'profile'">
            <section class="profile-panel">
              <h2><UserRound :size="18" aria-hidden="true" /> 编辑资料</h2>
              <el-form label-position="top">
                <el-form-item label="显示名称">
                  <el-input v-model="profileForm.displayName" maxlength="64" show-word-limit />
                </el-form-item>
                <el-form-item label="头像">
                  <div class="profile-avatar-upload">
                    <div class="profile-avatar-upload__preview">
                      <img v-if="profileForm.avatarUrl" :src="profileForm.avatarUrl" :alt="displayName" />
                      <span v-else>{{ displayName.slice(0, 1).toUpperCase() }}</span>
                    </div>
                    <div class="profile-avatar-upload__actions">
                      <el-upload
                        :show-file-list="false"
                        accept="image/jpeg,image/png,image/gif,image/webp"
                        :http-request="uploadAvatar"
                        :before-upload="beforeAvatarUpload"
                      >
                        <el-button :loading="uploadingAvatar">
                          <UploadCloud :size="15" aria-hidden="true" />
                          上传头像
                        </el-button>
                      </el-upload>
                      <el-button v-if="profileForm.avatarUrl" plain @click="profileForm.avatarUrl = ''">清除</el-button>
                    </div>
                  </div>
                </el-form-item>
                <el-button type="primary" :loading="savingProfile" @click="submitProfile">
                  <Save :size="15" aria-hidden="true" />
                  保存资料
                </el-button>
              </el-form>
            </section>

            <section class="profile-panel">
              <h2><KeyRound :size="18" aria-hidden="true" /> 修改密码</h2>
              <el-form label-position="top">
                <el-form-item label="原密码">
                  <el-input v-model="passwordForm.oldPassword" type="password" show-password />
                </el-form-item>
                <el-form-item label="新密码">
                  <el-input v-model="passwordForm.newPassword" type="password" show-password />
                </el-form-item>
                <el-form-item label="确认新密码">
                  <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
                </el-form-item>
                <el-button type="primary" :loading="changingPassword" @click="submitPassword">修改密码</el-button>
              </el-form>
            </section>

            <section class="profile-panel">
              <h2><ShieldCheck :size="18" aria-hidden="true" /> 账号信息</h2>
              <dl class="profile-details">
                <div><dt>用户 ID</dt><dd>{{ profile.userId }}</dd></div>
                <div><dt>角色</dt><dd>{{ roleLabel }}</dd></div>
                <div><dt>注册时间</dt><dd>{{ formatDateTime(profile.createdAt) }}</dd></div>
                <div><dt>资料更新时间</dt><dd>{{ formatDateTime(profile.updatedAt) }}</dd></div>
              </dl>
            </section>
          </template>

          <section v-else-if="activeTab === 'favorites'" class="profile-panel">
            <div class="profile-panel__header">
              <h2><Bookmark :size="18" aria-hidden="true" /> 我的收藏</h2>
              <span>{{ favoriteTotalItems }} 篇</span>
            </div>
            <el-skeleton v-if="favoriteLoading" :rows="3" animated />
            <div v-else-if="favoriteArticles.length" class="profile-favorites">
              <NuxtLink
                v-for="article in favoriteArticles"
                :key="article.id"
                class="profile-favorite"
                :to="`/article/${article.id}`"
              >
                <strong>{{ article.title }}</strong>
                <span class="profile-favorite__meta">{{ article.categoryName }} · {{ formatRelativeTime(article.publishedAt) }}</span>
                <p>{{ article.summary }}</p>
              </NuxtLink>
            </div>
            <el-empty v-else description="还没有收藏文章" />
            <el-pagination
              v-if="favoriteTotalItems > favoritePageSize"
              class="profile-pagination"
              background
              layout="prev, pager, next"
              :current-page="favoritePage"
              :page-size="favoritePageSize"
              :total="favoriteTotalItems"
              @current-change="changeFavoritePage"
            />
          </section>

          <section v-else-if="activeTab === 'comments'" class="profile-panel">
            <div class="profile-panel__header">
              <h2><MessageSquare :size="18" aria-hidden="true" /> 我的评论</h2>
              <span>{{ commentTotalItems }} 条</span>
            </div>
            <el-skeleton v-if="commentLoading" :rows="3" animated />
            <div v-else-if="myComments.length" class="profile-comments">
              <article v-for="comment in myComments" :key="comment.id" class="profile-comment">
                <div class="profile-comment__main">
                  <NuxtLink class="profile-comment__article" :to="`/article/${comment.articleId}`">
                    {{ comment.articleTitle }}
                  </NuxtLink>
                  <p>{{ comment.content }}</p>
                  <span class="profile-comment__meta">
                    {{ formatRelativeTime(comment.createdAt) }}
                    <template v-if="comment.parentAuthorName"> · 回复 {{ comment.parentAuthorName }}</template>
                    · {{ comment.likeCount }} 赞 · {{ comment.replyCount }} 回复
                  </span>
                </div>
                <el-button
                  type="danger"
                  plain
                  size="small"
                  :loading="activeDeletingCommentId === comment.id"
                  @click="removeMyComment(comment)"
                >
                  <Trash2 :size="14" aria-hidden="true" />
                  删除
                </el-button>
              </article>
            </div>
            <el-empty v-else description="还没有发布评论" />
            <el-pagination
              v-if="commentTotalItems > commentPageSize"
              class="profile-pagination"
              background
              layout="prev, pager, next"
              :current-page="commentPage"
              :page-size="commentPageSize"
              :total="commentTotalItems"
              @current-change="changeCommentPage"
            />
          </section>
        </div>
      </div>

      <el-empty v-else :description="profileErrorMessage || '未获取到个人资料'" />
    </div>
  </main>
</template>

<script setup lang="ts">
import { Bookmark, CalendarDays, Heart, KeyRound, MessageSquare, Save, ShieldCheck, ThumbsUp, Trash2, UploadCloud, UserRound } from '@lucide/vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import type { ArticleSummary, UserCommentSummary, UserProfile } from '~/types/blog';
import { buildAvatarUrl } from '~/utils/blog-adapter';
import { formatDateTime, formatRelativeTime } from '~/utils/date';
import { cropImage } from '~/utils/imageCrop'
import { compressImage } from '~/utils/imageCompress'
const userStore = useStore.user();
const {
  changePassword,
  deleteComment,
  fetchMyComments,
  fetchMyFavoriteArticles,
  fetchUserProfile,
  updateUserProfile,
  uploadProfileAvatar
} = useApiClient();
const {
  openLogin
} = useLoginModal();
const profile = ref<UserProfile | null>(null);
const favoriteArticles = ref<ArticleSummary[]>([]);
const myComments = ref<UserCommentSummary[]>([]);
const loading = ref(false);
const favoriteLoading = ref(false);
const commentLoading = ref(false);
const profileErrorMessage = ref('');
const savingProfile = ref(false);
const changingPassword = ref(false);
const uploadingAvatar = ref(false);
const activeDeletingCommentId = ref<number | null>(null);
const favoritePage = ref(1);
const favoritePageSize = 5;
const favoriteTotalItems = ref(0);
const commentPage = ref(1);
const commentPageSize = 5;
const commentTotalItems = ref(0);
const activeTab = ref<'profile' | 'favorites' | 'comments'>('profile');
const profileForm = reactive({
  displayName: '',
  avatarUrl: ''
});
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});
const displayName = computed(() => profile.value?.displayName || userStore.displayName || userStore.username || '用户');
const showUsernameHandle = computed(() => (profile.value?.username || userStore.username || '').toLowerCase() !== 'admin');
// 真实头像（用户已设/已上传）；无则留空，banner 回退为「昵称首字」头像，与下方上传预览一致。
const realProfileAvatar = computed(() => buildAvatarUrl(profile.value?.avatarUrl || profileForm.avatarUrl || ''));
const profileInitial = computed(() => (displayName.value || '用户').slice(0, 1).toUpperCase());
const roleLabel = computed(() => String(profile.value?.roleCode || userStore.roleCode || '').toUpperCase() === 'ADMIN' ? '管理员' : '普通用户');
const statCards = computed(() => [{
  label: '评论',
  value: profile.value?.commentCount ?? 0,
  icon: MessageSquare,
  tint: '#409eff'
}, {
  label: '文章点赞',
  value: profile.value?.articleLikeCount ?? 0,
  icon: Heart,
  tint: '#ef4444'
}, {
  label: '收藏',
  value: profile.value?.favoriteCount ?? 0,
  icon: Bookmark,
  tint: '#f59e0b'
}, {
  label: '评论点赞',
  value: profile.value?.commentLikeCount ?? 0,
  icon: ThumbsUp,
  tint: '#10b981'
}]);
const tabs = computed(() => [{
  key: 'profile' as const,
  label: '资料与安全',
  icon: UserRound,
  count: undefined as number | undefined
}, {
  key: 'favorites' as const,
  label: '我的收藏',
  icon: Bookmark,
  count: favoriteTotalItems.value
}, {
  key: 'comments' as const,
  label: '我的评论',
  icon: MessageSquare,
  count: commentTotalItems.value
}]);
const apiErrorMessage = (error: unknown, fallback: string) => {
  const apiError = error as {
    data?: {
      message?: string;
    };
    response?: {
      _data?: {
        message?: string;
      };
      status?: number;
    };
    status?: number;
    statusCode?: number;
    message?: string;
  };
  return apiError?.data?.message || apiError?.response?._data?.message || apiError?.message || fallback;
};
const apiErrorStatus = (error: unknown) => {
  const apiError = error as {
    response?: {
      status?: number;
    };
    status?: number;
    statusCode?: number;
  };
  return apiError?.response?.status || apiError?.status || apiError?.statusCode || 0;
};
const handleAuthError = async (error: unknown) => {
  const status = apiErrorStatus(error);
  if (status === 401 || status === 403) {
    userStore.clearSession();
    ElMessage.warning('登录状态已失效，请重新登录');
    await navigateTo('/');
    openLogin();
    return true;
  }
  return false;
};
const syncProfileForm = () => {
  profileForm.displayName = profile.value?.displayName || '';
  profileForm.avatarUrl = profile.value?.avatarUrl || '';
};
const loadProfile = async () => {
  profileErrorMessage.value = '';
  try {
    const response = await fetchUserProfile();
    profile.value = response.success ? response.data : null;
    if (!response.success) {
      profileErrorMessage.value = response.message || '未获取到个人资料';
    }
    syncProfileForm();
  } catch (error) {
    profile.value = null;
    profileErrorMessage.value = apiErrorMessage(error, '个人资料加载失败');
    if (!(await handleAuthError(error))) {
      ElMessage.error(profileErrorMessage.value);
    }
  }
};
const loadFavorites = async () => {
  favoriteLoading.value = true;
  try {
    const response = await fetchMyFavoriteArticles(favoritePage.value - 1, favoritePageSize);
    favoriteArticles.value = response.success ? response.data.items : [];
    favoriteTotalItems.value = response.success ? response.data.totalItems : 0;
  } catch (error) {
    favoriteArticles.value = [];
    favoriteTotalItems.value = 0;
    if (!(await handleAuthError(error))) {
      ElMessage.error(apiErrorMessage(error, '收藏文章加载失败'));
    }
  } finally {
    favoriteLoading.value = false;
  }
};
const loadMyComments = async () => {
  commentLoading.value = true;
  try {
    const response = await fetchMyComments(commentPage.value - 1, commentPageSize);
    myComments.value = response.success ? response.data.items : [];
    commentTotalItems.value = response.success ? response.data.totalItems : 0;
  } catch (error) {
    myComments.value = [];
    commentTotalItems.value = 0;
    if (!(await handleAuthError(error))) {
      ElMessage.error(apiErrorMessage(error, '我的评论加载失败'));
    }
  } finally {
    commentLoading.value = false;
  }
};
const submitProfile = async () => {
  if (!profileForm.displayName.trim()) {
    ElMessage.warning('显示名称不能为空');
    return;
  }
  savingProfile.value = true;
  try {
    const response = await updateUserProfile({
      displayName: profileForm.displayName.trim(),
      avatarUrl: profileForm.avatarUrl.trim()
    });
    if (response.success) {
      profile.value = response.data;
      userStore.updateProfile({
        displayName: response.data.displayName
      });
      syncProfileForm();
      ElMessage.success('个人资料已更新');
    }
  } catch (error) {
    if (!(await handleAuthError(error))) {
      ElMessage.error(apiErrorMessage(error, '个人资料更新失败'));
    }
  } finally {
    savingProfile.value = false;
  }
};
const beforeAvatarUpload = (file: File) => {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件');
    return false;
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像大小不能超过 5MB');
    return false;
  }
  return true;
};
const uploadAvatar = async (options: any) => {
  uploadingAvatar.value = true;
  try {
    const cropped = await cropImage(options.file as File, { defaultPreset: '1:1', title: '裁剪头像' });
    if (!cropped) {
      options.onError?.(new Error('cancelled'));
      return;
    }
    const compressed = await compressImage(cropped, { maxWidth: 800, maxHeight: 800, quality: 0.85 });
    const response = await uploadProfileAvatar(compressed);
    if (response.success) {
      profile.value = response.data;
      userStore.updateProfile({
        displayName: response.data.displayName
      });
      syncProfileForm();
      ElMessage.success('头像已上传');
      options.onSuccess?.(response.data);
    }
  } catch (error) {
    if (!(await handleAuthError(error))) {
      ElMessage.error(apiErrorMessage(error, '头像上传失败'));
    }
    options.onError?.(error);
  } finally {
    uploadingAvatar.value = false;
  }
};
const submitPassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写原密码和新密码');
    return;
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位');
    return;
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致');
    return;
  }
  changingPassword.value = true;
  try {
    const response = await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    });
    if (response.success) {
      passwordForm.oldPassword = '';
      passwordForm.newPassword = '';
      passwordForm.confirmPassword = '';
      ElMessage.success('密码已修改');
    }
  } catch (error) {
    if (!(await handleAuthError(error))) {
      ElMessage.error(apiErrorMessage(error, '密码修改失败'));
    }
  } finally {
    changingPassword.value = false;
  }
};
const changeFavoritePage = (nextPage: number) => {
  favoritePage.value = nextPage;
  loadFavorites();
};
const changeCommentPage = (nextPage: number) => {
  commentPage.value = nextPage;
  loadMyComments();
};
const removeMyComment = async (comment: UserCommentSummary) => {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确认删除这条评论吗？', '删除评论', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    });
  } catch {
    return;
  }
  activeDeletingCommentId.value = comment.id;
  try {
    const response = await deleteComment(comment.id);
    if (response.success) {
      ElMessage.success('评论已删除');
      if (myComments.value.length === 1 && commentPage.value > 1) {
        commentPage.value -= 1;
      }
      await Promise.all([loadProfile(), loadMyComments()]);
    }
  } catch (error) {
    if (!(await handleAuthError(error))) {
      ElMessage.error(apiErrorMessage(error, '评论删除失败'));
    }
  } finally {
    activeDeletingCommentId.value = null;
  }
};
// SSR 预取登录用户的个人页数据：token 走 cookie 持久化(pinia-persistedstate/nuxt 默认)，
// 服务端可读，已登录则在服务端就把资料/收藏/评论拉好，消除进入时的「空白→填充」闪烁；
// 未登录或服务端拿不到 token 时返回 null，交由下方 onMounted 客户端兜底(含重定向)。
const { data: ssrProfileData } = await useAsyncData('profile-page-data', async () => {
  if (!userStore.isAuthenticated) return null;
  try {
    const [profileRes, favRes, commentRes] = await Promise.all([
      fetchUserProfile(),
      fetchMyFavoriteArticles(0, favoritePageSize),
      fetchMyComments(0, commentPageSize)
    ]);
    return { profileRes, favRes, commentRes };
  } catch {
    // SSR 阶段任何失败都静默降级到客户端加载，避免首屏 500。
    return null;
  }
});
// 把 SSR 预取结果写入响应式状态：服务端渲染与客户端水合都会执行（客户端从 payload 取，不二次请求）。
if (ssrProfileData.value) {
  const { profileRes, favRes, commentRes } = ssrProfileData.value;
  if (profileRes?.success) {
    profile.value = profileRes.data;
    syncProfileForm();
  }
  if (favRes?.success) {
    favoriteArticles.value = favRes.data.items;
    favoriteTotalItems.value = favRes.data.totalItems;
  }
  if (commentRes?.success) {
    myComments.value = commentRes.data.items;
    commentTotalItems.value = commentRes.data.totalItems;
  }
}
onMounted(async () => {
  if (!userStore.isAuthenticated) {
    ElMessage.warning('请先登录');
    await navigateTo('/');
    openLogin();
    return;
  }
  // SSR 已预取则跳过，避免重复请求；否则客户端兜底加载。
  if (profile.value) return;
  loading.value = true;
  try {
    await loadProfile();
    if (profile.value) {
      await Promise.all([loadFavorites(), loadMyComments()]);
    }
  } finally {
    loading.value = false;
  }
});
</script>

<style lang="scss" scoped>
.profile-page {
  min-height: calc(100vh - 80px);
  padding: 32px 16px 56px;
  background:
    radial-gradient(120% 60% at 50% 0%, color-mix(in srgb, var(--theme-color) 12%, var(--bg-page)), var(--bg-page) 60%),
    var(--bg-page);
}

.profile-shell {
  max-width: 1080px;
  margin: 0 auto;
}


.profile-banner {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 20px;
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.profile-banner__decor {
  position: absolute;
  inset: 0 0 auto 0;
  height: 150px;
  background:
    radial-gradient(80% 140% at 18% 0%, color-mix(in srgb, var(--theme-color) 60%, transparent), transparent 70%),
    linear-gradient(120deg, color-mix(in srgb, var(--theme-color) 80%, #1d4ed8), color-mix(in srgb, #14b8a6 70%, var(--theme-color)));
}

.profile-banner__head {
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 20px;
  padding: 82px 28px 26px;
}

.profile-banner__avatar {
  flex-shrink: 0;
  width: 104px;
  height: 104px;
  border-radius: 24px;
  overflow: hidden;
  border: 4px solid var(--bg-card);
  background: var(--bg-soft);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.18);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .profile-banner__avatar-initial {
    width: 100%;
    height: 100%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, rgba(64, 158, 255, 0.22), rgba(79, 193, 178, 0.26));
    color: var(--theme-color);
    font-size: 44px;
    font-weight: 700;
  }
}

.profile-banner__identity {
  padding-bottom: 6px;

  h1 {
    margin: 4px 0 0;
    font-size: 26px;
    line-height: 1.2;
  }
}

.profile-banner__eyebrow {
  margin: 0;
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  text-shadow: 0 1px 6px rgba(15, 23, 42, 0.35);
}

.profile-banner__username {
  margin: 6px 0 8px;
  color: var(--text-muted);
  font-size: 14px;
}

.profile-banner__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.profile-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 11px;
  border-radius: 999px;
  background: var(--bg-soft);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  font-size: 12.5px;
}

.profile-badge--role {
  color: var(--theme-color);
  border-color: color-mix(in srgb, var(--theme-color) 40%, var(--border-color));
  background: color-mix(in srgb, var(--theme-color) 12%, var(--bg-card));
  font-weight: 700;
}

.profile-banner__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding: 8px 28px 26px;
}

.profile-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 14px 16px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--bg-soft);
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  strong {
    font-size: 22px;
    line-height: 1.1;
  }

  span:last-child {
    color: var(--text-muted);
    font-size: 13px;
  }
}

.profile-stat:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.1);
}

.profile-stat__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin-bottom: 6px;
  border-radius: 10px;
}

.profile-skeleton {
  margin-top: 18px;
  padding: 24px;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: var(--bg-card);
}


.profile-body {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.profile-nav {
  position: sticky;
  top: 90px;
  align-self: start;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.profile-nav__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 13px;
  border: 0;
  border-radius: 11px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 14.5px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;

  span:first-of-type {
    flex: 1;
    text-align: left;
  }
}

.profile-nav__item:hover {
  background: var(--bg-soft);
  color: var(--text-primary);
}

.profile-nav__item.active {
  background: color-mix(in srgb, var(--theme-color) 14%, var(--bg-card));
  color: var(--theme-color);
}

.profile-nav__count {
  min-width: 22px;
  padding: 1px 7px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--theme-color) 16%, var(--bg-soft));
  color: var(--theme-color);
  font-size: 12px;
  text-align: center;
}

.profile-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.profile-panel {
  padding: 22px 24px;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: var(--bg-card);
  box-shadow: var(--shadow-card);

  h2 {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0 0 18px;
    font-size: 17px;
    font-weight: 800;

    svg {
      color: var(--theme-color);
    }
  }
}

.profile-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;

  h2 {
    margin: 0;
  }

  span {
    color: var(--text-muted);
    font-size: 13px;
  }
}

.profile-avatar-upload {
  display: flex;
  align-items: center;
  gap: 16px;
}

.profile-avatar-upload__preview {
  width: 72px;
  height: 72px;
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-soft);
  border: 1px solid var(--border-color);
  color: var(--text-muted);
  font-size: 26px;
  font-weight: 700;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.profile-avatar-upload__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.profile-details {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin: 0;

  > div {
    padding: 12px 14px;
    border: 1px solid var(--border-color);
    border-radius: 12px;
    background: var(--bg-soft);
  }

  dt {
    color: var(--text-muted);
    font-size: 12.5px;
  }

  dd {
    margin: 4px 0 0;
    font-weight: 600;
    word-break: break-all;
  }
}


.profile-favorites {
  display: grid;
  gap: 12px;
}

.profile-favorite {
  display: block;
  padding: 16px 18px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--bg-soft);
  color: inherit;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;

  strong {
    display: block;
    font-size: 15.5px;
  }

  p {
    margin: 8px 0 0;
    color: var(--text-secondary);
    font-size: 13.5px;
    line-height: 1.7;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    overflow: hidden;
  }
}

.profile-favorite__meta {
  display: block;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12.5px;
}

.profile-favorite:hover {
  border-color: color-mix(in srgb, var(--theme-color) 38%, var(--border-color));
  transform: translateY(-3px);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.1);
}


.profile-comments {
  display: grid;
  gap: 12px;
}

.profile-comment {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--bg-soft);
}

.profile-comment__main {
  min-width: 0;

  p {
    margin: 8px 0;
    line-height: 1.7;
    word-break: break-word;
  }
}

.profile-comment__article {
  font-weight: 700;
  color: var(--theme-color);
}

.profile-comment__meta {
  color: var(--text-muted);
  font-size: 12.5px;
}

.profile-pagination {
  margin-top: 18px;
  justify-content: center;
}

@media (max-width: 860px) {
  .profile-banner__stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .profile-body {
    grid-template-columns: 1fr;
  }

  .profile-nav {
    position: static;
    flex-direction: row;
    overflow-x: auto;

    .profile-nav__count {
      display: none;
    }
  }

  .profile-details {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .profile-banner__head {
    flex-direction: column;
    align-items: flex-start;
    padding-top: 64px;
  }

  .profile-comment {
    flex-direction: column;
  }
}
</style>
