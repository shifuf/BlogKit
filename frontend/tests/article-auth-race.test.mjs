import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
import { ref, computed } from 'vue';

const source = fs.readFileSync(new URL('../pages/article/[slug].vue', import.meta.url), 'utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const parsed = ts.createSourceFile('article.ts', source, ts.ScriptTarget.Latest, true);
function pageRequests() {
  const names = new Set(['detailRequestGeneration', 'unlockRequestGeneration', 'startDetailRequest', 'detailRequestIsCurrent', 'refreshDetailForAuth', 'submitUnlock']);
  const declarations = parsed.statements.filter(statement => ts.isVariableStatement(statement) && statement.declarationList.declarations.some(declaration => names.has(declaration.name.getText(parsed))));
  const code = ts.transpileModule(declarations.map(statement => statement.getText(parsed)).join('\n'), { compilerOptions: { target: ts.ScriptTarget.ESNext } }).outputText.replace(/import\.meta\.client/g, 'true').replace(/export \{\};?/g, '');
  const pending = [];
  const context = { ref, computed,
    userStore: { accessToken: 'authorized-token', tokenName: 'Authorization', isAuthenticated: true },
    slug: ref('1'), articleId: ref(1), isApiArticle: ref(true),
    pageData: ref({ article: { id: 1, locked: true, rawContent: '' }, categories: [], related: [] }),
    currentDetailData: ref({ article: null, categories: [], related: [] }),
    unlockPassword: ref('password'), unlocking: ref(false), toBlogArticle: detail => detail,
    ElMessage: { success() {}, error() {} },
    fetchArticleDetail() { return new Promise(resolve => pending.push(resolve)); },
    unlockArticle() { return new Promise(resolve => pending.push(resolve)); }
  };
  context.article = computed(() => context.pageData.value.article);
  vm.runInNewContext(`${code}\nglobalThis.requests = { refreshDetailForAuth, submitUnlock };`, context);
  return { ...context, pending };
}
for (const method of ['refreshDetailForAuth', 'submitUnlock']) {
  test(`${method} cannot apply authorized response after logout`, async () => {
    const page = pageRequests();
    const request = page.requests[method]();
    page.userStore.accessToken = '';
    page.userStore.isAuthenticated = false;
    page.pending[0]({ success: true, data: { id: 1, locked: false, rawContent: 'private body' } });
    await request;
    assert.equal(page.pageData.value.article.rawContent, '');
  });
  test(`${method} cannot replace a different article after navigation`, async () => {
    const page = pageRequests();
    const request = page.requests[method]();
    page.slug.value = '2'; page.articleId.value = 2;
    page.pageData.value.article = { id: 2, rawContent: 'article two' };
    page.pending[0]({ success: true, data: { id: 1, rawContent: 'article one' } });
    await request;
    assert.equal(page.pageData.value.article.id, 2);
  });
}
test('older detail refresh cannot overwrite the latest server-redacted response', async () => {
  const page = pageRequests();
  const old = page.requests.refreshDetailForAuth();
  const latest = page.requests.refreshDetailForAuth();
  page.pending[1]({ success: true, data: { id: 1, rawContent: 'latest filtered body' } }); await latest;
  page.pending[0]({ success: true, data: { id: 1, rawContent: 'stale private body' } }); await old;
  assert.equal(page.pageData.value.article.rawContent, 'latest filtered body');
});
