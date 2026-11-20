import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
import { ref, computed } from 'vue';

async function headerState(series, settings = {}) {
  const source = fs.readFileSync(new URL('../components/blog/SiteHeader.vue', import.meta.url), 'utf8');
  const raw = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
  const script = ts.transpileModule(raw, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
    .replace(/^import .*;\r?\n/gm, '').replace(/^export \{\};?\r?\n/gm, '').replace(/import\.meta\.client/g, 'false');
  const context = { ref, computed, watch() {}, onMounted() {}, onUnmounted() {},
    useRoute: () => ({ path: '/', fullPath: '/' }), useStore: { user: () => ({ isAuthenticated: false }) },
    useTheme: () => ({}), useLoginModal: () => ({}), useState: (_key, factory) => ref(factory()),
    useSiteSettings: () => ({ siteSettings: ref(settings), loadSiteSettings: async () => {} }),
    usePublicCategories: async () => ({ data: ref([]) }),
    useAsyncData: async (_key, load) => ({ data: ref(await load()) }),
    useApiClient: () => ({ fetchSeries: async () => ({ success: true, data: series }) }),
    siteMeta: {}, buildAvatarUrl: value => value };
  return vm.runInNewContext(`(async () => { ${script}\nreturn { navigation: navItems.value, introductionImage: drawerHero.value }; })()`, context);
}
test('empty public series response does not add a phantom navigation entry', async () => {
  assert.equal((await headerState([])).navigation.some(item => item.to === '/series'), false);
});
test('a series returned by the public API enables the series navigation', async () => {
  assert.equal((await headerState([{ id: 1, name: 'Actual series', slug: 'actual' }])).navigation.filter(item => item.to === '/series').length, 1);
});
test('mobile introduction uses the configured author image when no home banner is configured', async () => {
  const state = await headerState([], { profileAvatarUrl: '/uploads/author.jpg', homeHeroImageUrl: '' });
  assert.equal(state.introductionImage, '/uploads/author.jpg');
});
test('mobile introduction keeps an explicitly configured banner ahead of the author image', async () => {
  const state = await headerState([], { profileAvatarUrl: '/uploads/author.jpg', homeHeroImageUrl: '/uploads/banner.jpg' });
  assert.equal(state.introductionImage, '/uploads/banner.jpg');
});
