import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { ref, reactive, computed, watch } from 'vue';
import { parse, compileScript } from '@vue/compiler-sfc';

const source = fs.readFileSync(new URL('../src/views/Message/index.vue', import.meta.url), 'utf8');
function page(overrides = {}) {
  const calls = [];
  const context = { ref, reactive, computed, watch, onMounted() {},
    message: { success() {}, error() {}, warning() {} },
    getCommentSummary: async () => ({}),
    getMessageList: async args => { calls.push(args); return { items: [], totalItems: 0 }; },
    replyMessage: async (id, content) => { calls.push({ id, content }); return { id: 3 }; }, ...overrides };
  const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import[\s\S]*?from ['"][^'"]+['"];?\r?\n/gm, '');
  vm.runInNewContext(`${script}\nglobalThis.page = { viewReplies, loadReplies, replyVisible, currentComment, replyList, replyPage, replyTotal, replyLoading,
    content: typeof adminReplyContent === 'undefined' ? undefined : adminReplyContent,
    submitting: typeof adminReplyLoading === 'undefined' ? undefined : adminReplyLoading,
    submit: typeof submitAdminReply === 'undefined' ? undefined : submitAdminReply };`, context);
  return { ...context.page, calls };
}
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; }

test('reply modal defines every state and handler used by its composer', () => {
  const { descriptor } = parse(source);
  const { bindings } = compileScript(descriptor, { id: 'comment-context' });
  for (const key of ['adminReplyContent', 'adminReplyLoading', 'submitAdminReply']) assert.ok(bindings[key], `Missing ${key}`);
  assert.equal(page().content.value, '');
});
test('opening a reply loads its root thread and clears the previous draft', async () => {
  const p = page(); assert.ok(p.content, 'Reply content must be initialized');
  p.content.value = 'old draft';
  await p.viewReplies({ id: 2, parentId: 1, content: 'reply', replyTo: 'parent' });
  assert.equal(p.content.value, ''); assert.equal(p.calls[0].parentId, 1); assert.equal(p.currentComment.value.id, 2);
});
test('blank reply is rejected and nonempty reply is trimmed and sent to the selected comment', async () => {
  const p = page(); assert.equal(typeof p.submit, 'function', 'Reply submit must exist');
  await p.viewReplies({ id: 2, parentId: 1 });
  p.content.value = '   '; await p.submit(); assert.ok(!p.calls.some(x => x.content));
  p.content.value = '  actual reply  '; await p.submit();
  assert.ok(p.calls.some(x => x.id === 2 && x.content === 'actual reply'));
  assert.equal(p.content.value, ''); assert.equal(p.submitting.value, false);
});
test('slow replies from a previously opened thread cannot overwrite the current context', async () => {
  const a = deferred(), b = deferred();
  const p = page({ getMessageList: args => args.parentId === 1 ? a.promise : b.promise });
  const first = p.viewReplies({ id: 1 }); const second = p.viewReplies({ id: 4 });
  b.resolve({ items: [{ id: 5 }], totalItems: 1 }); await second;
  a.resolve({ items: [{ id: 2 }], totalItems: 1 }); await first;
  assert.equal(p.replyList.value[0].id, 5); assert.equal(p.replyPage.value, 2);
});
test('successful submission preserves a newer draft typed while the request is pending', async () => {
  const pending = deferred();
  const p = page({ replyMessage: () => pending.promise });
  await p.viewReplies({ id: 2, parentId: 1 });
  p.content.value = 'submitted draft';
  const submitting = p.submit();
  p.content.value = 'new draft';
  pending.resolve({ id: 3 }); await submitting;
  assert.equal(p.content.value, 'new draft');
});
test('successful submission cannot clear a draft in a reopened context for the same comment', async () => {
  const pending = deferred();
  const p = page({ replyMessage: () => pending.promise });
  await p.viewReplies({ id: 2, parentId: 1 });
  p.content.value = 'same text';
  const submitting = p.submit();
  await p.viewReplies({ id: 2, parentId: 1 });
  p.content.value = 'same text';
  pending.resolve({ id: 3 }); await submitting;
  assert.equal(p.content.value, 'same text');
});
test('failed next page preserves replies and can retry the same page', async () => {
  let fail = true;
  const p = page({ getMessageList: async args => {
    if (args.pageNum === 2 && fail) { fail = false; throw new Error('temporary network failure'); }
    return { items: [{ id: args.pageNum }], totalItems: 2 };
  }});
  await p.viewReplies({ id: 10 });
  try { await p.loadReplies(p.currentComment.value, true); } catch {}
  assert.equal(p.replyPage.value, 2); assert.equal(p.replyList.value.length, 1);
  await p.loadReplies(p.currentComment.value, true); assert.equal(p.replyList.value.length, 2);
});
