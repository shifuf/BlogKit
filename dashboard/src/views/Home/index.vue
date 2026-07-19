<template>
  <div class="home-container">
    <div class="home-welcome">
      <div class="home-welcome__text">
        <h1>你好，{{ userStore.userInfo?.displayName || '管理员' }} 👋</h1>
        <p>欢迎来到 我的博客 管理后台，这里是你管理站点内容的中心。</p>
      </div>
      <div class="home-welcome__actions">
        <a-button type="primary" size="large" @click="goTo('/articles/edit')">
          <template #icon><edit-outlined /></template>
          写文章
        </a-button>
        <a-button size="large" @click="goTo('/articles')">
          <template #icon><file-text-outlined /></template>
          文章列表
        </a-button>
      </div>
    </div>

    <a-row :gutter="[16, 16]">
      <a-col v-for="card in cards" :key="card.key" :xs="24" :sm="12" :lg="6">
        <div class="stat-card" :class="`stat-card--${card.theme}`">
          <div class="stat-card__icon">
            <component :is="card.icon" />
          </div>
          <div class="stat-card__body">
            <a-statistic :title="card.title" :value="statistics[card.key] || 0" :loading="loading" />
          </div>
        </div>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" class="home-sections">
      <a-col :xs="24" :lg="16">
        <a-card title="内容榜单" :bordered="false" class="home-card">
          <div class="rank-grid">
            <section v-for="group in topArticleGroups" :key="group.key" class="rank-section">
              <div class="rank-section__title">{{ group.title }}</div>
              <a-empty v-if="!group.items.length" :description="`${group.title}暂无数据`" />
              <ol v-else class="rank-list">
                <li v-for="(item, index) in group.items" :key="`${group.key}-${item.id}-${index}`" class="rank-item">
                  <span class="rank-index">{{ index + 1 }}</span>
                  <button class="rank-title" type="button" @click="goTo(`/articles/edit?id=${item.id}`)">
                    {{ item.title }}
                  </button>
                  <span class="rank-value">{{ item.value }} {{ item.metric }}</span>
                </li>
              </ol>
            </section>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="8">
        <RecentMessages :limit="5" />
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" class="home-sections">
      <a-col :span="24">
        <a-card title="管理范围" :bordered="false" class="home-card">
          <div class="scope-tags">
            <a-tag v-for="scope in scopes" :key="scope" :color="scopeColor(scope)">{{ scope }}</a-tag>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { EditOutlined, FileTextOutlined, UserOutlined, EyeOutlined, MessageOutlined, ShareAltOutlined, FieldTimeOutlined } from '@ant-design/icons-vue';
