<script setup lang="ts">
import { ElMessage } from 'element-plus';
const user = useStore.user();
const config = useRuntimeConfig();
const api = useApiClient();
const state = ref<{email: string; emailVerified: boolean; thirdPartyBound: boolean}>({email:'',emailVerified:false,thirdPartyBound:false});
const sessions = ref<any[]>([]);
const subscription = ref({ email: '', emailVerified: false, confirmed: false, articleUpdates: false });
const preferenceLoading = ref(false);
const loadPreference = async () => {
  const response = await $fetch<{ data: typeof subscription.value }>('/subscriptions/email/preferences', { baseURL: config.public.apiBaseUrl, headers: headers() });
  subscription.value = response.data;
};
const savePreference = async () => {
  preferenceLoading.value = true;
  try {
    const response = await $fetch<{ data: typeof subscription.value }>('/subscriptions/email/preferences', { baseURL: config.public.apiBaseUrl, method: 'PUT', headers: headers(), body: { articleUpdates: subscription.value.articleUpdates } });
    subscription.value = response.data;
    ElMessage.success('邮件偏好已保存');
  } catch (e: any) { error.value = e?.data?.message || '偏好保存失败'; await loadPreference(); }
  finally { preferenceLoading.value = false; }
};
const email = ref(''), password = ref(''), totpCode = ref(''), busy = ref(false), error = ref('');
const headers = () => ({ [user.tokenName || 'Authorization']: `Bearer ${user.accessToken}` });
const request = <T>(path: string, method: 'GET'|'POST' = 'GET', body?: object) => $fetch<{data:T}>(path, { baseURL: config.public.apiBaseUrl, method, body, headers: headers() });
const run = async (action: () => Promise<void>) => {busy.value=true;error.value='';try{await action()}catch(e:any){error.value=e?.data?.message||'操作失败，请稍后重试'}finally{busy.value=false}};
const load = async () => {const [s,d] = await Promise.all([request<typeof state.value>('/auth/recovery/status'),request<any[]>('/auth/sessions')]);state.value=s.data;email.value=s.data.email||'';sessions.value=d.data;await loadPreference()};
const verifyEmail = () => run(async()=>{await request('/auth/recovery/email','POST',{email:email.value,password:password.value,totpCode:totpCode.value});password.value='';totpCode.value='';ElMessage.success('验证邮件已发送，请在 30 分钟内打开邮件链接')});
const revoke = (id?:number) => run(async()=>{await request(id?`/auth/sessions/${id}/revoke`:'/auth/sessions/revoke-others','POST');await load();ElMessage.success('设备已退出')});
const reauth = async()=>{await request('/auth/reauthenticate','POST',{username:user.username,password:password.value,totpCode:totpCode.value});password.value='';totpCode.value=''};
const bind = () => run(async()=>{await reauth();const response=await api.fetchOAuthAuthorizeUrl({callbackOrigin:window.location.origin,redirect:'/security',bind:true});if(response.data.authorizationUrl)window.location.assign(response.data.authorizationUrl)});
const unbind = () => run(async()=>{await reauth();await request('/auth/oauth/unbind','POST');await load();ElMessage.success('已解绑')});
onMounted(()=>{if(user.isAuthenticated)void run(load)});
</script>
<template>
  <main class="account-security">
    <NuxtLink to="/profile">← 个人中心</NuxtLink><h1>账号安全</h1>
    <p v-if="!user.isAuthenticated">请先通过站点登录入口登录。</p>
    <template v-else>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <section><h2>文章订阅偏好</h2><p>邮箱：{{ subscription.email || '未设置' }} · {{ subscription.confirmed ? '已确认订阅' : '未确认订阅' }}</p><p v-if="!subscription.emailVerified">请先完成下方邮箱验证，再开启新文章邮件。</p><el-switch v-model="subscription.articleUpdates" :disabled="!subscription.emailVerified || preferenceLoading" @change="savePreference" /> 接收公开新文章邮件<p>评论回复邮件按每条评论的通知选项发送；安全通知保持启用。</p></section>
      <section><h2>验证与恢复</h2><p>验证邮箱后可用邮件找回密码。更换邮箱或绑定身份需要再次确认密码；仅第三方登录的账号可在重新登录后五分钟内验证恢复邮箱。</p>
        <p>当前邮箱：{{ state.email || '未设置' }} · {{state.emailVerified?'已验证':'未验证'}}</p>
        <el-form label-position="top"><el-form-item label="邮箱"><el-input v-model="email" type="email" autocomplete="email" /></el-form-item>
          <el-form-item label="当前密码"><el-input v-model="password" type="password" show-password autocomplete="current-password" /></el-form-item>
          <el-form-item label="二步验证码（管理员已启用时必填，可用恢复码）"><el-input v-model="totpCode" autocomplete="one-time-code" /></el-form-item>
          <el-space wrap><el-button :loading="busy" type="primary" @click="verifyEmail">发送验证邮件</el-button><el-button :loading="busy" @click="bind">绑定 OAuth 账号</el-button><el-button v-if="state.thirdPartyBound" :loading="busy" @click="unbind">解绑第三方</el-button></el-space>
        </el-form><p>第三方账号建议先设置并验证恢复邮箱，避免失去登录方式。</p>
      </section>
      <section><h2>登录设备</h2><el-space><el-button :loading="busy" @click="run(load)">刷新</el-button><el-popconfirm title="退出其他所有设备？" @confirm="revoke()"><template #reference><el-button>退出其他设备</el-button></template></el-popconfirm></el-space>
        <ul><li v-for="s in sessions" :key="s.id"><strong>{{s.deviceName}}</strong> {{s.current?'（当前设备）':''}} · {{s.active?'有效':'已退出'}}<p>{{s.ipAddress}} · 最近活动 {{new Date(s.lastSeenAt).toLocaleString()}}</p><el-button v-if="s.active&&!s.current" :loading="busy" @click="revoke(s.id)">退出该设备</el-button></li></ul>
      </section>
    </template>
  </main>
</template>
<style scoped>.account-security{max-width:900px;margin:40px auto;padding:24px}.account-security section{margin:24px 0;padding:24px;border:1px solid #ddd;border-radius:12px}.account-security li{margin:16px 0}.account-security h1{font-size:2rem}.account-security h2{font-size:1.3rem;margin-bottom:12px}</style>
