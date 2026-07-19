import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

// 本地开发：把仓库根 .env 的 BLOG_API_PAYLOAD_SECRET 等注入 process.env（.env 只维护一份）。
const rootEnvPath = resolve(fileURLToPath(new URL('..', import.meta.url)), '.env');
if (existsSync(rootEnvPath)) {
  for (const line of readFileSync(rootEnvPath, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq <= 0) continue;
    const key = trimmed.slice(0, eq).trim();
    let value = trimmed.slice(eq + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (!(key in process.env) || !String(process.env[key] || '').trim()) {
      process.env[key] = value;
    }
  }
}

const backendOrigin = process.env.BLOG_BACKEND_ORIGIN || 'http://localhost:8080';
const adminDevOrigin = process.env.BLOG_ADMIN_DEV_ORIGIN || 'http://localhost:8088';
const adminDistDir = fileURLToPath(new URL('../dashboard/dist', import.meta.url));

function normalizeAdminBasePath(raw: string | undefined) {
  const value = String(raw || '/admin').trim() || '/admin';
  const withSlash = value.startsWith('/') ? value : `/${value}`;
  return withSlash.replace(/\/+$/, '') || '/admin';
}

const adminBasePath = normalizeAdminBasePath(process.env.BLOG_ADMIN_BASE_PATH);

// CSP：dev 下保留 Report-Only + 'unsafe-eval'（Vite HMR 依赖 eval，否则本地白屏）；
// 生产强制(Content-Security-Policy)并去掉 'unsafe-eval'（Nuxt/Vue 生产为预编译模板，不需要 eval），缩小 XSS 面。
const isProd = process.env.NODE_ENV === 'production';
const cspDirectives = [
  "default-src 'self'",
  // 生产去掉 'unsafe-eval'；dev 保留以兼容 HMR。'unsafe-inline' 两端都需要（主题初始化内联脚本 + 水合）。
  // 'wasm-unsafe-eval' 两端都需要：高德地图 JS API 2.0 的 WebGL 渲染器要编译 WebAssembly，
  // 生产 CSP 去掉了 'unsafe-eval' 后若不放行 wasm，会报 "WebGLRender is not a constructor"（本地 dev 是 Report-Only 不拦截，故只在线上复现）。
  `script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'${isProd ? '' : " 'unsafe-eval'"} https://webapi.amap.com https://*.amap.com https://cdn.sencdn.com`,
  "style-src 'self' 'unsafe-inline'",
  // 允许 http: 图片：统一文件管理/图床在测试环境常以 http://IP:端口 提供
  "img-src 'self' data: blob: https: http:",
  "font-src 'self' data:",
  "media-src 'self' https: http: blob:",
  // dev 额外放行 ws/wss 供 Vite HMR 长连接。
  `connect-src 'self' https: http: https://restapi.amap.com https://*.amap.com https://*.seniverse.com https://*.sencdn.com https://nominatim.openstreetmap.org${isProd ? '' : ' ws: wss:'}`,
  "worker-src 'self' blob:",
  "frame-src 'self'",
  "base-uri 'self'",
  "object-src 'none'"
].join('; ');
const cspHeaderName = isProd ? 'Content-Security-Policy' : 'Content-Security-Policy-Report-Only';
const cspHeader = { [cspHeaderName]: cspDirectives };
// 后台(/admin)专用 CSP：高德地图 JS API 2.0 的 WebGL 渲染器内部用 new Function() 初始化模块，
// 仅 'wasm-unsafe-eval' 不够（那只放行 WebAssembly.compile，不放行 eval/new Function），
// 必须给完整 'unsafe-eval'，否则线上报 "WebGLRender is not a constructor" 且地图一直「加载中」。
// 高德 JS SDK 只在后台加载（公开前台用的是 REST 逆地理，不加载 SDK），
// 故只在 /admin 放开 eval，公开前台仍保持无 eval 的严格 CSP，不扩大对外 XSS 面。
const adminCspDirectives = [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline' 'unsafe-eval' 'wasm-unsafe-eval' https://webapi.amap.com https://*.amap.com https://cdn.sencdn.com",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob: https: http:",
  "font-src 'self' data:",
  "media-src 'self' https: http: blob:",
  // 后台图库裁剪/统一文件管理：http(s) 文件源
  `connect-src 'self' https: http: https://restapi.amap.com https://*.amap.com https://*.seniverse.com https://*.sencdn.com https://nominatim.openstreetmap.org${isProd ? '' : ' ws: wss:'}`,
  "worker-src 'self' blob:",
  "frame-src 'self'",
  "base-uri 'self'",
  "object-src 'none'"
].join('; ');
const adminCspHeader = { [cspHeaderName]: adminCspDirectives };
export default defineNuxtConfig({
  app: {
    head: {
      // 播放器由 plugins/music-player.client.ts 注册自定义元素，不再注入旧 public 脚本。
      // maximum-scale=1：禁止 iOS 聚焦 input 时自动放大页面，避免弹窗按钮点不到。
      viewport: 'width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover'
    }
  },
  // 路由切换始终回到顶部；刷新时也由 scroll-top 插件兜底。
  router: {
    options: {
      scrollBehavior(to, _from, savedPosition) {
        // 仅浏览器前进/后退保留位置；普通跳转、进入文章/动态一律回顶。
        if (savedPosition && (to as { meta?: { keepScroll?: boolean } }).meta?.keepScroll) {
          return savedPosition;
        }
        return { left: 0, top: 0 };
      }
    }
  },
  compatibilityDate: '2026-05-26',
  devtools: {
    enabled: true
  },
  devServer: {
    host: '0.0.0.0',
    port: 3256
  },
  // 模板里若出现 music-player 标签，按自定义元素处理，避免 Vue 当组件解析。
  vue: {
    compilerOptions: {
      isCustomElement: (tag: string) => tag === 'music-player'
    }
  },
  modules: ['@element-plus/nuxt', '@nuxtjs/tailwindcss', '@pinia/nuxt', 'pinia-plugin-persistedstate/nuxt', '@vite-pwa/nuxt'],
  css: ['~/assets/styles/main.scss', '~/assets/font/iconfont.css'],
  runtimeConfig: {
    backendOrigin,
    public: {
      apiBaseUrl: '/api/v1',
      adminBasePath,
      // 留空表示按访问地址自动识别（换 IP/域名免配置）；需要固定正式域名时在 .env 设 BLOG_SITE_URL（或 NUXT_PUBLIC_SITE_URL）。
      siteUrl: process.env.NUXT_PUBLIC_SITE_URL || process.env.BLOG_SITE_URL || '',
      // 接口混淆密钥（音乐等响应解密）。.env 只填 BLOG_API_PAYLOAD_SECRET；
      // NUXT_PUBLIC_API_PAYLOAD_SECRET 仅作 compose/构建时的同值注入别名。
      apiPayloadSecret: process.env.NUXT_PUBLIC_API_PAYLOAD_SECRET
        || process.env.BLOG_API_PAYLOAD_SECRET
        || ''
    }
  },
  nitro: {
    // 预压缩静态产物为 .gz，nginx 直接下发，免去运行时压缩。
    // 注意：不开 brotli —— brotli 构建期 CPU/内存峰值高，在 2G 小内存服务器上易把内存压满导致构建卡死(OOM)。
    compressPublicAssets: {
      gzip: true,
      brotli: false
    },
    publicAssets: [{
      dir: adminDistDir,
      baseURL: adminBasePath,
      fallthrough: true,
      maxAge: 60 * 60 * 24 * 365
    }],
    serverAssets: [{
      baseName: 'admin',
      dir: adminDistDir,
      pattern: 'index.html'
    }],
    devProxy: {
      '/api': {
        target: `${backendOrigin}/api`,
        changeOrigin: true
      },
      '/uploads': {
        target: `${backendOrigin}/uploads`,
        changeOrigin: true
      },
      [adminBasePath]: {
        target: `${adminDevOrigin}${adminBasePath}`,
        changeOrigin: true
      }
    }
  },
  routeRules: {
    '/api/**': {
      proxy: `${backendOrigin}/api/**`
    },
    '/uploads/**': {
      proxy: `${backendOrigin}/uploads/**`
    },
    // 带内容哈希的构建产物长期不可变缓存：浏览器/CDN 命中后零请求，重复访问与首屏 chunk 加载更快。
    '/_nuxt/**': {
      headers: {
        'cache-control': 'public, max-age=31536000, immutable'
      }
    },
    '/layout-interaction/**': {
      headers: {
        'cache-control': 'public, max-age=2592000'
      }
    },
    // SSR 进程内缓存(stale-while-revalidate)：首个访问把热数据缓存进 nitro，
    // 后续请求直接吐缓存 HTML，秒开；过期后台静默刷新。后台改动最多滞后下列秒数生效。
    // 首页 / 不做 swr：首页栏目(板块)依赖后台「首页配置」，swr 会让配置改动滞后到缓存过期才生效
    // (表现为"保存后不立即生效、第二次改还返回上次值")。首页改为每次实时 SSR，后端 Redis 缓存(保存即清)仍保证速度。
    // 文章详情 /article/** 同理不做 swr：后台编辑保存后需「实时」生效(标题/正文立即同步)，
    // swr:60 会让编辑后旧 HTML 滞留到缓存过期(且叠加 SW 页面缓存表现为更久)，出现"过几分钟才更新、标题不同步"。
    // 详情接口本身每次请求都自增浏览量(无后端缓存)，实时 SSR 直接读库即可，速度足够。
    // 分类/标签页有路径参数、站内排序和客户端切换，不能用 SWR 复用旧 HTML/payload。
    '/dynamics': { swr: 30 },
    // 后台(/admin)放开浏览器定位权限：高德「获取当前定位」需要 geolocation。
    // 比下面 /** 更具体，优先生效。
    [`${adminBasePath}/**`]: {
      headers: {
        'Permissions-Policy': 'geolocation=(self), microphone=(), camera=()',
        // 后台专用 CSP（含 'unsafe-eval'）：高德 JS API 2.0 WebGL 渲染器需要，否则地图一直「加载中」。
        // 比下面 /** 更具体，Nitro 对同名 header 以更具体路由为准。
        ...adminCspHeader
      }
    },
    '/**': {
      headers: {
        'X-Content-Type-Options': 'nosniff',
        'X-Frame-Options': 'SAMEORIGIN',
        'Referrer-Policy': 'strict-origin-when-cross-origin',
        'Permissions-Policy': 'geolocation=(), microphone=(), camera=()',
        // CSP：生产环境强制拦截(Content-Security-Policy)且不含 'unsafe-eval'，缩小 XSS 面；
        // 开发环境降级为 Report-Only 且保留 'unsafe-eval'，避免 Vite HMR(依赖 eval) 被拦截导致白屏。
        // 放行项目实际用到的外部源：心知天气 iframe、高德地图(amap)、逆地理(nominatim)；
        // Nuxt 需 style 'unsafe-inline'(inlineStyles 内联关键 CSS) 与 script 'unsafe-inline'(主题初始化内联脚本/水合)；
        // img 放行图床(https:)与 data:；connect 放行外部 API；worker/blob 供 PWA Service Worker。
        ...cspHeader
      }
    }
  },
  app: {
    head: {
      title: 'Blog',
      htmlAttrs: {
        lang: 'zh-CN'
      },
      meta: [{
        charset: 'utf-8'
      }, {
        name: 'viewport',
        content: 'width=device-width, initial-scale=1'
      }, {
        name: 'description',
        content: '记录技术、项目与生活'
      }, {
        name: 'theme-color',
        content: '#409eff'
      }],
      link: [{
        rel: 'icon',
        type: 'image/x-icon',
        href: '/favicon.ico'
      }, {
        // RSS 自动发现：浏览器/订阅器(Feedly 等)读到此 link 即可一键订阅。
        rel: 'alternate',
        type: 'application/rss+xml',
        title: 'Blog',
        href: '/feed.xml'
      }],
      script: [{
        innerHTML: ";(function(){try{var t=localStorage.getItem('theme');document.documentElement.dataset.theme=t==='dark'?'dark':'light';}catch(e){}})();",
        tagPosition: 'head'
      }]
    }
  },
  elementPlus: {
    importStyle: 'scss',
    config: {
      zIndex: 12000
    }
  },
  pwa: {
    registerType: 'autoUpdate',
    manifest: {
      name: 'Blog',
      short_name: 'Blog',
      description: 'Personal blog powered by Spring Boot and Nuxt',
      lang: 'zh-CN',
      theme_color: '#409eff',
      background_color: '#ffffff',
      display: 'standalone',
      start_url: '/',
      icons: [{
        src: '/pwa-192x192.png',
        sizes: '192x192',
        type: 'image/png'
      }, {
        src: '/pwa-512x512.png',
        sizes: '512x512',
        type: 'image/png'
      }, {
        src: '/pwa-maskable-512x512.png',
        sizes: '512x512',
        type: 'image/png',
        purpose: 'maskable'
      }]
    },
    workbox: {
      // 不预缓存 HTML 应用壳：在线访问始终走网络拿最新 HTML，避免更新后旧壳引用失效 chunk 导致页面不可交互。
      globPatterns: ['**/*.{js,css,svg,png,ico,woff2}'],
      // 不预缓存：① layout-interaction 遗留资源；② admin 后台整包(首访前台用不到，~3.4MB)；
      // 这样首次访问只预缓存前台首屏必需资源，不与关键请求抢带宽。
      globIgnores: ['**/layout-interaction/**', '**' + adminBasePath + '/**'],
      maximumFileSizeToCacheInBytes: 3145728,
      cleanupOutdatedCaches: true,
      clientsClaim: true,
      skipWaiting: true,
      navigateFallbackDenylist: [/^\/api/, new RegExp('^' + adminBasePath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')), /^\/uploads/, /^\/oauth/],
      runtimeCaching: [{
        urlPattern: ({
          url
        }) => ['/api', adminBasePath, '/uploads'].some(p => url.pathname.startsWith(p)),
        handler: 'NetworkOnly'
      }, {
        // 分类/标签页依赖路径参数和站内筛选，始终走网络，避免旧 Service Worker 复用上一个页面 HTML。
        urlPattern: ({
          url,
          request,
          sameOrigin
        }) => sameOrigin && ['/category', '/tags'].some(path => url.pathname.startsWith(path)) && request.mode === 'navigate',
        handler: 'NetworkOnly'
      }, {
        urlPattern: ({
          request,
          sameOrigin
        }) => sameOrigin && request.destination === 'image',
        handler: 'CacheFirst',
        options: {
          cacheName: 'images',
          expiration: {
            maxEntries: 200,
            maxAgeSeconds: 2592000
          }
        }
      }, {
        urlPattern: ({
          request,
          sameOrigin
        }) => sameOrigin && request.mode === 'navigate',
        handler: 'NetworkFirst',
        options: {
          cacheName: 'pages',
          networkTimeoutSeconds: 3
        }
      }, {
        urlPattern: ({
          request,
          sameOrigin
        }) => sameOrigin && ['style', 'script', 'font'].includes(request.destination),
        handler: 'StaleWhileRevalidate',
        options: {
          cacheName: 'assets'
        }
      }]
    },
    client: {
      installPrompt: true
    },
    devOptions: {
      enabled: false
    }
  },
  tailwindcss: {
    viewer: false
  },
  plugins: [{
    src: '~/plugins/directives.js',
    mode: 'client',
    ssr: false
  }],
  vite: {
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: '@use "~/assets/styles/variable.scss" as *;'
        }
      }
    },
    build: {
      // 提升内联资源阈值，减少首屏小资源的额外请求
      assetsInlineLimit: 4096,
      cssCodeSplit: true,
      rollupOptions: {
        output: {
          // 把重型第三方库拆成稳定的 vendor chunk：内容哈希不随业务码变动，
          // 提升浏览器/CDN 缓存命中。只命中 node_modules，不碰业务代码，
          // 不影响 Nuxt 基于页面的路由级懒加载边界。
          manualChunks(id: string) {
            if (!id.includes('node_modules')) return;
            if (id.includes('md-editor-v3')) return 'md-editor';
            if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus';
            if (id.includes('@lucide/vue') || id.includes('lucide')) return 'icons';
          }
        }
      }
    }
  },
  experimental: {
    // 提取 SSR payload 为静态 JSON，缩小首屏 HTML 体积、加快解析与水合
    payloadExtraction: true,
    // 复用渲染期已 fetch 的数据，避免水合后立刻二次请求
    headNext: true
  },
  features: {
    // 内联关键 CSS 写入 <head>，避免首屏样式额外往返
    inlineStyles: true
  }
});
