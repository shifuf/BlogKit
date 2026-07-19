import { createRouter, createWebHistory } from "vue-router";
import Layout from '../components/Layout/index.vue';
import { useUserStore } from "../stores/modules/user";
import { start as startProgress, done as doneProgress } from '../utils/progress';
const routes = [{
  path: "/login",
  name: "Login",
  component: () => import("../views/Login/index.vue"),
  meta: {
    title: '登录',
    requiresAuth: false
  }
}, {
  path: "/",
  name: "Layout",
  component: Layout,
  meta: {
    requiresAuth: true
  },
  children: [{
    path: "",
    name: "dashboard",
    component: () => import("../views/Home/index.vue"),
    meta: {
      title: '工作台',
      requiresAuth: true
    }
  }, {
    path: "/users",
    name: "users",
    component: () => import("../views/User/index.vue"),
    meta: {
      title: '用户管理',
      requiresAuth: true
    }
  }, {
    path: "/categories",
    name: "categories",
    component: () => import("../views/Category/index.vue"),
    meta: {
      title: '分类管理',
      requiresAuth: true
    }
  }, {
    path: "/tags",
    name: "tags",
    component: () => import("../views/Tag/index.vue"),
    meta: {
      title: '标签管理',
      requiresAuth: true
    }
  }, {
    path: "/articles",
    name: "articles",
    component: () => import("../views/Article/index.vue"),
    meta: {
      title: '文章管理',
      requiresAuth: true
    }
  }, {
    path: "/articles/schedule",
    name: "articleSchedule",
    component: () => import("../views/Article/Schedule.vue"),
    meta: {
      title: '定时发布',
      requiresAuth: true
    }
  }, {
    path: "/series",
    name: "series",
    component: () => import("../views/Series/index.vue"),
    meta: {
      title: '系列管理',
      requiresAuth: true
    }
  }, {
    path: "/comments",
    name: "comments",
    component: () => import("../views/Message/index.vue"),
    meta: {
      title: '评论管理',
      requiresAuth: true
    }
  }, {
    path: "/attachments",
    name: "attachments",
    component: () => import("../views/Attachment/index.vue"),
    meta: {
      title: '附件管理',
      requiresAuth: true
    }
  }, {
    path: "/audit-logs",
    name: "auditLogs",
    component: () => import("../views/AuditLog/index.vue"),
    meta: {
      title: '操作日志',
      requiresAuth: true
    }
  }, {
    path: "/search-stats",
    name: "searchStats",
    component: () => import("../views/SearchStats/index.vue"),
    meta: {
      title: '搜索统计',
      requiresAuth: true
    }
  }, {
    path: "/ops/health",
    name: "opsHealth",
    component: () => import("../views/Ops/Health.vue"),
    meta: {
      title: '站点运维',
      requiresAuth: true
    }
  }, {
    path: "/articles/edit",
    name: "articleEdit",
    component: () => import("../views/Article/edit.vue"),
    meta: {
      title: '文章编辑',
      requiresAuth: true,
      hideInMenu: true
    }
  }, {
    path: "/dynamics",
    name: "dynamics",
    component: () => import("../views/Dynamic/index.vue"),
    meta: {
      title: '动态管理',
      requiresAuth: true
    }
  }, {
    path: "/friend-links",
    name: "friendLinks",
    component: () => import("../views/FriendLink/index.vue"),
    meta: {
      title: '友链管理',
      requiresAuth: true
    }
  }, {
    path: "/subscriptions",
    name: "subscriptions",
    component: () => import("../views/Subscription/index.vue"),
    meta: {
      title: '邮件订阅',
      requiresAuth: true
    }
  }, {
    path: "/blacklist",
    name: "blacklist",
    component: () => import("../views/Blacklist/index.vue"),
    meta: {
      title: '访问黑名单',
      requiresAuth: true
    }
  }, {
    path: "/settings",
    name: "settings",
    component: () => import("../views/Settings/index.vue"),
    meta: {
      title: '系统配置',
      requiresAuth: true
    }
  }, {
    path: "/settings/security",
    name: "settings-security",
    component: () => import("../views/Settings/Security.vue"),
    meta: {
      title: '账号安全',
      requiresAuth: true
    }
  }, {
    path: "/settings/import-export",
    name: "settings-import-export",
    component: () => import("../views/Settings/ImportExport.vue"),
    meta: {
      title: '导入导出',
      requiresAuth: true
    }
  }, {
    path: "/:pathMatch(.*)*",
    name: "notFound",
    component: () => import("../views/NotFound/index.vue"),
    meta: {
      title: '页面不存在',
      requiresAuth: true,
      hideInMenu: true
    }
  }]
}];
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
});
router.beforeEach(to => {
  startProgress();
  const userStore = useUserStore();
  const isLoggedIn = userStore.getIsLoggedIn;
  const isAdmin = userStore.isAdmin;
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth);
  if (requiresAuth && !isLoggedIn) {
    return '/login';
  }
  if (requiresAuth && !isAdmin) {
    userStore.logout();
    return '/login';
  }
  if (to.path === '/login' && isLoggedIn && isAdmin) {
    return '/';
  }
  if (to.path === '/login' && isLoggedIn && !isAdmin) {
    userStore.logout();
  }
});
router.afterEach(() => {
  doneProgress();
});
router.onError(() => {
  doneProgress();
});
// 登录后空闲时预取其余路由 chunk，避免首次进入页面时才下载。
let prefetched = false;
const prefetchRouteChunks = () => {
  if (prefetched) return;
  prefetched = true;
  router.getRoutes().forEach(record => {
    const loader = record.components && record.components.default;
    if (typeof loader === 'function') {
      try {
        const result = loader();
        if (result && typeof result.then === 'function') {
          result.catch(() => {});
        }
      } catch {
        // 忽略单个 chunk 预取失败
      }
    }
  });
};
router.afterEach(() => {
  if (typeof window === 'undefined' || prefetched) return;
  const run = () => prefetchRouteChunks();
  if (window.requestIdleCallback) {
    window.requestIdleCallback(run, { timeout: 4000 });
  } else {
    setTimeout(run, 1500);
  }
});
export default router;
