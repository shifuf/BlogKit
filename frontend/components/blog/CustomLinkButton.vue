<script setup lang="ts">
import { ExternalLink, Lock, MessageSquare } from '@lucide/vue';
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { DownloadResource } from '~/types/blog';
import { decodeBlockPayload, uniquifySvg, type ButtonDescriptor } from '~/utils/blog-blocks';
import { netdiskIcon } from '~/utils/netdisk-icons';
const props = defineProps<{
  payload: string;
  isDark: boolean;
  isAuthenticated: boolean;
  hasCommented: boolean;
  instanceId: number;
  resolve: (token: string) => Promise<DownloadResource>;
  onLockedAction: (gate: string) => void;
}>();
const descriptor = computed(() => decodeBlockPayload<ButtonDescriptor>(props.payload));
const gate = computed(() => descriptor.value?.gate || 'none');
const color = computed(() => descriptor.value?.color || '#3b82f6');
const loading = ref(false);
const locked = computed(() => {
  if (gate.value === 'login') return !props.isAuthenticated;
  if (gate.value === 'comment') return !props.hasCommented;
  return false;
});
const iconSvg = computed(() => {
  const key = descriptor.value?.icon;
  if (!key || key === 'link') return '';
  return uniquifySvg(netdiskIcon(key).svg, `btn-${props.instanceId}`);
});
const handleClick = async () => {
  if (locked.value) {
    props.onLockedAction(gate.value);
    return;
  }
  if (loading.value) return;
  loading.value = true;
  try {
    const data = await props.resolve(descriptor.value!.token);
    const url = data.button?.url;
    if (!url) {
      ElMessage.warning('该按钮暂无有效链接');
      return;
    }
    window.open(url, '_blank', 'noopener');
  } catch (error) {
    ElMessage.error((error as Error)?.message || '获取链接失败');
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <span v-if="descriptor" class="bd-linkbtn-wrap">
    <button
      type="button"
      class="bd-linkbtn"
      :class="{ 'bd-linkbtn--locked': locked }"
      :style="{ '--bd-btn-color': color }"
      :disabled="loading"
      @click="handleClick"
    >
      <Lock v-if="locked" :size="15" />
      <MessageSquare v-else-if="locked && gate === 'comment'" :size="15" />
      <span v-else-if="iconSvg" class="bd-linkbtn__icon" v-html="iconSvg" />
      <ExternalLink v-else :size="15" />
      <span class="bd-linkbtn__text">{{ descriptor.text }}</span>
    </button>
  </span>
</template>

<style scoped>
.bd-linkbtn-wrap {
  display: inline-block;
  margin: 4px 6px 4px 0;
}
.bd-linkbtn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 16px;
  border: 0;
  border-radius: 10px;
  background: var(--bd-btn-color, #3b82f6);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: filter 0.2s ease, transform 0.15s ease;
}
.bd-linkbtn:hover {
  filter: brightness(1.08);
  transform: translateY(-1px);
}
.bd-linkbtn:disabled {
  opacity: 0.6;
  cursor: progress;
}
.bd-linkbtn--locked {
  background: #9ca3af;
}
.bd-linkbtn__icon {
  width: 18px;
  height: 18px;
  display: inline-flex;
}
.bd-linkbtn__icon :deep(svg) {
  width: 100%;
  height: 100%;
}
</style>
