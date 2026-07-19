<script setup lang="ts">
const { siteSettings, loadSiteSettings } = useSiteSettings()
await loadSiteSettings()

const visible = computed(() =>
  Boolean(siteSettings.value.announcementEnabled && (siteSettings.value.announcementContent || '').trim())
)
const content = computed(() => (siteSettings.value.announcementContent || '').trim())
const linkUrl = computed(() => (siteSettings.value.announcementLinkUrl || '').trim())
const dismissed = useState('announcement-dismissed', () => false)
const show = computed(() => visible.value && !dismissed.value)

const dismiss = () => {
  dismissed.value = true
}
</script>

<template>
  <div v-if="show" class="site-announcement" role="status">
    <div class="site-announcement__inner">
      <a
        v-if="linkUrl"
        class="site-announcement__text"
        :href="linkUrl"
        :target="linkUrl.startsWith('http') ? '_blank' : undefined"
        :rel="linkUrl.startsWith('http') ? 'noopener noreferrer' : undefined"
      >
        {{ content }}
      </a>
      <span v-else class="site-announcement__text">{{ content }}</span>
      <button type="button" class="site-announcement__close" aria-label="关闭公告" @click="dismiss">×</button>
    </div>
  </div>
</template>

<style scoped>
.site-announcement {
  background: linear-gradient(90deg, #2f54eb, #409eff);
  color: #fff;
  font-size: 13px;
}
.site-announcement__inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 8px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.site-announcement__text {
  flex: 1;
  color: inherit;
  text-decoration: none;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.site-announcement__text:hover {
  text-decoration: underline;
}
.site-announcement__close {
  border: 0;
  background: transparent;
  color: #fff;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  opacity: .85;
}
.site-announcement__close:hover {
  opacity: 1;
}
</style>
