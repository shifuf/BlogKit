const { request } = require('../utils/request');

function fetchSiteSettings() {
  return request('/site-settings');
}

function fetchArticles(page = 0, size = 10, filters = {}) {
  return request('/articles', {
    data: {
      page,
      size,
      ...filters
    },
    auth: true
  });
}

function fetchArticleDetail(id) {
  // 带 auth 让后端识别登录态，使登录可见/指定用户/管理员门控生效。
  return request(`/articles/${id}`, { auth: true });
}

// 密码可见文章解锁：校验访问密码后返回完整正文。
function unlockArticle(id, password) {
  return request(`/articles/${id}/unlock`, {
    method: 'POST',
    data: { password },
    auth: true
  });
}

function fetchRelatedArticles(id, size = 4) {
  return request(`/articles/${id}/related`, {
    data: { size },
    auth: true
  });
}

function fetchArticleNeighbors(id) {
  return request(`/articles/${id}/neighbors`, { auth: true });
}

function fetchCategories() {
  return request('/categories');
}

function fetchTags() {
  return request('/tags');
}

function fetchComments(articleId, page = 0, size = 20) {
  return request(`/articles/${articleId}/comments`, {
    data: { page, size },
    auth: true
  });
}

function fetchCommentReplies(commentId, page = 0, size = 10) {
  return request(`/comments/${commentId}/replies`, { data: { page, size }, auth: true });
}

function login(payload) {
  return request('/auth/login', {
    method: 'POST',
    data: payload
  });
}

function loginWithWechatMiniProgram(payload) {
  return request('/auth/wechat-mini-program/login', {
    method: 'POST',
    data: payload
  });
}

function register(payload) {
  return request('/auth/register', {
    method: 'POST',
    data: payload
  });
}

function logout() {
  return request('/auth/logout', {
    method: 'POST',
    auth: true
  });
}

function fetchProfile() {
  return request('/auth/profile', {
    auth: true
  });
}

function fetchMyFavoriteArticles(page = 0, size = 5) {
  return request('/favorites/articles', {
    data: { page, size },
    auth: true
  });
}

function fetchMyComments(page = 0, size = 5) {
  return request('/comments/me', {
    data: { page, size },
    auth: true
  });
}

function likeArticle(articleId) {
  return request(`/articles/${articleId}/like`, {
    method: 'POST',
    auth: true
  });
}

function fetchFavoriteStatus(articleId) {
  return request(`/favorites/articles/${articleId}`, {
    auth: true
  });
}

function toggleFavorite(articleId) {
  return request(`/favorites/articles/${articleId}`, {
    method: 'POST',
    auth: true
  });
}

function createComment(articleId, content, parentId) {
  return request('/comments', {
    method: 'POST',
    data: { articleId, parentId, content },
    auth: true
  });
}

function toggleCommentLike(commentId) {
  return request(`/comments/${commentId}/like`, {
    method: 'POST',
    auth: true
  });
}

function deleteComment(commentId) {
  return request(`/comments/${commentId}`, {
    method: 'DELETE',
    auth: true
  });
}

// 凭密封 token 换取真实下载信息；带 auth 让后端校验登录/评论门控。
function openDownload(token) {
  return request('/downloads/open', {
    method: 'POST',
    data: { token },
    auth: true
  });
}

// ===== 动态（dynamics）=====
// 动态全部公开，无需登录；点赞/评论用 guestKey 归属。列表不分页，一次返回全部可见动态。
function fetchDynamics(page = 0, size = 10, guestKey) {
  return request('/dynamics', { data: { page, size, guestKey } });
}

function fetchDynamicReplies(id, rootId, page = 0, size = 10) {
  return request(`/dynamics/${id}/comments/${rootId}/replies`, { data: { page, size } });
}

function fetchDynamicLikeStatus(id, guestKey) {
  return request(`/dynamics/${id}/like`, {
    data: guestKey ? { guestKey } : {}
  });
}

function toggleDynamicLike(id, guestKey) {
  return request(`/dynamics/${id}/like`, {
    method: 'POST',
    data: guestKey ? { guestKey } : {}
  });
}

function fetchDynamicComments(id, page = 0, size = 10) {
  return request(`/dynamics/${id}/comments`, {
    data: { page, size }
  });
}

// parentId 传被回复评论 id（后端扁平化挂到其顶级评论）；authorName 登录时可空，游客默认「游客」。
function postDynamicComment(id, payload) {
  return request(`/dynamics/${id}/comments`, {
    method: 'POST',
    data: payload
  });
}

module.exports = {
  fetchSiteSettings,
  fetchArticles,
  fetchArticleDetail,
  unlockArticle,
  fetchRelatedArticles,
  fetchArticleNeighbors,
  fetchCategories,
  fetchTags,
  fetchComments,
  fetchCommentReplies,
  login,
  loginWithWechatMiniProgram,
  register,
  logout,
  fetchProfile,
  fetchMyFavoriteArticles,
  fetchMyComments,
  likeArticle,
  fetchFavoriteStatus,
  toggleFavorite,
  createComment,
  toggleCommentLike,
  deleteComment,
  openDownload,
  fetchDynamics,
  fetchDynamicReplies,
  fetchDynamicLikeStatus,
  toggleDynamicLike,
  fetchDynamicComments,
  postDynamicComment
};
