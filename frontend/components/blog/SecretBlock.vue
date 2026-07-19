<script setup lang="ts">
import { Lock, MessageSquare } from '@lucide/vue';
import { computed } from 'vue';
const props = defineProps<{
  gate: string;
  html: string;
  isAuthenticated: boolean;
  hasCommented: boolean;
  onLockedAction: (gate: string) => void;
}>();
const locked = computed(() => {
  // 服务端已清空未授权块体：有正文则视为已通过门控（含管理员/作者/已评论）。
  // 仍兼容旧客户端：无正文时按 gate 本地判断。
  if (props.html && props.html.trim()) return false;
  if (props.gate === 'comment') return !props.hasCommented;
  return !props.isAuthenticated;
});
const hint = computed(() => props.gate === 'comment' ? '评论后可见此处内容' : '登录后可见此处内容');
const showEmptyUnlocked = computed(() => !locked.value && !(props.html && props.html.trim()));
</script>

<template>
  <div class="bd-secret" :class="{ 'bd-secret--locked': locked }">
    <div v-if="locked" class="bd-secret__mask">
      <Lock :size="20" />
      <span class="bd-secret__hint">{{ hint }}</span>
      <button type="button" class="bd-secret__btn" @click="onLockedAction(gate)">
        <component :is="gate === 'comment' ? MessageSquare : Lock" :size="15" />
        {{ gate === 'comment' ? '去评论' : '去登录' }}
      </button>
    </div>
    <div v-else-if="showEmptyUnlocked" class="bd-secret__content bd-secret__content--empty" />
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
</style>
