import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { ref, reactive, computed, watch } from 'vue';

const source = fs.readFileSync(new URL('../src/views/Message/index.vue', import.meta.url), 'utf8');
function page() {
  const operations = [];
  const context = {
    ref, reactive, computed, watch, onMounted() {},
    message: { success() {}, error() {} },
    approveMessage: async id => operations.push(['article-approve', id]),
    rejectMessage: async id => operations.push(['article-reject', id]),
    approveDynamicComment: async id => operations.push(['dynamic-approve', id]),
    rejectDynamicComment: async id => operations.push(['dynamic-reject', id]),
    getDynamicCommentList: async () => ({ items: [{ id: 11, status: 'APPROVED', visible: true }], totalItems: 1 }),
    getCommentSummary: async () => ({}), getMessageList: async () => ({ items: [], totalItems: 0 })
  };
  const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import[\s\S]*?from ['"][^'"]+['"];?\r?\n/gm, '');
  vm.runInNewContext(`${script}\nglobalThis.page = { commentType, approveComment, rejectComment, commentList };`, context);
  context.page.commentType.value = 'dynamic';
  return { ...context.page, operations };
}

test('approving a dynamic comment uses dynamic moderation and reloads its server status', async () => {
  const p = page(); await p.approveComment({ id: 11, status: 'PENDING' });
  assert.deepEqual(p.operations, [['dynamic-approve', 11]]);
  assert.equal(p.commentList.value[0].status, 'APPROVED');
});

test('rejecting a dynamic comment uses dynamic moderation rather than the article ID namespace', async () => {
  const p = page(); await p.rejectComment({ id: 11 });
  assert.deepEqual(p.operations, [['dynamic-reject', 11]]);
});
