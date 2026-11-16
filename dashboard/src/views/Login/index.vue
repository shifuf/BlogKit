<template>
  <div class="login-container">
    <div class="login-bg" aria-hidden="true">
      <div class="login-bg__shape login-bg__shape--1" />
      <div class="login-bg__shape login-bg__shape--2" />
      <div class="login-bg__shape login-bg__shape--3" />
    </div>

    <div class="login-card">
      <div class="login-brand">
        <div class="login-brand__logo" style="display: none;">
          <img :src="brandLogo" alt="我的博客 Logo" />
        </div>
        <h1 class="login-title">我的博客 后台管理</h1>
        <p class="login-subtitle">记录技术、项目与生活</p>
      </div>

      <a-form
        :model="loginForm"
        name="login-form"
        @finish="handleLogin"
        :rules="rules"
      >
        <a-form-item name="username">
          <a-input
            v-model:value="loginForm.username"
            placeholder="用户名"
            size="large"
          >
            <template #prefix><user-outlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item name="password">
          <a-input-password
            v-model:value="loginForm.password"
            placeholder="密码"
            size="large"
          >
            <template #prefix><lock-outlined /></template>
          </a-input-password>
        </a-form-item>
        <a-form-item v-if="totpRequired" name="totpCode">
          <a-input v-model:value="loginForm.totpCode" placeholder="动态验证码（启用二步验证后必填）" size="large" maxlength="6">
            <template #prefix><safety-certificate-outlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            class="login-button"
            size="large"
            :loading="loading"
          >
            登录
          </a-button>
        </a-form-item>
      </a-form>

      <p class="login-footer">请使用管理员账号登录</p>
    </div>
  </div>
</template>

<script setup>
import { nextTick, reactive, ref } from 'vue';
import { brandLogo } from '@/utils/brand';
import { message } from 'ant-design-vue';
import { UserOutlined, LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue';
import { login } from '@/api/Auth';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/modules/user';

const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const totpRequired = ref(false);
const loginForm = reactive({
  username: '',
  password: '',
  totpCode: ''
});
const rules = {
  username: [{
    required: true,
    message: '请输入用户名',
    trigger: 'blur'
  }],
  password: [{
    required: true,
    message: '请输入密码',
    trigger: 'blur'
  }]
};

const handleLogin = async values => {
  loading.value = true;
  try {
    const session = await login({
      username: values.username,
      password: values.password,
      totpCode: values.totpCode || undefined
    });
    if (session.roleCode !== 'ADMIN') {
      message.error('您没有管理员权限，无法登录');
      return;
    }
    userStore.setSession(session);
    message.success('登录成功');
    router.push('/');
  } catch (error) {
    if (error?.code === 'TOTP_REQUIRED' || error?.message === 'TOTP_REQUIRED') {
      totpRequired.value = true;
      loginForm.totpCode = '';
      await nextTick();
      message.info('该管理员已启用二步验证，请输入动态验证码');
    }
  } finally {
    loading.value = false;
  }
};
</script>

<style lang="scss" scoped>
.login-container {
  position: relative;
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 24px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

.login-bg__shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.45;
}

.login-bg__shape--1 {
  width: 520px;
  height: 520px;
  top: -160px;
  right: -120px;
  background: linear-gradient(120deg, #409eff, #3f5cff);
}

.login-bg__shape--2 {
  width: 420px;
  height: 420px;
  bottom: -120px;
  left: -100px;
  background: linear-gradient(120deg, #00c6ff, #0072ff);
}

.login-bg__shape--3 {
  width: 300px;
  height: 300px;
  bottom: 120px;
  right: 15%;
  background: linear-gradient(120deg, #ff7eb3, #ff758c);
  opacity: 0.25;
}

.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  padding: 42px 36px 32px;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(12px);
}

.login-brand {
  text-align: left;
  margin-bottom: 32px;
}

.login-brand__logo {
  display: none !important;
}

.login-title {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 800;
  color: #1f2937;
  text-align: left;
}

.login-subtitle {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
  text-align: left;
}

.login-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 10px;
  box-shadow: 0 8px 22px rgba(64, 94, 255, 0.28);
}

.login-footer {
  margin: 20px 0 0;
  text-align: left;
  color: #9ca3af;
  font-size: 13px;
}

:deep(.ant-input-affix-wrapper) {
  border-radius: 10px;
  padding: 10px 14px;
}

:deep(.ant-input) {
  font-size: 15px;
  text-align: left !important;
}

:deep(.ant-input::placeholder) {
  text-align: left !important;
}

:deep(.ant-form-item-label) {
  text-align: left !important;
}

:deep(.ant-form-item-label > label) {
  justify-content: flex-start !important;
}
</style>
