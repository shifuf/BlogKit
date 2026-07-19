<template>
  <main class="oauth-callback">
    <el-card class="oauth-callback__card" shadow="never">
      <div v-if="status === 'loading'" class="oauth-callback__state">
        <el-icon class="is-loading" size="34"><Loading /></el-icon>
        <h1>正在完成 OAuth 登录</h1>
        <p>请稍候，系统正在校验授权结果。</p>
      </div>
      <div v-else class="oauth-callback__state">
        <h1>登录失败</h1>
        <p>{{ errorMessage }}</p>
        <el-button type="primary" @click="goLogin">重新登录</el-button>
      </div>
    </el-card>
  </main>
</template>

<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
const router = useRouter();
const route = useRoute();
const userStore = useStore.user();
const {
  loginWithOAuthCode
} = useApiClient();
const {
  openLogin
} = useLoginModal();
const status = ref<'loading' | 'error'>('loading');
const errorMessage = ref('OAuth 登录失败，请重新登录。');
const safeRedirectPath = (value?: string) => {
  const adminBase = (useRuntimeConfig().public.adminBasePath || '/admin').replace(/\/+$/, '') || '/admin';
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith(adminBase) || value.startsWith('/manage')) {
    return '/';
  }
  return value;
};
const getApiErrorMessage = (error: unknown, fallback: string) => {
  const maybeError = error as {
    data?: {
      message?: string;
      msg?: string;
    } | string;
    response?: {
      _data?: {
        message?: string;
        msg?: string;
      } | string;
      data?: {
        message?: string;
        msg?: string;
      } | string;
    };
    message?: string;
  };
  const data = maybeError?.data || maybeError?.response?._data || maybeError?.response?.data;
  if (data && typeof data === 'object') {
    return data.message || data.msg || fallback;
  }
  if (typeof data === 'string' && data.trim()) {
    return data.trim();
  }
  return maybeError?.message || fallback;
};
const goLogin = async () => {
  await router.replace('/');
  openLogin();
};
onMounted(async () => {
  const error = Array.isArray(route.query.error) ? route.query.error[0] : route.query.error;
  if (error) {
    status.value = 'error';
    errorMessage.value = '你已取消授权或认证中心拒绝了本次登录。';
    return;
  }
  const code = Array.isArray(route.query.code) ? route.query.code[0] : route.query.code;
  const state = Array.isArray(route.query.state) ? route.query.state[0] : route.query.state;
  if (typeof code !== 'string' || typeof state !== 'string' || !code || !state) {
    status.value = 'error';
    errorMessage.value = 'OAuth 回调参数不完整，请重新登录。';
    return;
  }
  try {
    const response = await loginWithOAuthCode({
      code,
      state
    });
    if (response.success) {
      userStore.setSession(response.data.session);
      ElMessage.success(response.message || '登录成功');
      await router.replace(safeRedirectPath(response.data.redirectPath));
      return;
    }
  } catch (error) {
    status.value = 'error';
    errorMessage.value = getApiErrorMessage(error, 'OAuth 登录校验失败，请重新发起登录。');
  }
});
</script>

<style lang="scss" scoped>
.oauth-callback {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at 70% 15%, rgba(31, 122, 140, 0.2), transparent 26%),
    linear-gradient(135deg, #f6f9ff 0%, #eef4ff 48%, #f8fbff 100%);
}

.oauth-callback__card {
  width: min(480px, 100%);
  border: 1px solid #d9e6f7;
  border-radius: 22px;
  text-align: center;
}

.oauth-callback__state {
  padding: 24px 10px;

  h1 {
    margin: 16px 0 8px;
    color: #102a43;
    font-size: 1.45rem;
  }

  p {
    margin-bottom: 18px;
    color: #486581;
  }
}
</style>
