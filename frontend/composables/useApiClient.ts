import type { ApiResponse, PageResponse } from '~/types/api';
import type { ArticleDetail, ArticleSummary, CategorySummary, CommentSummary, DownloadResource, DynamicPost, DynamicComment, EngagementState, FriendLinkSummary, LoginPayload, OAuthAuthorizePayload, OAuthCallbackPayload, OAuthLoginPayload, RegisterPayload, SiteSettings, TagSummary, ArticleSort, PasswordChangePayload, UserCommentSummary, UserProfile, UserProfileUpdatePayload, UserSession, UserNotification } from '~/types/blog';
export const useApiClient = () => {
  const config = useRuntimeConfig();
  const userStore = useStore.user();
  const authHeaders = () => {
    if (!userStore.accessToken) {
      return {};
    }
    return {
      [userStore.tokenName || 'Authorization']: `Bearer ${userStore.accessToken}`
    };
  };
  const fetchArticleSummaries = (page = 0, size = 10, filters: {
    categorySlug?: string;
    tagSlug?: string;
    keyword?: string;
    sort?: ArticleSort;
  } = {}, signal?: AbortSignal) => {
    return $fetch<ApiResponse<PageResponse<ArticleSummary>>>('/articles', {
      baseURL: config.public.apiBaseUrl,
      query: {
        page,
        size,
        ...filters
      },
      headers: authHeaders(),
      signal
    });
  };
  const fetchArticleDetail = (id: number) => {
    return $fetch<ApiResponse<ArticleDetail>>(`/articles/${id}`, {
      baseURL: config.public.apiBaseUrl,
      // 携带登录态，使登录可见/指定用户/管理员的访问控制门控对当前用户生效。
      headers: authHeaders()
    });
  };
  const fetchArticlePreview = (token: string) => {
    return $fetch<ApiResponse<ArticleDetail>>(`/articles/preview/${token}`, {
      baseURL: config.public.apiBaseUrl
    });
  };
  const unlockArticle = (id: number, password: string) => {
    return $fetch<ApiResponse<ArticleDetail>>(`/articles/${id}/unlock`, {
      method: 'POST',
      baseURL: config.public.apiBaseUrl,
      body: {
        password
      },
      headers: authHeaders()
    });
  };
  const fetchRelatedArticles = (id: number, size = 4) => {
    return $fetch<ApiResponse<ArticleSummary[]>>(`/articles/${id}/related`, {
      baseURL: config.public.apiBaseUrl,
      query: {
        size
      },
      headers: authHeaders()
    });
  };
  const fetchArticleArchive = (year?: number) => {
    return $fetch<ApiResponse<Array<{ year: number; months: Array<{ month: number; articles: Array<{ id: number; title: string; publishedAt: string }> }> }>>>('/articles/archive', {
      baseURL: config.public.apiBaseUrl,
      query: year ? { year } : undefined
    });
  };
  const fetchArticleArchiveYears = () => {
    return $fetch<ApiResponse<number[]>>('/articles/archive/years', {
      baseURL: config.public.apiBaseUrl
    });
  };
  const recordArticleShare = (id: number, payload: { shareType?: string; guestKey?: string }) => {
    return $fetch<ApiResponse<number>>(`/articles/${id}/share`, {
      method: 'POST',
      baseURL: config.public.apiBaseUrl,
      body: payload
    });
  };
  const fetchCategories = () => {
    return $fetch<ApiResponse<CategorySummary[]>>('/categories', {
      baseURL: config.public.apiBaseUrl
    });
  };
  const fetchTags = () => {
    return $fetch<ApiResponse<TagSummary[]>>('/tags', {
      baseURL: config.public.apiBaseUrl
    });
  };
  const fetchSiteSettings = () => {
    return $fetch<ApiResponse<SiteSettings>>('/site-settings', {
      baseURL: config.public.apiBaseUrl
    });
  };
  const fetchDynamics = () => {
    return $fetch<ApiResponse<DynamicPost[]>>('/dynamics', {
      baseURL: config.public.apiBaseUrl
    });
  };
  const fetchVisitorLocation = () => {
    return $fetch<ApiResponse<{ region: string }>>('/meta/visitor-location', {
      baseURL: config.public.apiBaseUrl
    });
  };
  const fetchDynamicLikeStatus = (id: number, guestKey: string) => {
    return $fetch<ApiResponse<{ liked: boolean; likeCount: number }>>(`/dynamics/${id}/like`, {
      baseURL: config.public.apiBaseUrl,
      query: { guestKey }
    });
  };
  const toggleDynamicLike = (id: number, guestKey: string) => {
    return $fetch<ApiResponse<{ liked: boolean; likeCount: number }>>(`/dynamics/${id}/like`, {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      body: { guestKey }
    });
  };
  const fetchDynamicComments = (id: number, page = 0, size = 10) => {
    return $fetch<ApiResponse<PageResponse<DynamicComment>>>(`/dynamics/${id}/comments`, {
      baseURL: config.public.apiBaseUrl,
      query: { page, size }
    });
  };
  const postDynamicComment = (id: number, payload: { authorName: string; content: string; guestKey: string; parentId?: number | null }) => {
    return $fetch<ApiResponse<DynamicComment>>(`/dynamics/${id}/comments`, {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      body: payload
    });
  };
  const fetchFriendLinks = () => {
    return $fetch<ApiResponse<FriendLinkSummary[]>>('/friend-links', {
      baseURL: config.public.apiBaseUrl
    });
  };
  const fetchFriendLinkCaptcha = () => {
    return $fetch<ApiResponse<{ captchaId: string; imageData: string; expiresAt?: string }>>('/friend-links/captcha', {
      baseURL: config.public.apiBaseUrl
    });
  };
  const applyFriendLink = (payload: {
    name: string;
    url: string;
    description?: string;
    email?: string;
    captchaId: string;
    captchaCode: string;
  }) => {
    return $fetch<ApiResponse<{ id: number; status: string; backlinkFound?: boolean }>>('/friend-links/apply', {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      body: payload
    });
  };
  const login = (payload: LoginPayload) => {
    return $fetch<ApiResponse<UserSession>>('/auth/login', {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      body: payload
    });
  };
  const fetchOAuthAuthorizeUrl = (payload: {
    callbackOrigin: string;
    redirect?: string;
  }) => {
    return $fetch<ApiResponse<OAuthAuthorizePayload>>('/auth/oauth/authorize', {
      baseURL: config.public.apiBaseUrl,
      query: payload
    });
  };
  const loginWithOAuthCode = (payload: OAuthCallbackPayload) => {
    return $fetch<ApiResponse<OAuthLoginPayload>>('/auth/oauth/callback', {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      body: payload
    });
  };
  const register = (payload: RegisterPayload) => {
    return $fetch<ApiResponse<UserSession>>('/auth/register', {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      body: payload
    });
  };
  const logout = () => {
    return $fetch<ApiResponse<null>>('/auth/logout', {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      headers: authHeaders()
    });
  };
  // 校验当前令牌是否仍有效：命中后端 /auth/me（需登录）。令牌过期会返回 401，
  // 用于开机时清理 localStorage 里已失效但仍残留的登录态（否则 isAuthenticated 恒为 true）。
  const fetchCurrentUser = () => {
    return $fetch<ApiResponse<UserSession>>('/auth/me', {
      baseURL: config.public.apiBaseUrl,
      headers: authHeaders()
    });
  };
  const fetchUserProfile = () => {
    return $fetch<ApiResponse<UserProfile>>('/auth/profile', {
      baseURL: config.public.apiBaseUrl,
      headers: authHeaders()
    });
  };
  const updateUserProfile = (payload: UserProfileUpdatePayload) => {
    return $fetch<ApiResponse<UserProfile>>('/auth/profile', {
      baseURL: config.public.apiBaseUrl,
      method: 'PUT',
      body: payload,
      headers: authHeaders()
    });
  };
  const uploadProfileAvatar = (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return $fetch<ApiResponse<UserProfile>>('/auth/profile/avatar', {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      body: formData,
      headers: authHeaders()
    });
  };
  const changePassword = (payload: PasswordChangePayload) => {
    return $fetch<ApiResponse<null>>('/auth/password', {
      baseURL: config.public.apiBaseUrl,
      method: 'PUT',
      body: payload,
      headers: authHeaders()
    });
  };
  const fetchMyFavoriteArticles = (page = 0, size = 5) => {
    return $fetch<ApiResponse<PageResponse<ArticleSummary>>>('/favorites/articles', {
      baseURL: config.public.apiBaseUrl,
      query: {
        page,
        size
      },
      headers: authHeaders()
    });
  };
  const fetchMyComments = (page = 0, size = 5) => {
    return $fetch<ApiResponse<PageResponse<UserCommentSummary>>>('/comments/me', {
      baseURL: config.public.apiBaseUrl,
      query: {
        page,
        size
      },
      headers: authHeaders()
    });
  };
  const fetchComments = (articleId: number, page = 0, size = 20) => {
    return $fetch<ApiResponse<PageResponse<CommentSummary>>>(`/articles/${articleId}/comments`, {
      baseURL: config.public.apiBaseUrl,
      query: {
        page,
        size
      },
      headers: authHeaders()
    });
  };
  const likeArticle = (articleId: number, guestKey?: string) => {
    return $fetch<ApiResponse<EngagementState>>(`/articles/${articleId}/like`, {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      query: guestKey ? { guestKey } : {},
      headers: authHeaders()
    });
  };
  // 公开互动状态：游客带 guestKey 查询自己的点赞态，登录用户返回点赞+收藏态。
  const fetchEngagementStatus = (articleId: number, guestKey?: string) => {
    return $fetch<ApiResponse<EngagementState>>(`/articles/${articleId}/engagement`, {
      baseURL: config.public.apiBaseUrl,
      query: guestKey ? { guestKey } : {},
      headers: authHeaders()
    });
  };
  const fetchFavoriteStatus = (articleId: number) => {
    return $fetch<ApiResponse<EngagementState>>(`/favorites/articles/${articleId}`, {
      baseURL: config.public.apiBaseUrl,
      headers: authHeaders()
    });
  };
  const toggleFavorite = (articleId: number) => {
    return $fetch<ApiResponse<EngagementState>>(`/favorites/articles/${articleId}`, {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      headers: authHeaders()
    });
  };
  const createComment = (articleId: number, content: string, parentId?: number, guest?: {
    guestName?: string;
    guestEmail?: string;
    guestWebsite?: string;
  }, notifyOnReply?: boolean) => {
    return $fetch<ApiResponse<CommentSummary>>('/comments', {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      body: {
        articleId,
        parentId,
        content,
        notifyOnReply: Boolean(notifyOnReply),
        ...(guest || {})
      },
      headers: authHeaders()
    });
  };
  const toggleCommentLike = (commentId: number) => {
    return $fetch<ApiResponse<CommentSummary>>(`/comments/${commentId}/like`, {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      headers: authHeaders()
    });
  };
  const deleteComment = (commentId: number) => {
    return $fetch<ApiResponse<null>>(`/comments/${commentId}`, {
      baseURL: config.public.apiBaseUrl,
      method: 'DELETE',
      headers: authHeaders()
    });
  };
  const reportComment = (commentId: number) => {
    return $fetch<ApiResponse<CommentSummary>>(`/comments/${commentId}/report`, {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      headers: authHeaders()
    });
  };
  const uploadCommentImage = (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return $fetch<ApiResponse<{
      fileName: string;
      url: string;
      size: number;
    }>>('/comments/upload-image', {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      body: formData,
      headers: authHeaders()
    });
  };
  const openDownload = (token: string) => {
    return $fetch<ApiResponse<DownloadResource>>('/downloads/open', {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      body: {
        token
      },
      headers: authHeaders()
    });
  };
  const fetchNotifications = (page = 0, size = 20) => $fetch<ApiResponse<PageResponse<UserNotification>>>('/notifications', { baseURL: config.public.apiBaseUrl, query: { page, size }, headers: authHeaders() });
  const fetchNotificationUnreadCount = () => $fetch<ApiResponse<{ count: number }>>('/notifications/unread-count', { baseURL: config.public.apiBaseUrl, headers: authHeaders() });
  const markNotificationRead = (id: number) => $fetch<ApiResponse<null>>(`/notifications/${id}/read`, { baseURL: config.public.apiBaseUrl, method: 'POST', headers: authHeaders() });
  const markAllNotificationsRead = () => $fetch<ApiResponse<null>>('/notifications/read-all', { baseURL: config.public.apiBaseUrl, method: 'POST', headers: authHeaders() });
  return {
    login,
    fetchOAuthAuthorizeUrl,
    loginWithOAuthCode,
    register,
    logout,
    fetchCurrentUser,
    fetchUserProfile,
    updateUserProfile,
    uploadProfileAvatar,
    changePassword,
    fetchMyFavoriteArticles,
    fetchMyComments,
    fetchArticleSummaries,
    fetchArticleDetail,
    fetchArticlePreview,
    recordArticleShare,
    unlockArticle,
    fetchRelatedArticles,
    fetchArticleArchive,
    fetchArticleArchiveYears,
    fetchCategories,
    fetchTags,
    fetchSiteSettings,
    fetchDynamics,
    fetchVisitorLocation,
    fetchDynamicLikeStatus,
    toggleDynamicLike,
    fetchDynamicComments,
    postDynamicComment,
    fetchFriendLinks,
    fetchFriendLinkCaptcha,
    applyFriendLink,
    fetchComments,
    createComment,
    toggleCommentLike,
    deleteComment,
    reportComment,
    uploadCommentImage,
    openDownload,
    likeArticle,
    fetchEngagementStatus,
    fetchFavoriteStatus,
    toggleFavorite,
    fetchNotifications,
    fetchNotificationUnreadCount,
    markNotificationRead,
    markAllNotificationsRead
  };
};
