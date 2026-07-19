// 开机校验登录态：Pinia persist 会把 accessToken 存到 localStorage，令牌在后端过期后
// 前端仍残留该字符串，导致 isAuthenticated 恒为 true——收藏点了没反应、抽屉一直显示「退出登录」。
// 这里在客户端启动时用 /auth/me 校验一次：401(令牌已失效)则清空本地登录态，回到未登录 UI；
// 成功则顺带用最新信息刷新 session（相当于无感续期的配套）。
export default defineNuxtPlugin(async () => {
  const userStore = useStore.user();
  if (!userStore.accessToken) {
    return;
  }
  const { fetchCurrentUser } = useApiClient();
  try {
    const response = await fetchCurrentUser();
    if (response.success && response.data) {
      // 令牌有效：用后端最新资料刷新本地会话（用户名/昵称/角色可能已变更）。
      userStore.setSession({
        userId: response.data.userId,
        username: response.data.username,
        displayName: response.data.displayName,
        roleCode: response.data.roleCode,
        tokenName: response.data.tokenName || userStore.tokenName,
        // /auth/me 不重发 token，沿用本地已存的。
        tokenValue: userStore.accessToken
      });
    } else {
      userStore.clearSession();
    }
  } catch (error) {
    // 仅 401/403(令牌失效)才清理；网络抖动等其它错误保留登录态，避免误登出。
    const status = (error as { response?: { status?: number }; status?: number })?.response?.status
      || (error as { status?: number })?.status
      || 0;
    if (status === 401 || status === 403) {
      userStore.clearSession();
    }
  }
});
