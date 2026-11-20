import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { ref, computed, nextTick } from 'vue';

const read = relative => fs.readFileSync(new URL(`../src/${relative}`, import.meta.url), 'utf8');
const stripImports = source => source.replace(/^import[\s\S]*?from ['"][^'"]+['"];?\r?\n/gm, '');
const plain = value => JSON.parse(JSON.stringify(value));
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
function toolbar(overrides = {}) {
  const searches = [], previews = [], errors = [], unmount = [];
  const context = {
    ref, computed, nextTick, onBeforeUnmount: fn => unmount.push(fn), defineEmits: () => () => {},
    message: { success() {}, warning() {}, error: text => errors.push(text) },
    searchMusic: async (keyword, platform) => { searches.push({ keyword, platform }); return []; },
    resolveMusic: async rid => ({ url: `/api/v1/music/stream?rid=${rid}`, candidates: [] }),
    window: { createPlainMusicPlayer: (container, track) => { previews.push(track); return { destroy() {} }; } },
    ...overrides
  };
  const script = read('views/Article/components/MusicToolbar.vue').match(/<script setup>([\s\S]*?)<\/script>/)[1];
  vm.runInNewContext(`${stripImports(script)}
    globalThis.toolbar = { open, close, doSearch, togglePreview, stopPreview, visible, keyword, loading, searched,
      list, selectedIndex, previewIndex, previewLoading, previewError, previewContainer,
      searchError: typeof searchError === 'undefined' ? undefined : searchError,
      platform: typeof platform === 'undefined' ? undefined : platform,
      changePlatform: typeof changePlatform === 'undefined' ? undefined : changePlatform };`, context);
  context.toolbar.previewContainer.value = {};
  return { ...context.toolbar, searches, previews, errors, unmount };
}

test('search wrapper forwards platform and defaults old callers to all platforms', async () => {
  const calls = [];
  const context = { request: { get: async (path, config) => { calls.push({ path, config }); return []; } } };
  vm.runInNewContext(`${stripImports(read('api/Music/index.js')).replace(/\bexport /g, '')}
    globalThis.search = searchMusic;`, context);
  await context.search('稻香', 'qq');
  await context.search('晴天');
  assert.deepEqual(plain(calls.map(call => call.config.params)), [
    { keyword: '稻香', platform: 'qq' }, { keyword: '晴天', platform: 'all' }
  ]);
});

test('platform change clears selected results and preview then searches the current keyword', async () => {
  const p = toolbar();
  await p.open();
  p.keyword.value = '  稻香  ';
  p.list.value = [{ rid: 'old', name: '旧结果' }];
  p.selectedIndex.value = 0;
  p.previewIndex.value = 0;
  assert.equal(p.platform?.value, 'all');
  p.platform.value = 'netease';
  await p.changePlatform();
  assert.equal(p.selectedIndex.value, -1);
  assert.equal(p.previewIndex.value, -1);
  assert.deepEqual(plain(p.list.value), []);
  assert.deepEqual(plain(p.searches), [{ keyword: '稻香', platform: 'netease' }]);
});

test('an older search cannot replace newer platform results', async () => {
  const first = deferred(), second = deferred();
  let calls = 0;
  const p = toolbar({ searchMusic: () => (++calls === 1 ? first.promise : second.promise) });
  await p.open();
  p.keyword.value = '稻香';
  const oldSearch = p.doSearch();
  p.platform.value = 'netease';
  const newSearch = p.changePlatform();
  second.resolve([{ rid: 'new', sourceName: '网易云音乐' }]);
  await newSearch;
  first.resolve([{ rid: 'old', sourceName: 'QQ 音乐' }]);
  await oldSearch;
  assert.deepEqual(plain(p.list.value), [{ rid: 'new', sourceName: '网易云音乐' }]);
});

test('the current provider error stays distinct from a successful empty search', async () => {
  let fail = true;
  const p = toolbar({ searchMusic: async () => { if (fail) throw new Error('当前平台暂不可用'); return []; } });
  await p.open();
  p.keyword.value = '稻香';
  await p.doSearch();
  assert.equal(p.searchError?.value, '当前平台暂不可用');
  fail = false;
  await p.doSearch();
  assert.equal(p.searchError.value, '');
  assert.deepEqual(plain(p.list.value), []);
});

test('an older search completion cannot clear the current loading indicator or report an obsolete error', async () => {
  const first = deferred(), second = deferred();
  let calls = 0;
  const p = toolbar({ searchMusic: () => (++calls === 1 ? first.promise : second.promise) });
  await p.open();
  p.keyword.value = '稻香';
  const oldSearch = p.doSearch();
  const newSearch = p.doSearch();
  first.reject(new Error('old provider unavailable'));
  await oldSearch;
  assert.equal(p.loading.value, true);
  assert.deepEqual(p.errors, []);
  second.resolve([]);
  await newSearch;
  assert.equal(p.loading.value, false);
});

test('closing and reopening the modal invalidates a pending search', async () => {
  const pending = deferred();
  const p = toolbar({ searchMusic: () => pending.promise });
  await p.open();
  p.keyword.value = '稻香';
  const searching = p.doSearch();
  p.close();
  await p.open();
  pending.resolve([{ rid: 'closed' }]);
  await searching;
  assert.deepEqual(plain(p.list.value), []);
  assert.equal(p.loading.value, false);
});

test('closing the modal while a preview resolves cannot start audio afterward', async () => {
  const pending = deferred();
  const p = toolbar({ resolveMusic: () => pending.promise });
  await p.open();
  p.list.value = [{ rid: 'old' }];
  const previewing = p.togglePreview(0);
  p.close();
  pending.resolve({ url: '/api/v1/music/stream?rid=old' });
  await previewing;
  assert.equal(p.previewIndex.value, -1);
  assert.deepEqual(p.previews, []);
});

test('changing platform invalidates a pending preview and ignores its late failure', async () => {
  const pending = deferred();
  const p = toolbar({ resolveMusic: () => pending.promise });
  await p.open();
  p.keyword.value = '稻香';
  p.list.value = [{ rid: 'old' }];
  const previewing = p.togglePreview(0);
  p.platform.value = 'kugou';
  await p.changePlatform();
  pending.reject(new Error('old preview failed'));
  await previewing;
  assert.equal(p.previewError.value, '');
  assert.deepEqual(p.errors, []);
});

test('stopping and restarting the same preview keeps only the newest response', async () => {
  const first = deferred(), second = deferred();
  let calls = 0;
  const p = toolbar({ resolveMusic: () => (++calls === 1 ? first.promise : second.promise) });
  await p.open();
  p.list.value = [{ rid: 'same' }];
  const oldPreview = p.togglePreview(0);
  p.stopPreview();
  const newPreview = p.togglePreview(0);
  second.resolve({ url: '/api/v1/music/stream?rid=new' });
  await newPreview;
  first.resolve({ url: '/api/v1/music/stream?rid=old' });
  await oldPreview;
  assert.deepEqual(p.previews.map(track => track.url), ['/api/v1/music/stream?rid=new']);
});
