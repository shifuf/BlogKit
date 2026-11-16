<template>
  <div class="security-page">
    <a-row :gutter="[16,16]">
      <a-col :xs="24" :xl="10">
        <a-card title="管理员二步验证" :bordered="false">
          <a-alert :type="totpEnabled ? 'success' : 'warning'" :message="totpEnabled ? '二步验证已启用' : '二步验证未启用'" show-icon />
          <div class="actions">
            <a-button v-if="!totpEnabled" type="primary" @click="startSetup">生成密钥</a-button>
            <template v-else><a-space><a-button @click="startSetup">更换验证器</a-button><a-button @click="generateRecovery">生成恢复码</a-button><a-button danger @click="disableVisible=true">关闭二步验证</a-button></a-space></template>
          </div>
          <div v-if="setup" class="setup-box">
            <p>请在 Microsoft Authenticator、Google Authenticator、1Password 等应用中添加 TOTP 账户。</p>
            <a-typography-text copyable code>{{ setup.secret }}</a-typography-text>
            <a-textarea :value="setup.provisioningUri" readonly :rows="3" class="uri" />
            <a-input v-model:value="confirmCode" maxlength="6" placeholder="输入应用生成的 6 位验证码" />
            <a-button type="primary" :loading="saving" @click="confirmSetup">验证并启用</a-button>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :xl="14">
        <a-card title="登录设备与会话" :bordered="false">
          <template #extra><a-space><a-button @click="loadSessions">刷新</a-button><a-popconfirm title="确定退出其它所有设备？" @confirm="revokeOthers"><a-button danger>退出其它设备</a-button></a-popconfirm></a-space></template>
          <a-table row-key="id" :data-source="sessions" :columns="columns" :loading="loading" :pagination="false">
            <template #bodyCell="{column,record}">
              <template v-if="column.dataIndex==='deviceName'"><strong>{{ record.deviceName }}</strong><a-tag v-if="record.current" color="green">当前设备</a-tag><div class="muted">{{ record.ipAddress }}</div></template>
              <template v-if="column.dataIndex==='active'"><a-tag :color="record.active?'blue':'default'">{{ record.active?'有效':'已退出' }}</a-tag></template>
              <template v-if="column.dataIndex==='action'"><a-popconfirm v-if="record.active&&!record.current" title="确定退出该设备？" @confirm="revoke(record.id)"><a>退出</a></a-popconfirm><span v-else>-</span></template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>
    <a-modal v-model:open="proofVisible" title="验证现有二步验证" @ok="submitProof"><a-input v-model:value="proofCode" placeholder="当前验证码或一次性恢复码" autocomplete="one-time-code" /></a-modal>
    <a-modal v-model:open="recoveryVisible" title="保存恢复码" :footer="null"><a-alert message="恢复码仅显示本次，每码只能使用一次。请离线保存；重新生成会作废旧码。" type="warning" show-icon /><pre style="white-space:pre-wrap">{{ recovery.join('\n') }}</pre><a-button @click="copyRecovery">复制全部恢复码</a-button></a-modal>
    <a-modal v-model:open="disableVisible" title="关闭二步验证" ok-text="确认关闭" @ok="disableTotpNow"><a-input v-model:value="disableCode" placeholder="当前动态验证码或恢复码" /></a-modal>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'; import { message } from 'ant-design-vue'; import { confirmTotp, disableTotp, getTotpStatus, listSessions, revokeOtherSessions, revokeSession, setupTotp, recoveryCodes } from '@/api/Auth'; import { formatDateTime } from '@/utils/date';
const totpEnabled=ref(false),setup=ref(null),confirmCode=ref(''),disableCode=ref(''),disableVisible=ref(false),saving=ref(false),loading=ref(false),sessions=ref([]);
const columns=[{title:'设备',dataIndex:'deviceName'},{title:'首次登录',dataIndex:'createdAt',customRender:({text})=>formatDateTime(text),width:170},{title:'最近活动',dataIndex:'lastSeenAt',customRender:({text})=>formatDateTime(text),width:170},{title:'状态',dataIndex:'active',width:90},{title:'操作',dataIndex:'action',width:70}];
const loadStatus=async()=>{totpEnabled.value=Boolean((await getTotpStatus()).enabled)}; const loadSessions=async()=>{loading.value=true;try{sessions.value=await listSessions()}finally{loading.value=false}};
const proofVisible=ref(false),proofCode=ref(''),proofAction=ref(''),recovery=ref([]),recoveryVisible=ref(false);
const performSetup=async code=>{setup.value=await setupTotp(code);confirmCode.value=''};
const startSetup=async()=>{if(totpEnabled.value){proofAction.value='setup';proofCode.value='';proofVisible.value=true}else await performSetup()};
const generateRecovery=()=>{proofAction.value='recovery';proofCode.value='';proofVisible.value=true};
const submitProof=async()=>{if(proofAction.value==='setup')await performSetup(proofCode.value);else{recovery.value=await recoveryCodes(proofCode.value);recoveryVisible.value=true}proofVisible.value=false;proofCode.value=''};
const copyRecovery=async()=>{try{await navigator.clipboard.writeText(recovery.value.join('\n'));message.success('已复制，请安全保存')}catch{message.warning('请手动选择并保存恢复码')}}; const confirmSetup=async()=>{if(!/^\d{6}$/.test(confirmCode.value)){message.warning('请输入 6 位验证码');return}saving.value=true;try{await confirmTotp(confirmCode.value);message.success('二步验证已启用');setup.value=null;await loadStatus()}finally{saving.value=false}};
const disableTotpNow=async()=>{await disableTotp(disableCode.value);disableVisible.value=false;disableCode.value='';message.success('二步验证已关闭');await loadStatus()};
const revoke=async id=>{await revokeSession(id);message.success('设备已退出');await loadSessions()};const revokeOthers=async()=>{await revokeOtherSessions();message.success('其它设备已退出');await loadSessions()};
onMounted(()=>Promise.all([loadStatus(),loadSessions()]));
</script>
<style scoped>.security-page{padding:16px}.actions{margin-top:16px}.setup-box{display:grid;gap:12px;margin-top:18px;padding:16px;background:#f7f8fa;border-radius:8px}.uri{margin-top:4px}.muted{font-size:12px;color:#8c8c8c;margin-top:4px}</style>
