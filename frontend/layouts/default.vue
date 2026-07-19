<template>
  <div class="site-shell">
    <BlogSiteAnnouncement />
    <BlogSiteHeader />
    <main class="site-main">
      <slot />
    </main>
    <BlogSiteFooter />
    <BlogBackToTop />
    <BlogImageViewer />
    <BlogLoginModal v-model="loginModalOpen" />
  </div>
</template>

<script setup lang="ts">
const {
  siteSettings,
  loadSiteSettings
} = useSiteSettings();
const {
  open: loginModalOpen
} = useLoginModal();
useHead({
  title: computed(() => `${siteSettings.value.siteName || '我的博客'} - ${siteSettings.value.siteSubtitle || '记录技术、项目与生活'}`),
  meta: [{
    name: 'description',
    content: computed(() => siteSettings.value.siteDescription || '记录技术、项目与生活')
  }, {
    name: 'keywords',
    content: computed(() => siteSettings.value.siteKeywords || '博客,Nuxt,Spring Boot')
  }]
});
await loadSiteSettings();
</script>
