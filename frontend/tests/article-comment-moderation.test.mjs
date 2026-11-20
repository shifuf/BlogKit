import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
import { ref, reactive, computed, watch, nextTick } from 'vue';

const adapterSource = fs.readFileSync(new URL('../utils/blog-adapter.ts', import.meta.url), 'utf8');
const adapterCode = ts.transpileModule(adapterSource.slice(adapterSource.indexOf('export const toBlogComment'), adapterSource.indexOf('export const toFriendLink')),
  { compilerOptions: { target: ts.ScriptTarget.ESNext } }).outputText.replace('export const', 'const');
const adapter = { formatRelativeTime: () => 'now', normalizeOptionalAssetUrl: value => value || undefined };
vm.runInNewContext(`${adapterCode}\nglobalThis.convert = toBlogComment;`, adapter);
const convert = adapter.convert;
const raw = (id, status = 'APPROVED', mine = false) => ({ id, authorName: 'same name', articleId: 1, content: `comment ${id}`, status, mine, replies: [], replyCount: 0 });
const comment = (...args) => convert(raw(...args));
const response = (items, totalItems = items.length) => ({ success: true, data: { items, totalItems } });
const deferred = () => { let resolve; const promise = new Promise(yes => { resolve = yes; }); return { promise, resolve }; };
const source = fs.readFileSync(new URL('../components/blog/CommentSection.vue', import.meta.url), 'utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ESNext } }).outputText
  .replace(/^import .*;\r?\n/gm, '').replaceAll('import.meta.client', 'true').replace(/export \{\};?/g, '');

export function commentHarness(overrides = {}) {
  const timers = new Map(), mounts = [], cleanups = [];
  let nextTimer = 0;
  const props = reactive({ articleId: 1, commentsEnabled: true, interactionCount: 3 });
  const user = reactive({ isAuthenticated: false, accessToken: '', tokenName: 'Authorization', username: '', displayName: '' });
  const api = { fetchComments: async () => response([]), fetchCommentReplies: async () => response([]),
    createComment: async () => ({ success: true, data: raw(9, 'PENDING', true) }), ...overrides };
  const context = { ref, reactive, computed, watch, AbortController,
    defineProps: () => props, withDefaults: value => value, useStore: { user: () => user },
    useLoginModal: () => ({ openLogin() {} }), useApiClient: () => api,
    useSiteSettings: () => ({ siteSettings: ref({}), loadSiteSettings() {} }),
    onMounted: callback => mounts.push(callback), onBeforeUnmount: callback => cleanups.push(callback), onUnmounted: callback => cleanups.push(callback),
    setTimeout: callback => { const id = ++nextTimer; timers.set(id, callback); return id; }, clearTimeout: id => timers.delete(id),
    localStorage: { getItem: () => null, setItem() {} }, window: { dispatchEvent() {} }, CustomEvent: class {},
    ElMessage: { success() {}, warning() {}, error() {} }, ElMessageBox: { confirm: async () => true },
    MarkdownIt: class { render(value) { return value; } }, toBlogComment: convert,
    sanitizeCommentHtml: value => value, formatDateTime: value => value, formatRelativeTime: value => value
  };
  vm.runInNewContext(`${code}\nglobalThis.api = { comments, form, replyForm, activeReply, replyPages, commentsTotal, interactionTotal,
    isSelfComment, loadRemoteComments, loadMoreReplies, submitComment, submitReply, hasMoreComments };`, context);
  return { ...context.api, props, user, timers, mounts, cleanups, context };
}

test('adapter and article ownership honor server mine despite identical visitor names', () => {
  const p = commentHarness(); p.form.author = 'same name';
  assert.equal(p.isSelfComment(comment(1, 'PENDING', true)), true);
  assert.equal(p.isSelfComment(comment(2, 'APPROVED', false)), false);
});
test('pending root echoes without increasing public interactions and is included in viewer pagination', async () => {
  const p = commentHarness(); p.form.author = 'same name'; p.form.content = 'pending';
  await p.submitComment();
  assert.equal(p.comments.value[0].mine, true); assert.equal(p.comments.value[0].status, 'PENDING');
  assert.equal(p.interactionTotal.value, 3); assert.equal(p.commentsTotal.value, 1);
});
test('reply paging replaces duplicate IDs and retains own pending without replacing public replyCount', async () => {
  const p = commentHarness({ fetchCommentReplies: async () => response([{ ...raw(2), content: 'updated' }], 2) });
  const root = comment(1); root.replyCount = 1; root.replies = [comment(2), comment(9, 'PENDING', true)]; p.comments.value = [root];
  await p.loadMoreReplies(p.comments.value[0]);
  assert.equal(p.comments.value[0].replies.length, 2);
  assert.equal(p.comments.value[0].replies.find(c => c.id === 9).status, 'PENDING');
  assert.equal(p.comments.value[0].replies.find(c => c.id === 2).content, 'updated');
  assert.equal(p.comments.value[0].replyCount, 1);
});
test('a root repeated during paging is replaced once', async () => {
  const p = commentHarness({ fetchComments: async () => response([{ ...raw(1), content: 'updated' }], 1) });
  p.comments.value = [comment(1)]; await p.loadRemoteComments(true);
  assert.equal(p.comments.value.length, 1); assert.equal(p.comments.value[0].content, 'updated');
});
for (const change of ['article', 'auth']) test(`late private list is discarded after ${change} changes`, async () => {
  const pending = deferred(); const p = commentHarness({ fetchComments: () => pending.promise });
  const loading = p.loadRemoteComments();
  if (change === 'article') p.props.articleId = 2; else p.user.accessToken = 'another-user';
  pending.resolve(response([raw(9, 'PENDING', true)])); await loading; await nextTick();
  assert.equal(p.comments.value.length, 0);
});
test('late submit cannot insert a private comment into a different article', async () => {
  const pending = deferred(); const p = commentHarness({ createComment: () => pending.promise });
  p.form.author = 'name'; p.form.content = 'pending'; const sending = p.submitComment(); p.props.articleId = 2;
  pending.resolve({ success: true, data: raw(9, 'PENDING', true) }); await sending; await nextTick();
  assert.equal(p.comments.value.length, 0);
});
test('polling replaces pending status with approval and preserves replies already paged', async () => {
  const p = commentHarness({ fetchComments: async () => response([{ ...raw(1), replies: [raw(2)] }]),
    fetchCommentReplies: async () => response([raw(2), raw(3)]) });
  const root = comment(1); root.replies = [comment(2), comment(3)]; p.comments.value = [root];
  p.replyPages.value[1] = 1;
  await p.loadRemoteComments();
  assert.equal(p.comments.value[0].replies.length, 2);
});
test('a delayed reply page cannot skip replies after the root list is refreshed', async () => {
  const pending = deferred();
  const requested = [];
  const p = commentHarness({ fetchComments: async () => response([{ ...raw(1), replyCount: 20, replies: [raw(2), raw(3)] }]),
    fetchCommentReplies: (_id, page) => {
      requested.push(page);
      return requested.length === 1 ? pending.promise : Promise.resolve(response(Array.from({ length: 10 }, (_, i) => raw(2 + page * 10 + i)), 20));
    } });
  const root = comment(1); root.replies = [comment(2), comment(3)]; root.replyCount = 20;
  p.comments.value = [root];
  const loading = p.loadMoreReplies(p.comments.value[0]);
  await p.loadRemoteComments(false, true);
  pending.resolve(response(Array.from({ length: 10 }, (_, i) => raw(2 + i)), 20));
  await loading;
  await p.loadMoreReplies(p.comments.value[0]);
  assert.deepEqual(requested, [0, 0]);
  assert.deepEqual(Array.from(p.comments.value[0].replies, item => item.id), Array.from({ length: 10 }, (_, i) => 2 + i));
});
test('reply paging waits for an active list replacement before advancing its cursor', async () => {
  const pending = deferred(); const requested = [];
  const p = commentHarness({ fetchComments: () => pending.promise,
    fetchCommentReplies: async (_id, page) => { requested.push(page); return response([raw(2), raw(3), raw(4)], 3); } });
  const root = comment(1); root.replies = [comment(2), comment(3)]; p.comments.value = [root];
  const refreshing = p.loadRemoteComments(false, true);
  await p.loadMoreReplies(p.comments.value[0]);
  pending.resolve(response([{ ...raw(1), replyCount: 3, replies: [raw(2), raw(3)] }]));
  await refreshing;
  await p.loadMoreReplies(p.comments.value[0]);
  assert.deepEqual(requested, [0]);
  assert.deepEqual(Array.from(p.comments.value[0].replies, item => item.id), [2, 3, 4]);
});
test('polling keeps every own private reply when more were posted than the preview limit', async () => {
  let nextId = 9;
  const p = commentHarness({ createComment: async () => ({ success: true, data: raw(nextId++, 'PENDING', true) }),
    fetchComments: async () => response([{ ...raw(1), replies: [raw(9, 'PENDING', true), raw(10, 'PENDING', true)] }]),
    fetchCommentReplies: async () => response([raw(9, 'PENDING', true), raw(10, 'PENDING', true), raw(11, 'PENDING', true)], 3) });
  p.comments.value = [comment(1)];
  for (let i = 0; i < 3; i++) {
    p.activeReply.value = { rootId: 1, targetId: 1, targetAuthor: 'same name' };
    p.replyForm.author = 'same name'; p.replyForm.content = `own reply ${i}`;
    await p.submitReply();
  }
  assert.equal(p.comments.value[0].replies.length, 3);
  await p.loadRemoteComments(false, true);
  assert.deepEqual(Array.from(p.comments.value[0].replies, item => item.id), [9, 10, 11]);
});
test('own pending starts a status timer and unmount clears it and invalidates late replies', async () => {
  const pending = deferred(); const p = commentHarness({ fetchCommentReplies: () => pending.promise });
  p.form.author = 'name'; p.form.content = 'pending'; await p.submitComment();
  assert.equal(p.timers.size, 1);
  const root = comment(1); p.comments.value.push(root); const loading = p.loadMoreReplies(root);
  assert.ok(p.cleanups.length);
  p.cleanups.forEach(cleanup => cleanup()); pending.resolve(response([raw(2)])); await loading;
  assert.equal(p.timers.size, 0); assert.equal(root.replies.length, 0);
});
test('approval discovered during reply paging refreshes authoritative public counts', async () => {
  const rootResponse = { ...raw(1), replyCount: 2, replies: [raw(2), raw(9, 'APPROVED', true)] };
  const p = commentHarness({ fetchComments: async () => response([rootResponse]),
    fetchCommentReplies: async () => response([raw(2), raw(9, 'APPROVED', true)], 2) });
  const root = comment(1); root.replyCount = 1; root.replies = [comment(2), comment(9, 'PENDING', true)]; p.comments.value = [root];
  await p.loadMoreReplies(p.comments.value[0]);
  assert.equal(p.comments.value[0].replyCount, 2);
});
test('status polling stops after its budget while leaving the pending comment visible', async () => {
  const p = commentHarness({ fetchComments: async () => response([raw(9, 'PENDING', true)]) });
  p.form.author = 'name'; p.form.content = 'pending'; await p.submitComment();
  let ticks = 0;
  while (p.timers.size && ticks < 30) {
    const [id, callback] = p.timers.entries().next().value; p.timers.delete(id); await callback(); ticks++;
  }
  assert.equal(ticks, 24); assert.equal(p.timers.size, 0); assert.equal(p.comments.value[0].status, 'PENDING');
});
