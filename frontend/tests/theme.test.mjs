import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
import { computed, nextTick, ref, watch } from 'vue';

function themeSession({ stored = null, dark = false, blocked = false } = {}) {
  const source = fs.readFileSync(new URL('../composables/useTheme.ts', import.meta.url), 'utf8');
  const utilityUrl = new URL('../utils/theme.ts', import.meta.url);
  const utility = fs.existsSync(utilityUrl) ? fs.readFileSync(utilityUrl, 'utf8') : '';
  const script = ts.transpileModule(`${utility}\n${source.replace(/^import .*from .*;?\r?\n/gm, '')}`, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext }
  }).outputText
    .replace(/^export /gm, '').replaceAll('import.meta.client', 'true');
  const mounted = [], unmounted = [], watchers = [], writes = [], states = new Map(), listeners = new Set();
  const media = {
    matches: dark,
    addEventListener(_type, listener) { listeners.add(listener); },
    removeEventListener(_type, listener) { listeners.delete(listener); }
  };
  const document = { documentElement: { dataset: {} } };
  const storage = {
    getItem() { if (blocked) throw new Error('Storage blocked'); return stored; },
    setItem(key, value) { if (blocked) throw new Error('Storage blocked'); writes.push([key, value]); stored = value; }
  };
  const context = {
    document, localStorage: storage, window: { matchMedia: () => media, localStorage: storage },
    useState(key, initial) { if (!states.has(key)) states.set(key, ref(initial())); return states.get(key); },
    onMounted(callback) { mounted.push(callback); }, onUnmounted(callback) { unmounted.push(callback); },
    computed,
    watch(value, callback) { watchers.push(watch(value, callback)); }
  };
  const api = vm.runInNewContext(`${script}\nuseTheme();`, context);
  return {
    api, document, writes, listeners,
    async mount() { mounted.forEach(callback => callback()); await nextTick(); },
    async toggle() { api.toggleTheme(); await nextTick(); },
    async system(value) { media.matches = value; listeners.forEach(listener => listener({ matches: value })); await nextTick(); },
    unmount() { unmounted.forEach(callback => callback()); watchers.forEach(stop => stop()); }
  };
}

test('mounted appearance follows the system without persisting an inferred choice', async () => {
  const session = themeSession({ dark: true });
  await session.mount();
  assert.equal(session.api.theme.value, 'dark');
  assert.equal(session.document.documentElement.dataset.theme, 'dark');
  assert.deepEqual(session.writes, []);
});

test('system changes update an unselected appearance and stop after unmount', async () => {
  const session = themeSession();
  await session.mount();
  await session.system(true);
  assert.equal(session.api.isDark.value, true);
  await session.system(false);
  assert.equal(session.api.isDark.value, false);
  session.unmount();
  assert.equal(session.listeners.size, 0);
});

test('saved explicit theme and a manual toggle keep priority over later system changes', async () => {
  const session = themeSession({ stored: 'light', dark: true });
  await session.mount();
  assert.equal(session.api.isDark.value, false);
  await session.system(false);
  await session.system(true);
  assert.equal(session.api.isDark.value, false);
  await session.toggle();
  assert.equal(session.api.isDark.value, true);
  assert.deepEqual(session.writes, [['theme', 'dark']]);
  await session.system(false);
  assert.equal(session.api.isDark.value, true);
});

test('blocked browser storage still allows system default and a manual toggle', async () => {
  const session = themeSession({ blocked: true, dark: true });
  await session.mount();
  assert.equal(session.api.isDark.value, true);
  await assert.doesNotReject(() => session.toggle());
  assert.equal(session.api.isDark.value, false);
  await session.system(false);
  await session.system(true);
  assert.equal(session.api.isDark.value, false);
});
