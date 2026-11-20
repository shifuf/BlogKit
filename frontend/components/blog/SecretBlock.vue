<script setup lang="ts">
import { Lock, MessageSquare } from '@lucide/vue';
import { computed, ref } from 'vue';
const props = defineProps<{
  gate: string;
  html: string;
  isAuthenticated: boolean;
  hasCommented: boolean;
  onLockedAction: (gate: string) => void;
  sectionId?: string;
  onUnlock?: (sectionId: string, password: string) => Promise<void>;
}>();
const password = ref('');
const unlocking = ref(false);
const unlockError = ref('');
// A server-redacted block remains locked until refreshed content actually arrives.
const locked = computed(() => !props.html || !props.html.trim());
const hint = computed(() => ({
  comment: '评论后可见此处内容', password: '输入区块密码后可见此处内容',
  users: props.isAuthenticated ? '此处内容仅对指定用户可见' : '登录指定账号后可见此处内容',
  login: '登录后可见此处内容'
} as Record<string, string>)[props.gate] || '此处内容暂不可见');
const unlock = async () => {
  if (!password.value || unlocking.value || !props.onUnlock || !props.sectionId) return;
  unlocking.value = true;
  unlockError.value = '';
  try { await props.onUnlock(props.sectionId, password.value); password.value = ''; }
  catch (failure) {
    const error = failure as { message?: string; data?: { message?: string }; response?: { _data?: { message?: string } } };
    unlockError.value = error.data?.message || error.response?._data?.message || error.message || '解锁失败，请重试';
  } finally { unlocking.value = false; }
};
</script>

<template>
  <div class="bd-secret" :class="{ 'bd-secret--locked': locked }">
    <div v-if="locked" class="bd-secret__mask">
      <Lock :size="20" />
      <span class="bd-secret__hint">{{ hint }}</span>
      <form v-if="gate === 'password' && sectionId" class="bd-secret__password-form" @submit.prevent="unlock">
        <label class="bd-secret__password-label">区块密码
          <input v-model="password" type="password" autocomplete="off" maxlength="100" :disabled="unlocking" required />
        </label>
        <button type="submit" class="bd-secret__btn" :disabled="unlocking || !password">{{ unlocking ? '验证中…' : '解锁内容' }}</button>
        <span v-if="unlockError" class="bd-secret__error" role="alert">{{ unlockError }}</span>
      </form>
      <button v-else-if="gate === 'login' || gate === 'comment' || (gate === 'users' && !isAuthenticated)" type="button" class="bd-secret__btn" @click="onLockedAction(gate)">
        <component :is="gate === 'comment' ? MessageSquare : Lock" :size="15" />
        {{ gate === 'comment' ? '去评论' : '去登录' }}
      </button>
    </div>
    <div v-else class="bd-secret__content" v-html="html" />
  </div>
</template>

<style scoped>
.bd-secret {
  margin: 18px 0;
}
.bd-secret--locked {
  border: 1px dashed color-mix(in srgb, var(--theme-color, #3b82f6) 40%, var(--border-color, #e5e7eb));
  border-radius: 12px;
  background: color-mix(in srgb, var(--theme-color, #3b82f6) 5%, transparent);
}
.bd-secret__mask {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 28px 16px;
  color: var(--text-muted, #6b7280);
  text-align: center;
}
.bd-secret__hint {
  font-size: 14px;
}
.bd-secret__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border: 0;
  border-radius: 999px;
  background: var(--theme-color, #3b82f6);
  color: #fff;
  font-size: 13.5px;
  cursor: pointer;
  transition: filter 0.2s ease;
}
.bd-secret__btn:hover {
  filter: brightness(1.06);
}
.bd-secret__password-form { display: flex; justify-content: center; align-items: end; flex-wrap: wrap; gap: 10px; max-width: 100%; }
.bd-secret__password-label { display: flex; flex-direction: column; gap: 5px; text-align: left; font-size: 13px; min-width: 0; }
.bd-secret__password-label input { box-sizing: border-box; width: min(220px, 100%); max-width: 100%; border: 1px solid var(--border-color, #cbd5e1); border-radius: 8px; padding: 8px 10px; background: var(--bg-card, #fff); color: var(--text-primary, #1e293b); }
.bd-secret__btn:disabled { opacity: .6; cursor: default; }
.bd-secret__error { flex-basis: 100%; font-size: 13px; color: var(--danger-color, #dc2626); overflow-wrap: anywhere; }
</style>
