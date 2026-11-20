import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
import { ref, reactive, computed, watch } from 'vue';

const source = fs.readFileSync(new URL('../pages/dynamics.vue', import.meta.url), 'utf8');
const script = source.slice(source.indexOf('// 互动：'), source.indexOf('// 首屏列表'));
const code = ts.transpileModule(script, { compilerOptions: { target: ts.ScriptTarget.ESNext } }).outputText
  .replaceAll('import.meta.client', 'true').replace(/export \{\};?/g, '');
const deferred = () => { let resolve; const promise = new Promise(yes => { resolve = yes; }); return { promise, resolve }; };
const item = { id: 1, likeCount: 0, commentCount: 3 };
const comment = (id, status = 'APPROVED', mine = false) => ({ id, status, mine, authorName: 'same name', content: `comment ${id}`, replies: [], replyCount: 0 });
const response = (items, totalItems = items.length) => ({ success: true, data: { items, totalItems } });

function page(overrides = {}) {
  const timers = new Map();
  const cleanups = [];
  let nextTimer = 0;
  const context = {
    ref, reactive, computed, watch, AbortController,
    userStore: reactive({ accessToken: '', tokenName: 'Authorization', isAuthenticated: false }),
    onBeforeUnmount: callback => cleanups.push(callback),
    onUnmounted: callback => cleanups.push(callback),
    setTimeout: callback => { const id = ++nextTimer; timers.set(id, callback); return id; },
    clearTimeout: id => timers.delete(id),
    getGuestKey: () => 'untrusted-key',
    localStorage: { getItem: () => null, setItem() {} },
    fetchDynamicComments: async () => response([]),
    fetchDynamicReplies: async () => response([]),
    fetchDynamicLikeStatus: async () => ({ success: true, data: { liked: false, likeCount: 0, commentCount: 3 } }),
    postDynamicComment: async () => ({ success: true, data: comment(9, 'PENDING', true) }),
    fetchDynamics: async () => response([item]),
    ElMessage: { success() {}, error() {} },
    ...overrides
  };
  vm.runInNewContext(`${code}\nglobalThis.page = { ensureEngagement, loadComments, loadDynamicReplies, toggleComments, submitComment, submitReply, openReply,
    isSelfDynamicComment, rememberedGuestName };`, context);
  return { ...context.page, context, timers, cleanups };
}

test('dynamic ownership uses the server mine flag when two visitors use the same name', () => {
  const p = page(); p.rememberedGuestName.value = 'same name';
  assert.equal(p.isSelfDynamicComment(comment(1, 'PENDING', true)), true);
  assert.equal(p.isSelfDynamicComment(comment(2, 'APPROVED', false)), false);
});

test('submitting a pending dynamic comment echoes it without increasing the public count', async () => {
  const p = page(); const state = p.ensureEngagement(item);
  state.open = true; state.form.content = 'pending';
  await p.submitComment(item);
  assert.equal(state.comments[0].status, 'PENDING');
  assert.equal(state.commentCount, 3);
});

test('reply paging preserves the own pending reply and replaces duplicate approved IDs', async () => {
  const approved = comment(2); const pending = comment(9, 'PENDING', true);
  const p = page({ fetchDynamicReplies: async () => response([{ ...approved, content: 'updated' }], 2) });
  const state = p.ensureEngagement(item); state.open = true;
  const root = comment(1); root.replyCount = 1; root.replies = [approved, pending]; state.comments = [root];
  await p.loadDynamicReplies(item, state.comments[0]);
  assert.equal(state.comments[0].replies.length, 2);
  assert.equal(state.comments[0].replies.find(c => c.id === 9).status, 'PENDING');
  assert.equal(state.comments[0].replies.find(c => c.id === 2).content, 'updated');
  assert.equal(state.comments[0].replyCount, 1, 'Viewer-specific total is separate from public reply count');
});

test('a root appearing again during pagination is updated exactly once', async () => {
  const p = page({ fetchDynamicComments: async () => response([{ ...comment(1), content: 'updated' }], 2) });
  const state = p.ensureEngagement(item); state.open = true; state.comments = [comment(1)]; state.commentsPage = 1;
  await p.loadComments(item, true);
  assert.equal(state.comments.length, 1); assert.equal(state.comments[0].content, 'updated');
});

test('closing a dynamic comment panel invalidates an in-flight private response', async () => {
  const pending = deferred(); const p = page({ fetchDynamicComments: () => pending.promise });
  const opening = p.toggleComments(item); await p.toggleComments(item);
  pending.resolve(response([comment(9, 'PENDING', true)])); await opening;
  assert.equal(p.ensureEngagement(item).comments.length, 0);
});

test('a comment response from before an auth change cannot repopulate private comments', async () => {
  const pending = deferred(); let calls = 0;
  const p = page({ fetchDynamicComments: () => calls++ === 0 ? pending.promise : Promise.resolve(response([])) });
  const state = p.ensureEngagement(item); state.open = true;
  const loading = p.loadComments(item);
  p.context.userStore.accessToken = 'another-user';
  pending.resolve(response([comment(9, 'PENDING', true)])); await loading;
  assert.equal(state.comments.length, 0);
});

test('own pending comments start bounded polling and closing the panel clears its timer', async () => {
  const p = page(); const state = p.ensureEngagement(item); state.open = true; state.form.content = 'pending';
  await p.submitComment(item);
  assert.equal(p.timers.size, 1);
  await p.toggleComments(item); assert.equal(p.timers.size, 0);
});

