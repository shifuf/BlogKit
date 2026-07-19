<script setup lang="ts">
const { fetchArticleArchive, fetchArticleArchiveYears } = useApiClient()

const selectedYear = ref<number | null>(null)
const years = ref<number[]>([])
const archive = ref<Array<{ year: number; months: Array<{ month: number; articles: Array<{ id: number; title: string; publishedAt: string }> }> }>>([])
const pending = ref(true)
const openMonths = ref<Record<number, boolean>>({})

const loadYears = async () => {
  const res = await fetchArticleArchiveYears()
  years.value = res.success ? (res.data || []) : []
  if (!selectedYear.value && years.value.length) {
    selectedYear.value = years.value[0]
  }
}

const loadArchive = async () => {
  pending.value = true
  try {
    const res = await fetchArticleArchive(selectedYear.value || undefined)
    archive.value = res.success ? (res.data || []) : []
    const next: Record<number, boolean> = {}
    const months = archive.value[0]?.months || []
    months.forEach((month, index) => {
      next[month.month] = index === 0
    })
    openMonths.value = next
  } finally {
    pending.value = false
  }
}

const selectYear = async (year: number) => {
  if (selectedYear.value === year) return
  selectedYear.value = year
  await loadArchive()
}

const toggleMonth = (month: number) => {
  openMonths.value = { ...openMonths.value, [month]: !openMonths.value[month] }
}

const formatDay = (value?: string) => {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(5, 10)
}

await loadYears()
await loadArchive()

useHead({
  title: '文章归档'
})
</script>

<template>
  <main class="archive-page page-container">
    <header class="archive-page__head">
      <h1>文章归档</h1>
      <p>按年/月浏览公开文章（默认最近一年）</p>
    </header>

    <div v-if="years.length" class="archive-years">
      <button
        v-for="year in years"
        :key="year"
        type="button"
        class="archive-years__item"
        :class="{ active: selectedYear === year }"
        @click="selectYear(year)"
      >
        {{ year }}
      </button>
    </div>

    <ElSkeleton v-if="pending" :rows="8" animated />
    <ElEmpty v-else-if="!archive?.length" description="暂无文章" />
    <div v-else class="archive-tree">
      <section v-for="year in archive" :key="year.year" class="archive-year">
        <div class="archive-year__head">
          <strong>{{ year.year }} 年</strong>
          <span>{{ year.months.reduce((sum, m) => sum + m.articles.length, 0) }} 篇</span>
        </div>
        <div class="archive-year__body">
          <div v-for="month in year.months" :key="`${year.year}-${month.month}`" class="archive-month">
            <button type="button" class="archive-month__head" @click="toggleMonth(month.month)">
              <h3>{{ month.month }} 月 · {{ month.articles.length }} 篇</h3>
            </button>
            <ul v-show="openMonths[month.month]">
              <li v-for="article in month.articles" :key="article.id">
                <time>{{ formatDay(article.publishedAt) }}</time>
                <NuxtLink :to="`/article/${article.id}`">{{ article.title }}</NuxtLink>
              </li>
            </ul>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.archive-page {
  max-width: 860px;
  padding: 28px 16px 48px;
}
.archive-page__head {
  margin-bottom: 18px;
}
.archive-page__head h1 {
  margin: 0 0 8px;
  font-size: 28px;
}
.archive-page__head p {
  margin: 0;
  color: var(--text-secondary);
}
.archive-years {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.archive-years__item {
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: inherit;
  border-radius: 999px;
  padding: 6px 12px;
  cursor: pointer;
}
.archive-years__item.active {
  border-color: var(--theme-color);
  color: var(--theme-color);
  background: color-mix(in srgb, var(--theme-color) 10%, transparent);
}
.archive-year {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  margin-bottom: 12px;
  overflow: hidden;
  background: var(--bg-card);
}
.archive-year__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  background: var(--bg-soft);
}
.archive-year__body {
  padding: 8px 16px 16px;
}
.archive-month + .archive-month {
  margin-top: 12px;
}
.archive-month__head {
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
  color: inherit;
}
.archive-month h3 {
  margin: 0 0 8px;
  font-size: 15px;
  color: var(--text-secondary);
}
.archive-month ul {
  list-style: none;
  margin: 0;
  padding: 0;
}
.archive-month li {
  display: flex;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--border-color);
}
.archive-month li:last-child {
  border-bottom: 0;
}
.archive-month time {
  color: var(--text-secondary);
  font-size: 13px;
  min-width: 42px;
}
.archive-month a {
  color: inherit;
  text-decoration: none;
}
.archive-month a:hover {
  color: var(--theme-color);
}
</style>
