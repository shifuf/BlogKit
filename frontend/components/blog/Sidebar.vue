<script setup lang="ts">
const { siteSettings } = useSiteSettings();
</script>

<template>
  <aside class="blog-sidebar">
    <WidgetWeather v-if="siteSettings.weatherEnabled" />
    <BlogRandomPostsWidget />
  </aside>
</template>
