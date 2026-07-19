<script setup lang="ts">
import { Download, Lock, MessageSquare, Copy } from '@lucide/vue';
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { DownloadResource } from '~/types/blog';
import { decodeBlockPayload, uniquifySvg, type DownloadDescriptor } from '~/utils/blog-blocks';
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
const descriptor = computed(() => decodeBlockPayload<DownloadDescriptor>(props.payload));
const gate = computed(() => descriptor.value?.gate || 'none');
const locked = computed(() => {
  if (gate.value === 'login') return !props.isAuthenticated;
  if (gate.value === 'comment') return !props.hasCommented;
  return false;
});
const items = computed(() => descriptor.value?.items || []);
const resolved = ref<DownloadResource['items']>();
const loadingIndex = ref(-1);
const platformLabel = (key: string) => netdiskIcon(key).label;
const iconFor = (key: string, index: number) => uniquifySvg(netdiskIcon(key).svg, `${props.instanceId}-${index}`);
const ensureResolved = async () => {
  if (resolved.value) return resolved.value;
  const data = await props.resolve(descriptor.value!.token);
  resolved.value = data.items || [];
  return resolved.value;
};
const handleDownload = async (index: number) => {
  if (locked.value) {
    props.onLockedAction(gate.value);
    return;
  }
  if (loadingIndex.value !== -1) return;
  loadingIndex.value = index;
  try {
    const list = await ensureResolved();
    const target = list?.[index];
    if (!target?.url) {
      ElMessage.warning('该资源暂无有效链接');
      return;
    }
    if (target.code) {
      try {
        await navigator.clipboard?.writeText(target.code);
        ElMessage.success(`提取码 ${target.code} 已复制`);
      } catch {}
    }
    window.open(target.url, '_blank', 'noopener');
  } catch (error: any) {
    // 捕获401错误，提示重新登录
    if (error?.statusCode === 401 || error?.message?.includes('401') || error?.message?.includes('登录')) {
      ElMessage.error('登录已过期，请重新登录');
      props.onLockedAction('login');
    } else {
      ElMessage.error(error?.message || '获取下载地址失败');
    }
  } finally {
    loadingIndex.value = -1;
  }
};
const copyCode = async (index: number) => {
  const code = resolved.value?.[index]?.code;
  if (!code) return;
  try {
    await navigator.clipboard?.writeText(code);
    ElMessage.success(`提取码 ${code} 已复制`);
  } catch {
    ElMessage.warning('复制失败，请手动复制');
  }
};
</script>

<template>
  <div v-if="descriptor" class="bd-download" :class="{ 'bd-download--dark': isDark }">
    <div class="bd-download__head">
      <span class="bd-download__badge">资源</span>
      <span class="bd-download__title">{{ descriptor.title || '资源下载' }}</span>
    </div>
    <p v-if="descriptor.desc" class="bd-download__desc">{{ descriptor.desc }}</p>

    <div v-if="locked" class="bd-download__locked">
      <Lock :size="18" />
      <span>{{ gate === 'login' ? '登录后可查看下载地址' : '评论后可查看下载地址' }}</span>
      <button type="button" class="bd-download__unlock" @click="onLockedAction(gate)">
        <component :is="gate === 'login' ? Lock : MessageSquare" :size="15" />
        {{ gate === 'login' ? '去登录' : '去评论' }}
      </button>
    </div>

    <div v-else class="bd-download__list">
      <button
        v-for="(item, index) in items"
        :key="index"
        type="button"
        class="bd-download__item"
        :disabled="loadingIndex === index"
        @click="handleDownload(index)"
      >
        <span class="bd-download__icon" v-html="iconFor(item.platform, index)" />
        <span class="bd-download__meta">
          <span class="bd-download__name">{{ item.name || platformLabel(item.platform) }}</span>
          <span class="bd-download__sub">
            {{ platformLabel(item.platform) }}
            <template v-if="resolved && resolved[index]?.code">
              · 提取码 {{ resolved[index]?.code }}
              <Copy class="bd-download__copy" :size="13" @click.stop="copyCode(index)" />
            </template>
            <template v-else-if="item.hasCode">· 含提取码</template>
          </span>
        </span>
        <Download class="bd-download__action" :size="18" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.bd-download {
  width: 100%;
  max-width: 100%;
  margin: 20px 0;
  padding: 18px 18px 16px;
  border: 1px solid #eef0f3;
  border-radius: 14px;
  background: linear-gradient(180deg, #fafbfc 0%, #ffffff 100%);
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.05);
}
.bd-download--dark {
  border-color: rgba(255, 255, 255, 0.08);
  background: linear-gradient(180deg, #1c2230 0%, #171b26 100%);
  box-shadow: none;
}
.bd-download__head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.bd-download__badge {
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #3b82f6;
  border-radius: 6px;
  padding: 2px 8px;
}
.bd-download__title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}
.bd-download--dark .bd-download__title {
  color: #e5e7eb;
}
.bd-download__desc {
  margin: 10px 0 0;
  font-size: 13.5px;
  line-height: 1.6;
  color: #6b7280;
  white-space: pre-wrap;
}
.bd-download__locked {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  margin-top: 14px;
  padding: 16px;
  border-radius: 10px;
  background: rgba(59, 130, 246, 0.06);
  color: #6b7280;
  font-size: 14px;
}
.bd-download__unlock {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-left: auto;
  padding: 7px 14px;
  border: 0;
  border-radius: 8px;
  background: #3b82f6;
  color: #fff;
  font-size: 13.5px;
  cursor: pointer;
}
.bd-download__unlock:hover {
  filter: brightness(1.06);
}
.bd-download__list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(220px, 100%), 1fr));
  gap: 12px;
  margin-top: 14px;
}
.bd-download__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #eef0f3;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.15s ease;
}
.bd-download__item:hover {
  border-color: #3b82f6;
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.14);
  transform: translateY(-1px);
}
.bd-download__item:disabled {
  opacity: 0.6;
  cursor: progress;
}
.bd-download--dark .bd-download__item {
  background: #20273544;
  border-color: rgba(255, 255, 255, 0.08);
}
.bd-download__icon {
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
}
.bd-download__icon :deep(svg) {
  width: 100%;
  height: 100%;
}
.bd-download__meta {
  flex: 1;
  min-width: 0;
}
.bd-download__name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bd-download--dark .bd-download__name {
  color: #e5e7eb;
}
.bd-download__sub {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #9ca3af;
}
.bd-download__copy {
  cursor: pointer;
  vertical-align: -2px;
}
.bd-download__action {
  color: #3b82f6;
  flex: 0 0 auto;
}

@media (max-width: 560px) {
  .bd-download {
    margin: 16px 0;
    padding: 16px;
    border-radius: 16px;
  }

  .bd-download__head {
    flex-wrap: wrap;
  }

  .bd-download__locked {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
    padding: 14px;
  }

  .bd-download__locked > span {
    line-height: 1.7;
  }

  .bd-download__unlock {
    width: 100%;
    min-height: 44px;
    margin-left: 0;
  }

  .bd-download__item {
    gap: 10px;
    padding: 12px 10px;
  }

  .bd-download__icon {
    width: 34px;
    height: 34px;
    flex-basis: 34px;
  }
}
</style>
