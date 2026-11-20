import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

const source = fs.readFileSync(new URL('../composables/useApiClient.ts', import.meta.url), 'utf8');
const deferred = () => { let resolve; const promise = new Promise(yes => { resolve = yes; }); return { promise, resolve }; };
const flush = () => new Promise(resolve => setImmediate(resolve));
function harness(fetch, navigator = {}, client = true) {
  const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ESNext } }).outputText
    .replace(/^import .*;\r?\n/gm, '').replace('export const useApiClient', 'const useApiClient')
    .replaceAll('import.meta.client', String(client)).replaceAll('import.meta.server', String(!client));
  const context = { $fetch: fetch, navigator, useRuntimeConfig: () => ({ public: { apiBaseUrl: '/api/v1' } }),
    useStore: { user: () => ({}) }, useRequestHeaders: () => ({}) };
  vm.runInNewContext(`${code}\nglobalThis.create = useApiClient;`, context);
  return context.create;
}
test('all comment operations share one credential bootstrap across API instances before concurrent reads and posts', async () => {
  const bootstrap = deferred(); const calls = []; let cookie = null; let locks = 0;
  const create = harness(async (path, options) => {
    calls.push({ path, cookie, options });
    if (path === '/comments/identity') { await bootstrap.promise; cookie = 'server-issued'; }
    return { success: true, data: {} };
  }, { locks: { request: async (_name, callback) => { locks++; return callback(); } } });
  const a = create(), b = create();
  const requests = [a.fetchComments(1), b.fetchDynamicComments(2), a.createComment(1, 'pending'),
    b.postDynamicComment(2, { authorName: 'same', content: 'pending' }), a.fetchCommentReplies(3), b.fetchDynamicReplies(2, 4)];
  await flush();
  assert.deepEqual(calls.map(call => call.path), ['/comments/identity']);
  bootstrap.resolve(); await Promise.all(requests);
  assert.equal(locks, 1);
  assert.equal(calls.length, 7);
  assert.ok(calls.slice(1).every(call => call.cookie === 'server-issued' && call.options.credentials === 'include'));
});
test('separate first-use tabs serialize credential creation with the browser lock', async () => {
  let lockTail = Promise.resolve(); const calls = []; const firstBootstrap = deferred(); let cookie = null;
  const navigator = { locks: { request(_name, callback) { const current = lockTail.then(callback); lockTail = current.catch(() => {}); return current; } } };
  const fetch = async path => {
    calls.push({ path, cookie });
    if (path === '/comments/identity' && !cookie) { await firstBootstrap.promise; cookie = 'first-cookie'; }
    return { success: true, data: {} };
  };
  const first = harness(fetch, navigator)(), second = harness(fetch, navigator)();
  const requests = [first.fetchDynamicComments(1), second.createComment(1, 'pending')];
  await flush(); assert.equal(calls.length, 1);
  firstBootstrap.resolve(); await Promise.all(requests);
  assert.equal(calls.filter(call => call.path === '/comments/identity' && !call.cookie).length, 1);
  assert.ok(calls.filter(call => call.path !== '/comments/identity').every(call => call.cookie === 'first-cookie'));
});
test('aborting a waiter cannot cancel shared bootstrap or submit its obsolete request', async () => {
  const bootstrap = deferred(); const calls = []; const controller = new AbortController();
  const create = harness(async path => { calls.push(path); if (path === '/comments/identity') await bootstrap.promise; return { success: true }; });
  const api = create();
  const aborted = api.fetchComments(1, 0, 20, controller.signal);
  const other = api.fetchDynamicComments(2); controller.abort(); bootstrap.resolve();
  await assert.rejects(aborted, { name: 'AbortError' }); await other;
  assert.deepEqual(calls, ['/comments/identity', '/dynamics/2/comments']);
});
test('SSR comment requests never bootstrap an unforwarded browser cookie', async () => {
  const calls = []; const api = harness(async path => { calls.push(path); return { success: true }; }, {}, false)();
  await api.fetchComments(1);
  assert.deepEqual(calls, ['/articles/1/comments']);
});
test('failed identity initialization prevents a post and permits a later retry', async () => {
  const calls = []; let attempts = 0;
  const api = harness(async path => {
    calls.push(path);
    if (path === '/comments/identity' && ++attempts === 1) throw new Error('unavailable');
    return { success: true };
  })();
  await assert.rejects(api.createComment(1, 'pending'), /unavailable/);
  await api.createComment(1, 'pending');
  assert.deepEqual(calls, ['/comments/identity', '/comments/identity', '/comments']);
});
