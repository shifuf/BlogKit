import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import Components from 'unplugin-vue-components/vite';
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers';
import { fileURLToPath, URL } from 'node:url';

function normalizeAdminBasePath(raw) {
  const value = String(raw || '/admin').trim() || '/admin';
  const withSlash = value.startsWith('/') ? value : `/${value}`;
  return withSlash.replace(/\/+$/, '') || '/admin';
}

export default defineConfig(({ mode }) => {
  // Load env from dashboard package and repo root; only BLOG_API_PAYLOAD_SECRET is required.
  const rootEnv = loadEnv(mode, fileURLToPath(new URL('..', import.meta.url)), '');
  const localEnv = loadEnv(mode, process.cwd(), '');
  const backendOrigin = localEnv.BLOG_BACKEND_ORIGIN
    || rootEnv.BLOG_BACKEND_ORIGIN
    || process.env.BLOG_BACKEND_ORIGIN
    || 'http://localhost:8080';
  const payloadSecret = localEnv.VITE_API_PAYLOAD_SECRET
    || rootEnv.VITE_API_PAYLOAD_SECRET
    || localEnv.BLOG_API_PAYLOAD_SECRET
    || rootEnv.BLOG_API_PAYLOAD_SECRET
    || process.env.VITE_API_PAYLOAD_SECRET
    || process.env.BLOG_API_PAYLOAD_SECRET
    || '';
  const adminBasePath = normalizeAdminBasePath(
    localEnv.BLOG_ADMIN_BASE_PATH
    || rootEnv.BLOG_ADMIN_BASE_PATH
    || process.env.BLOG_ADMIN_BASE_PATH
    || '/admin'
  );

  return {
    base: `${adminBasePath}/`,
    plugins: [vue({
      template: { compilerOptions: { isCustomElement: tag => tag === 'music-player' } }
    }), Components({
      dirs: ['src/components'],
      globalNamespaces: [],
      transformer: 'vue3',
      resolvers: [AntDesignVueResolver({
        importStyle: false
      })],
      dts: true
    })],
    define: {
      'import.meta.env.VITE_API_PAYLOAD_SECRET': JSON.stringify(payloadSecret),
      'import.meta.env.VITE_ADMIN_BASE_PATH': JSON.stringify(adminBasePath)
    },
    server: {
      port: 8088,
      proxy: {
        '/api': {
          target: backendOrigin,
          changeOrigin: true
        },
        '/uploads': {
          target: backendOrigin,
          changeOrigin: true
        }
      }
    },
    build: {
      chunkSizeWarningLimit: 700,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) return;
            if (id.includes('node_modules/md-editor-v3')) return 'md-editor';
            if (id.includes('node_modules/ant-design-vue')) return 'antd';
            if (id.includes('node_modules/@ant-design/') || id.includes('node_modules/@vicons/')) return 'icons';
            return 'vendor';
          }
        }
      }
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    }
  };
});