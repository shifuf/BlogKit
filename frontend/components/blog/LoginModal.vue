<script setup lang="ts">
import { ElMessage } from 'element-plus';
const props = withDefaults(defineProps<{
  modelValue?: boolean;
}>(), {
  modelValue: false
});
const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
});
const userStore = useStore.user();
const route = useRoute();
const {
  login,
  register,
  fetchOAuthAuthorizeUrl
} = useApiClient();
const {
  siteSettings,
  loadSiteSettings
} = useSiteSettings();
const isRegister = ref(false);
const showLoginPassword = ref(false);
const showRegisterPassword = ref(false);
const isSubmitting = ref(false);
const isStartingOAuth = ref(false);
const accountInput = ref<HTMLInputElement | null>(null);
const loginForm = reactive({
  account: '',
  password: '',
  remember: true
});
const registerForm = reactive({
  username: '',
  displayName: '',
  password: ''
});
const close = () => {
  visible.value = false;
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
  if (data && typeof data === 'object') return data.message || data.msg || fallback;
  if (typeof data === 'string' && data.trim()) return data.trim();
  return maybeError?.message || fallback;
};
const submitLogin = async () => {
  if (!loginForm.account.trim() || !loginForm.password.trim()) {
    ElMessage.warning('请输入账号和密码');
    return;
  }
  isSubmitting.value = true;
  try {
    const response = await login({
      username: loginForm.account.trim(),
      password: loginForm.password
    });
    if (response.success) {
      userStore.setSession(response.data);
      ElMessage.success(response.message || '登录成功');
      close();
    }
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '登录失败，请检查账号密码'));
  } finally {
    isSubmitting.value = false;
  }
};
const submitRegister = async () => {
  if (!siteSettings.value.allowRegister) {
    ElMessage.warning('当前站点未开放注册');
    return;
  }
  if (!registerForm.username.trim() || !registerForm.displayName.trim() || !registerForm.password.trim()) {
    ElMessage.warning('请填写用户名、昵称和密码');
    return;
  }
  isSubmitting.value = true;
  try {
    const response = await register({
      username: registerForm.username.trim(),
      displayName: registerForm.displayName.trim(),
      password: registerForm.password
    });
    if (response.success) {
      userStore.setSession(response.data);
      ElMessage.success(response.message || '注册成功');
      close();
    }
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '注册失败，请稍后重试'));
  } finally {
    isSubmitting.value = false;
  }
};
const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && visible.value) {
    close();
  }
};
const safeRedirectPath = () => {
  const path = route.fullPath;
  if (!path || !path.startsWith('/') || path.startsWith('//') || path.startsWith('/admin') || path.startsWith('/manage') || path.startsWith('/login')) {
    return '/';
  }
  return path;
};
const sanitizeOAuthAuthorizationUrl = (authorizationUrl: string) => {
  try {
    const url = new URL(authorizationUrl);
    url.searchParams.delete('scope');
    return url.toString();
  } catch {
    return authorizationUrl.replace(/([?&])scope=[^&]*&?/, '$1').replace(/[?&]$/, '');
  }
};
const startOAuthLogin = async () => {
  if (!import.meta.client) return;
  isStartingOAuth.value = true;
  try {
    const response = await fetchOAuthAuthorizeUrl({
      callbackOrigin: window.location.origin,
      redirect: safeRedirectPath()
    });
    if (response.success && response.data.authorizationUrl) {
      window.location.href = sanitizeOAuthAuthorizationUrl(response.data.authorizationUrl);
      return;
    }
    ElMessage.error('OAuth 登录暂不可用');
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, 'OAuth 登录暂不可用'));
  } finally {
    isStartingOAuth.value = false;
  }
};
watch(() => props.modelValue, async value => {
  if (import.meta.client) {
    document.body.classList.toggle('login-modal-open', value);
  }
  if (value) {
    await nextTick();
    accountInput.value?.focus();
  } else {
    isRegister.value = false;
  }
}, {
  immediate: true
});
onMounted(() => {
  void loadSiteSettings();
  window.addEventListener('keydown', onKeydown);
});
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown);
  if (import.meta.client) {
    document.body.classList.remove('login-modal-open');
  }
});
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="login-modal-overlay" role="presentation" @click.self="close">
      <div class="login-modal" role="dialog" aria-modal="true" aria-labelledby="login-modal-title">
        <div class="login-modal__inner" :class="{ 'login-modal__inner--flip': isRegister }">
          <section class="login-modal__face login-modal__face--front">
            <button class="login-modal__close" type="button" aria-label="关闭登录弹窗" @click="close">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </button>

            <h2 id="login-modal-title" class="login-modal__title">登录</h2>
            <p class="login-modal__desc">登录后体验更多功能</p>

            <form class="login-modal__form" @submit.prevent="submitLogin">
              <label class="login-modal__field">
                <span class="login-modal__label">账号</span>
                <span class="login-modal__input-wrap">
                  <input ref="accountInput" v-model="loginForm.account" class="login-modal__input" type="text" placeholder="请输入账号或邮箱" autocomplete="username">
                </span>
              </label>

              <label class="login-modal__field">
                <span class="login-modal__label">密码</span>
                <span class="login-modal__input-wrap">
                  <input v-model="loginForm.password" class="login-modal__input" :type="showLoginPassword ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password">
                  <button class="login-modal__eye" type="button" aria-label="显示或隐藏密码" @click="showLoginPassword = !showLoginPassword">
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
                      <circle cx="12" cy="12" r="3" />
                    </svg>
                  </button>
                </span>
              </label>

              <div class="login-modal__options">
                <label class="login-modal__remember">
                  <input v-model="loginForm.remember" type="checkbox">
                  <span>记住我</span>
                </label>
                <NuxtLink class="login-modal__link" to="/account-recovery" @click="close">忘记密码？</NuxtLink>
              </div>

              <button class="login-modal__submit" type="submit" :disabled="isSubmitting">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
                  <path d="m10 17 5-5-5-5" />
                  <path d="M15 12H3" />
                </svg>
                {{ isSubmitting ? '登录中...' : '登录' }}
              </button>
            </form>

            <div v-if="siteSettings.oauthLoginEnabled" class="login-modal__oauth">
              <div class="login-modal__divider"><span>或</span></div>
              <button class="login-modal__oauth-btn" type="button" :disabled="isStartingOAuth" @click="startOAuthLogin">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M21 2v6h-6" />
                  <path d="M3 12a9 9 0 0 1 15-6.7L21 8" />
                  <path d="M3 22v-6h6" />
                  <path d="M21 12a9 9 0 0 1-15 6.7L3 16" />
                </svg>
                {{ isStartingOAuth ? '正在跳转...' : '使用 OAuth 登录' }}
              </button>
            </div>

            <div class="login-modal__footer">
              还没有账号？
              <button type="button" @click="isRegister = true">立即注册</button>
            </div>
          </section>

          <section class="login-modal__face login-modal__face--back">
            <button class="login-modal__close" type="button" aria-label="关闭登录弹窗" @click="close">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </button>

            <h2 class="login-modal__title">注册</h2>
            <p class="login-modal__desc">创建账号后可继续完善资料</p>

            <form class="login-modal__form" @submit.prevent="submitRegister">
              <label class="login-modal__field">
                <span class="login-modal__label">用户名</span>
                <span class="login-modal__input-wrap">
                  <input v-model="registerForm.username" class="login-modal__input" type="text" placeholder="请输入用户名" autocomplete="username">
                </span>
              </label>

              <label class="login-modal__field">
                <span class="login-modal__label">昵称</span>
                <span class="login-modal__input-wrap">
                  <input v-model="registerForm.displayName" class="login-modal__input" type="text" placeholder="请输入昵称" autocomplete="nickname">
                </span>
              </label>

              <label class="login-modal__field">
                <span class="login-modal__label">密码</span>
                <span class="login-modal__input-wrap">
                  <input v-model="registerForm.password" class="login-modal__input" :type="showRegisterPassword ? 'text' : 'password'" placeholder="请输入密码" autocomplete="new-password">
                  <button class="login-modal__eye" type="button" aria-label="显示或隐藏密码" @click="showRegisterPassword = !showRegisterPassword">
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
                      <circle cx="12" cy="12" r="3" />
                    </svg>
                  </button>
                </span>
              </label>

              <button class="login-modal__submit" type="submit" :disabled="isSubmitting || !siteSettings.allowRegister">
                {{ isSubmitting ? '注册中...' : siteSettings.allowRegister ? '注册' : '暂未开放注册' }}
              </button>
            </form>

            <div class="login-modal__footer">
              已有账号？
              <button type="button" @click="isRegister = false">返回登录</button>
            </div>
          </section>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped lang="scss">
.login-modal__oauth {
  margin-top: 18px;
}

.login-modal__divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  color: #94a3b8;
  font-size: 13px;
}

.login-modal__divider::before,
.login-modal__divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: rgba(148, 163, 184, 0.3);
}

.login-modal__oauth-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 44px;
  border: 1px solid var(--theme-color, #409eff);
  border-radius: 12px;
  background: transparent;
  color: var(--theme-color, #409eff);
  font-weight: 700;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, transform 0.1s ease;
}

.login-modal__oauth-btn:hover {
  background: var(--theme-color, #2a8aed);
  color: #fff;
}

.login-modal__oauth-btn:active {
  transform: scale(0.98);
}

.login-modal__oauth-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.login-modal__oauth-btn svg {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
</style>
