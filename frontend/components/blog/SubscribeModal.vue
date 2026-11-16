<script setup lang="ts">
const open = defineModel<boolean>('open', { default: false })
const config = useRuntimeConfig()
const email = ref('')
const loading = ref(false)
const message = ref('')
const ok = ref(false)
const emailInput = ref<HTMLInputElement | null>(null)

const close = () => {
  open.value = false
}

const submit = async () => {
  const value = email.value.trim()
  if (!value || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    message.value = '请输入有效邮箱'
    ok.value = false
    return
  }
  loading.value = true
  message.value = ''
  try {
    await $fetch('/subscriptions/email', {
      baseURL: config.public.apiBaseUrl,
      method: 'POST',
      body: { email: value }
    })
    message.value = '确认邮件已加入发送队列，请前往邮箱点击确认链接'
    ok.value = true
    email.value = ''
  } catch (error: any) {
    message.value = error?.data?.message || error?.message || '订阅失败，请稍后再试'
    ok.value = false
  } finally {
    loading.value = false
  }
}

watch(open, value => {
  if (value) {
    message.value = ''
    ok.value = false
    nextTick(() => {
      // 稍等弹窗动画后聚焦，避免键盘顶起时位置还在底部
      setTimeout(() => emailInput.value?.focus(), 80)
    })
  }
})
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="subscribe-modal" role="dialog" aria-modal="true" aria-label="邮件订阅" @click.self="close">
      <div class="subscribe-modal__panel">
        <header class="subscribe-modal__head">
          <strong>邮件订阅</strong>
          <button type="button" class="subscribe-modal__close" aria-label="关闭" @click="close">×</button>
        </header>
        <p class="subscribe-modal__desc">订阅后，本站发布新文章时会向你的邮箱发送通知。可随时退订。</p>
        <form class="subscribe-modal__form" @submit.prevent="submit">
          <label for="subscribe-email">邮箱地址</label>
          <input
            id="subscribe-email"
            ref="emailInput"
            v-model="email"
            type="email"
            placeholder="your@email.com"
            maxlength="128"
            autocomplete="email"
            enterkeyhint="done"
          />
          <p v-if="message" class="subscribe-modal__msg" :class="{ ok }">{{ message }}</p>
          <div class="subscribe-modal__actions">
            <button type="button" class="ghost" @click="close">取消</button>
            <button type="submit" class="primary" :disabled="loading">{{ loading ? '提交中…' : '订阅' }}</button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.subscribe-modal {
  position: fixed;
  inset: 0;
  z-index: 3200;
  display: flex;
  /* 始终垂直居中：移动端键盘弹出时输入框仍在可视区中部，不会被底部键盘盖住 */
  align-items: center;
  justify-content: center;
  padding: max(16px, env(safe-area-inset-top, 0px)) 16px max(16px, env(safe-area-inset-bottom, 0px));
  background: rgba(15, 23, 42, .45);
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}
.subscribe-modal__panel {
  width: min(420px, 100%);
  margin: auto;
  border-radius: 16px;
  background: var(--bg-card, #fff);
  box-shadow: 0 24px 60px rgba(15, 23, 42, .28);
  overflow: hidden;
  max-height: min(88dvh, 560px);
}
.subscribe-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px 0;
}
.subscribe-modal__close {
  border: 0;
  background: transparent;
  font-size: 24px;
  line-height: 1;
  color: var(--text-secondary);
  cursor: pointer;
}
.subscribe-modal__desc {
  margin: 8px 18px 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.55;
}
.subscribe-modal__form {
  display: grid;
  gap: 10px;
  padding: 14px 18px 18px;
}
.subscribe-modal__form label {
  font-size: 13px;
  color: var(--text-secondary);
}
.subscribe-modal__form input {
  height: 42px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 10px;
  padding: 0 12px;
  background: var(--bg-body, #fff);
  color: inherit;
  font: inherit;
  font-size: 16px; /* 避免 iOS 聚焦时整页放大 */
}
.subscribe-modal__msg {
  margin: 0;
  font-size: 13px;
  color: #ef4444;
}
.subscribe-modal__msg.ok { color: #16a34a; }
.subscribe-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}
.subscribe-modal__actions button {
  height: 36px;
  padding: 0 14px;
  border-radius: 8px;
  border: 1px solid var(--border-color, #e5e7eb);
  cursor: pointer;
  font-weight: 600;
}
.subscribe-modal__actions .ghost {
  background: transparent;
  color: var(--text-secondary);
}
.subscribe-modal__actions .primary {
  border-color: transparent;
  background: var(--theme-color, #409eff);
  color: #fff;
}
.subscribe-modal__actions .primary:disabled {
  opacity: .65;
  cursor: not-allowed;
}
@media (max-width: 560px) {
  .subscribe-modal {
    align-items: center;
    padding: 12px;
  }
  .subscribe-modal__panel {
    width: 100%;
    max-width: 420px;
    border-radius: 16px;
  }
}
</style>