import { getDashboardStats } from '@/api/Dashboard';
import { useUserStore } from '@/stores/modules/user';
import RecentMessages from '@/components/RecentMessages/index.vue';
const router = useRouter();
const userStore = useUserStore();
const statistics = ref({
  articleCount: 0,
  userCount: 0,
  totalViews: 0,
  commentCount: 0,
  shareCount: 0,
  todayShareCount: 0
});
const topLists = ref({
  topViewedArticles: [],
  topLikedArticles: [],
  topSharedArticles: []
});
const loading = ref(false);
const cards = [{
  key: 'articleCount',
  title: '文章总数',
  icon: FileTextOutlined,
  theme: 'blue'
}, {
  key: 'userCount',
  title: '用户总数',
  icon: UserOutlined,
  theme: 'cyan'
}, {
  key: 'totalViews',
  title: '总访问量',
  icon: EyeOutlined,
  theme: 'green'
}, {
  key: 'commentCount',
  title: '评论总数',
  icon: MessageOutlined,
  theme: 'orange'
}, {
  key: 'shareCount',
  title: '分享次数',
  icon: ShareAltOutlined,
  theme: 'purple'
}, {
  key: 'todayShareCount',
  title: '近24小时分享',
  icon: FieldTimeOutlined,
  theme: 'magenta'
}];
const topArticleGroups = computed(() => [{
  key: 'views',
  title: '阅读排行',
  items: topLists.value.topViewedArticles || []
}, {
  key: 'likes',
  title: '点赞排行',
  items: topLists.value.topLikedArticles || []
}, {
  key: 'shares',
  title: '分享排行',
  items: topLists.value.topSharedArticles || []
}]);
const scopes = ['用户管理', '菜单管理', '文章管理 / md-editor-v3', '评论管理', '动态管理', '友链管理', '系统配置 / 导入导出'];
const scopeColorMap = {
  '用户管理': 'blue',
  '菜单管理': 'cyan',
  '文章管理 / md-editor-v3': 'green',
  '评论管理': 'geekblue',
  '动态管理': 'orange',
  '友链管理': 'purple',
  '系统配置 / 导入导出': 'red'
};
const scopeColor = scope => scopeColorMap[scope] || 'default';
const loadDashboard = async () => {
  loading.value = true;
  try {
    const dashboard = await getDashboardStats();
    statistics.value = dashboard?.summary || dashboard || {};
    topLists.value = {
      topViewedArticles: dashboard?.topViewedArticles || [],
      topLikedArticles: dashboard?.topLikedArticles || [],
      topSharedArticles: dashboard?.topSharedArticles || []
    };
  } finally {
    loading.value = false;
  }
};
const goTo = path => router.push(path);
onMounted(() => {
  loadDashboard();
});
</script>

<style lang="scss" scoped>
.home-container {
  padding: 20px;
}

.home-welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 24px;
}

.home-welcome__text h1 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
  color: #1f2937;
}

.home-welcome__text p {
  margin: 0;
  color: #6b7280;
}

.home-welcome__actions {
  display: flex;
  gap: 12px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #f0f0f0;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.04);
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 10px 26px rgba(15, 23, 42, 0.08);
  }
}

.stat-card__icon {
  width: 52px;
  height: 52px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  font-size: 24px;
  background: rgba(64, 158, 255, 0.12);
  color: #409eff;
}

.stat-card--cyan .stat-card__icon {
  background: rgba(19, 194, 194, 0.12);
  color: #13c2c2;
}

.stat-card--green .stat-card__icon {
  background: rgba(82, 196, 26, 0.12);
  color: #52c41a;
}

.stat-card--orange .stat-card__icon {
  background: rgba(250, 140, 22, 0.12);
  color: #fa8c16;
}

.stat-card--purple .stat-card__icon {
  background: rgba(114, 46, 209, 0.12);
  color: #722ed1;
}

.stat-card--magenta .stat-card__icon {
  background: rgba(235, 47, 150, 0.12);
  color: #eb2f96;
}

.stat-card__body {
  min-width: 0;

  :deep(.ant-statistic-title) {
    color: #6b7280;
    font-size: 13px;
    margin-bottom: 4px;
  }

  :deep(.ant-statistic-content) {
    font-size: 26px;
    font-weight: 700;
    color: #1f2937;
  }
}

.home-sections {
  margin-top: 20px;
}

.home-card {
  border-radius: 14px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.04);
}

.rank-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.rank-section {
  min-width: 0;
}

.rank-section__title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.rank-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.rank-item {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
}

.rank-index {
  width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: 6px;
  background: #f0f5ff;
  color: #2f54eb;
  font-size: 12px;
  font-weight: 700;
}

.rank-title {
  min-width: 0;
  padding: 0;
  overflow: hidden;
  border: 0;
  background: transparent;
  color: #1f2937;
  cursor: pointer;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-title:hover {
  color: #2f54eb;
}

.rank-value {
  color: #8c8c8c;
  font-size: 12px;
  white-space: nowrap;
}

.scope-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

@media (max-width: 768px) {
  .home-welcome {
    flex-direction: column;
    align-items: flex-start;
  }

  .rank-grid {
    grid-template-columns: 1fr;
  }
}
</style>