test('unmount cleans up pending status timers and discards late replies', async () => {
  const pending = deferred(); const p = page({ fetchDynamicReplies: () => pending.promise });
  const state = p.ensureEngagement(item); state.open = true; state.form.content = 'pending';
  await p.submitComment(item);
  const root = comment(1); state.comments.push(root);
  const loading = p.loadDynamicReplies(item, state.comments.find(c => c.id === 1));
  assert.ok(p.cleanups.length, 'The component must register lifecycle cleanup');
  for (const cleanup of p.cleanups) cleanup();
  pending.resolve(response([comment(2)])); await loading;
  assert.equal(p.timers.size, 0); assert.equal(root.replies.length, 0);
});

async function tick(p) {
  const [id, callback] = p.timers.entries().next().value;
  p.timers.delete(id);
  await callback();
}

test('a list request started before submission cannot remove the newly saved pending comment', async () => {
  const pending = deferred(); const p = page({ fetchDynamicComments: () => pending.promise });
  const state = p.ensureEngagement(item); state.open = true;
  const loading = p.loadComments(item); state.form.content = 'new pending'; await p.submitComment(item);
  pending.resolve(response([])); await loading;
  assert.equal(state.comments.length, 1); assert.equal(state.comments[0].id, 9); assert.equal(state.loadingComments, false);
});

test('polling applies an approval once and keeps public counts separate from pending comments', async () => {
  const p = page({ fetchDynamicComments: async () => response([comment(9, 'APPROVED', true), comment(10, 'PENDING', true)]),
    fetchDynamicLikeStatus: async () => ({ success: true, data: { liked: false, likeCount: 0, commentCount: 4 } }) });
  const state = p.ensureEngagement(item); state.open = true; state.form.content = 'new pending';
  await p.submitComment(item); state.comments.push(comment(10, 'PENDING', true));
  await tick(p); assert.equal(state.commentCount, 4); assert.equal(state.comments[0].status, 'APPROVED');
  await tick(p); assert.equal(state.commentCount, 4);
});

test('polling stops after its bounded attempts when the AI result stays pending', async () => {
  let refreshes = 0;
  const p = page({ fetchDynamicComments: async () => { refreshes++; return response([comment(9, 'PENDING', true)]); } });
  const state = p.ensureEngagement(item); state.open = true; state.form.content = 'new pending'; await p.submitComment(item);
  for (let n = 0; n < 40 && p.timers.size; n++) await tick(p);
  assert.equal(refreshes, 36); assert.equal(p.timers.size, 0);
});

test('polling for another pending comment retains replies the visitor already paged open', async () => {
  const replies = Array.from({ length: 11 }, (_, n) => comment(20 + n));
  const root = { ...comment(1), replyCount: 11, replies: replies.slice(0, 2) };
  const p = page({
    fetchDynamicComments: async () => response([comment(9, 'PENDING', true), root]),
    fetchDynamicReplies: async (_id, _root, number) => response(replies.slice(number * 10, (number + 1) * 10), 11)
  });
  const state = p.ensureEngagement(item); state.open = true; state.comments = [{ ...root }];
  await p.loadDynamicReplies(item, state.comments[0]); await p.loadDynamicReplies(item, state.comments[0]);
  state.form.content = 'new pending'; await p.submitComment(item);
  await tick(p);
  assert.equal(state.comments.find(comment => comment.id === 1).replies.length, 11);
});

test('a poll reply page keeps the fresh own pending preview even when public replies fill that page', async () => {
  const root = { ...comment(1), replyCount: 10, replies: [comment(9, 'PENDING', true)] };
  const p = page({ fetchDynamicComments: async () => response([root]),
    fetchDynamicReplies: async () => response(Array.from({ length: 10 }, (_, n) => comment(20 + n)), 11) });
  const state = p.ensureEngagement(item); state.open = true;
  await p.loadComments(item); await tick(p);
  assert.equal(state.comments[0].replies.find(comment => comment.id === 9)?.status, 'PENDING');
  assert.equal(p.timers.size, 1);
});

test('polling takes the authoritative public count when an approved reply moves outside the loaded preview', async () => {
  const pending = comment(9, 'PENDING', true);
  const root = { ...comment(1), replyCount: 3, replies: [pending] };
  let first = true;
  const p = page({ fetchDynamicComments: async () => {
    const current = first ? root : { ...root, replyCount: 8, replies: [comment(20), comment(21)] };
    first = false; return response([current]);
  }, fetchDynamicReplies: async () => response([comment(20), comment(21)], 8),
  fetchDynamicLikeStatus: async () => ({ success: true, data: { liked: false, likeCount: 0, commentCount: 9 } }) });
  const state = p.ensureEngagement(item); state.open = true;
  await p.loadComments(item); await tick(p);
  assert.equal(state.commentCount, 9);
});

test('reply paging updates the public reply count when a known pending reply is now approved', async () => {
  const pending = comment(9, 'PENDING', true);
  const p = page({ fetchDynamicReplies: async () => response([comment(9, 'APPROVED', true)], 2) });
  const state = p.ensureEngagement(item); state.open = true;
  state.comments = [{ ...comment(1), replyCount: 1, replies: [comment(2), pending] }];
  await p.loadDynamicReplies(item, state.comments[0]);
  assert.equal(state.comments[0].replyCount, 2); assert.equal(state.commentCount, 4);
});

test('reply approval under an owned rejected root never increases the public post count', async () => {
  const p = page({ fetchDynamicReplies: async () => response([comment(9, 'APPROVED', true)], 1) });
  const state = p.ensureEngagement(item); state.open = true;
  state.comments = [{ ...comment(1, 'REJECTED', true), replyCount: 0, replies: [comment(9, 'PENDING', true)] }];
  await p.loadDynamicReplies(item, state.comments[0]);
  assert.equal(state.commentCount, 3); assert.equal(state.comments[0].replyCount, 1);
});
