<template>
  <div class="article-page">
    <main class="article-layout">
      <section class="article-feed" aria-label="文章列表">
        <div class="tag-filter-slot">
          <slot />
        </div>

        <el-skeleton v-if="loading && !articles.length" class="article-feed__empty" :rows="6" animated />

        <el-empty v-else-if="!articles.length" class="article-feed__empty" description="暂无公开文章" />

        <article v-for="article in articles" :key="article.id" class="article-card">
          <div class="article-card__cover">
            <NuxtLink :to="`/category/${article.id}`">
              <el-image class="article-card__image" :src="article.coverImageUrl" :alt="article.title" lazy />
              <span class="article-card__category">{{ article.categoryName }}</span>
            </NuxtLink>
          </div>

          <div class="article-card__body">
            <div class="article-card__content">
              <NuxtLink :to="`/category/${article.id}`" :title="article.title">
                <h2 class="article-card__title">{{ article.title }}</h2>
              </NuxtLink>
              <p class="article-card__summary">{{ article.summary }}</p>
            </div>

            <footer class="article-card__meta">
              <div class="article-card__tags">
                <span v-for="tagName in article.tagNames" :key="tagName">
                  <NuxtLink :to="`/tags/${tagName}`"><i>#</i>{{ tagName }}</NuxtLink>
                </span>
              </div>
              <div class="article-card__footer">
                <div class="article-card__stats">
                  <span><i class="iconfont">&#xe675;</i>{{ article.likeCount }}</span>
                  <span><i class="iconfont">&#xe677;</i>{{ article.viewCount }}</span>
                  <span><i class="iconfont">&#x100e9;</i>{{ article.commentCount }}</span>
                </div>
                <time :datetime="article.publishedAt">{{ formatRelativeTime(article.publishedAt) }}</time>
              </div>
            </footer>
          </div>
        </article>

        <div v-if="totalItems > pageSize" class="article-feed__pagination">
          <el-pagination
            background
            layout="prev, pager, next"
            :current-page="page"
            :page-size="pageSize"
            :total="totalItems"
            @current-change="emit('pageChange', $event)"
          />
        </div>
      </section>

      <aside class="article-sidebar">
        <WidgetSketch />
        <div v-if="siteSettings.weatherEnabled" class="weather-card">
          <WidgetWeather />
        </div>
      </aside>
    </main>
  </div>
</template>

<script setup lang="ts">
import type { ArticleSummary } from '~/types/blog';
import { formatRelativeTime } from '~/utils/date';
const { siteSettings } = useSiteSettings();
withDefaults(defineProps<{
  articles: ArticleSummary[];
  loading?: boolean;
  page?: number;
  pageSize?: number;
  totalItems?: number;
}>(), {
  loading: false,
  page: 1,
  pageSize: 10,
  totalItems: 0
});
const emit = defineEmits<{
  pageChange: [page: number];
}>();
</script>

<style lang="scss" scoped>
.article-page {
  margin-top: 30px;
}

.tag-filter-slot {
  width: 100%;
}

.article-layout {
  max-width: 1400px;
  display: flex;
  justify-content: space-between;
  margin: 0 auto;
  padding: 5rem 20px;
}

.article-feed {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-start;
  align-content: flex-start;
  width: 75%;
  height: fit-content;
}

.article-feed__empty {
  width: 100%;
  min-height: 360px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--card-bg-color);
}

.article-feed__pagination {
  width: 100%;
  display: flex;
  justify-content: center;
  margin-top: 1.2rem;
}

.article-card {
  flex-direction: column;
  width: calc(100% / 2 - 0.5rem);
  margin-bottom: 0.75rem;
  position: relative;
  overflow: hidden;
  will-change: transform;
  display: flex;
  align-items: center;
  transition: all 0.3s ease 0s;
  border-radius: 12px;
  box-shadow: var(--shadow-border);
  border: 1px solid var(--border-color);
}

.article-card__cover {
  width: 100%;
  height: 100%;
  display: flex;

  a {
    min-height: 100%;
    min-width: 100%;
    background-color: transparent;
    text-decoration: none;
    transition: all 0.3s ease 0s;
    overflow-wrap: break-word;
    -webkit-user-drag: none;
  }
}

.article-card__image {
  width: 100%;
  height: 100%;
  transition: all 0.6s ease 0s;
  object-fit: cover;
  max-width: 100%;
}

.article-card__category {
  position: absolute;
  top: 10px;
  left: 5px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  border-radius: 5px;
  font-size: 14px;
  padding: 2px 8px;
}

.article-card__body {
  padding: 1rem;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  width: 100%;
  height: 160px;
}

.article-card__tags {
  margin-right: 8px;
  padding: 0 0.2rem 0 0;

  i {
    color: var(--tags-color);
  }
}

.article-card__meta span {
  margin-right: 0.6rem;
  font-size: 1.06rem;
}

.article-card__footer {
  margin-top: 0.6rem;
  display: flex;
  justify-content: space-between;

  .iconfont {
    font-size: 1.2rem;
  }
}

.article-sidebar {
  width: calc(25% - 30px);
  height: auto;
  margin-right: 10px;

  .card-widget {
    border-radius: 12px;
  }
}

.weather-card {
  height: 180px;
  width: 100%;
  margin-top: 20px;
}

@media screen and (max-width: 1200px) {
  .article-feed {
    width: 100%;
  }

  .article-card__body {
    height: 100%;
  }

  .article-sidebar {
    margin-left: 20px;
  }
}

@media screen and (max-width: 1020px) {
  .article-sidebar,
  .article-card__summary,
  .article-card__footer span:nth-child(2n + 1) {
    display: none;
  }
}

@media screen and (max-width: 760px) {
  .article-layout {
    width: 100%;
  }

  .article-card {
    height: 225px;
    width: 100%;
    flex-direction: row;
  }

  .article-card__tags {
    display: none;
  }
}

@media screen and (max-width: 680px) {
  .article-card {
    width: 100%;
    height: 100%;
    flex-direction: column;
  }

  .article-card__cover {
    height: 170px;
  }
}

@media screen and (max-width: 300px) {
  .article-layout {
    flex-direction: column;
    padding: 0 20px;
  }
}
</style>
