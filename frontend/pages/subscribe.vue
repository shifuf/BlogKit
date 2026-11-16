<script setup lang="ts">
const route=useRoute(),config=useRuntimeConfig();const token=typeof route.query.confirm==='string'?route.query.confirm:'';
const done=ref(false),busy=ref(false),error=ref('');
if(!token)await navigateTo({path:'/',query:{subscribe:'1'}});
const confirm=async()=>{busy.value=true;try{await $fetch('/subscriptions/email/confirm',{baseURL:config.public.apiBaseUrl,method:'POST',body:{token}});done.value=true}catch(e:any){error.value=e?.data?.message||'确认失败，请重新申请订阅'}finally{busy.value=false}};
</script>
<template><main style="max-width:520px;margin:80px auto;padding:24px"><h1>确认邮件订阅</h1><p v-if="done">订阅已确认，有公开新文章时将收到通知。</p><template v-else><p>点击后开始接收站点公开新文章的邮件通知，邮件内可以随时退订。</p><el-alert v-if="error" :title="error" type="error"/><el-button :loading="busy" @click="confirm">确认订阅</el-button></template><p><NuxtLink to="/">返回首页</NuxtLink></p></main></template>
