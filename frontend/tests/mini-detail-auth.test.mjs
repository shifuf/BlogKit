import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

function deferred() { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; }
function detailPage() {
  let definition;
  let session = null;
  const pending = [];
  const app = { isLoggedIn: () => Boolean(session?.tokenValue), getSession: () => session };
  const api = {
    fetchArticleDetail(id) { const request = deferred(); pending.push({ ...request, id, kind: 'detail' }); return request.promise; },
    unlockArticle(id) { const request = deferred(); pending.push({ ...request, id, kind: 'unlock' }); return request.promise; },
    fetchFavoriteStatus: async () => ({ liked: false, favorited: false }),
    fetchComments: async () => ({ items: [], totalItems: 0 })
  };
  vm.runInNewContext(fs.readFileSync(new URL('../../miniprogram/pages/detail/detail.js', import.meta.url), 'utf8'), {
    Page: value => { definition = value; }, getApp: () => app,
    require: id => id.includes('services/blog') ? api : { normalizeArticle: x => x, normalizeArticleDetail: x => x, normalizeComment: x => x },
    wx: { setNavigationBarTitle() {}, showToast() {}, getStorageSync() {}, setStorageSync() {} }
  });
  const page = { ...definition, data: { ...definition.data, id: 1 }, setData(patch) {
    for (const [key, value] of Object.entries(patch)) {
      const parts = key.split('.');
      if (parts.length === 1) this.data[key] = value;
      else if (this.data[parts[0]]) this.data[parts[0]][parts[1]] = value;
    }
  } };
  page.refreshSession();
  return { page, pending, api, login(token = 'fixture-token') { session = { tokenName: 'Authorization', tokenValue: token }; }, logout() { session = null; } };
}

test('mini login onShow reloads server-authorized article', async () => {
  const fixture = detailPage();
  fixture.page.data.article = { locked: true, content: '' };
  fixture.login();
  const shown = fixture.page.onShow();
  assert.equal(fixture.pending.length, 1);
  fixture.pending[0].resolve({ id: 1, locked: false, content: 'authorized body' });
  await shown;
  assert.equal(fixture.page.data.article.content, 'authorized body');
});
test('mini logout clears prior private body and comments before anonymous refresh completes', async () => {
  const fixture = detailPage(); fixture.login(); fixture.page.refreshSession();
  fixture.page.data.article = { id: 1, visibility: 'PRIVATE', content: 'private body' };
  fixture.page.data.comments = [{ content: 'private comment' }];
  fixture.logout();
  const shown = fixture.page.onShow();
  assert.equal(fixture.page.data.article, null);
  assert.equal(fixture.page.data.comments.length, 0);
  fixture.pending[0].resolve({ id: 1, locked: true, content: '' }); await shown;
  assert.equal(fixture.page.data.article.locked, true);
});
for (const kind of ['detail', 'unlock']) {
  test(`mini ${kind} rejects a late authorized response after logout`, async () => {
    const fixture = detailPage(); fixture.login(); fixture.page.refreshSession();
    fixture.page.data.unlockPassword = 'fixture-password';
    const request = kind === 'detail' ? fixture.page.loadArticle() : fixture.page.submitUnlock();
    fixture.logout(); fixture.page.refreshSession();
    fixture.pending[0].resolve({ id: 1, content: 'private body' }); await request;
    assert.equal(fixture.page.data.article, null);
  });
  test(`mini ${kind} rejects a response for an older article route`, async () => {
    const fixture = detailPage(); fixture.page.data.unlockPassword = 'fixture-password';
    const request = kind === 'detail' ? fixture.page.loadArticle() : fixture.page.submitUnlock();
    fixture.page.data.id = 2; fixture.page.data.article = { id: 2, content: 'article two' };
    fixture.pending[0].resolve({ id: 1, content: 'article one' }); await request;
    assert.equal(fixture.page.data.article.id, 2);
  });
}
test('mini latest article request wins and unloaded pages cannot accept responses', async () => {
  const fixture = detailPage();
  const first = fixture.page.loadArticle(); const latest = fixture.page.loadArticle();
  fixture.pending[1].resolve({ id: 1, content: 'latest' }); await latest;
  fixture.pending[0].resolve({ id: 1, content: 'old' }); await first;
  assert.equal(fixture.page.data.article.content, 'latest');
  const removed = fixture.page.loadArticle(); fixture.page.onUnload();
  fixture.pending[2].resolve({ id: 1, content: 'removed' }); await removed;
  assert.equal(fixture.page.data.article.content, 'latest');
});
test('mini successful password unlock still displays authorized detail', async () => {
  const fixture = detailPage(); fixture.page.data.unlockPassword = 'fixture-password';
  const request = fixture.page.submitUnlock();
  fixture.pending[0].resolve({ id: 1, locked: false, content: 'password authorized' }); await request;
  assert.equal(fixture.page.data.article.content, 'password authorized');
  assert.equal(fixture.page.data.unlocking, false);
});
test('mini comments pending at logout cannot repopulate cleared private comments', async () => {
  const fixture = detailPage(); fixture.login(); fixture.page.refreshSession();
  fixture.page.data.commentsEnabled = true;
  const comments = deferred(); fixture.api.fetchComments = () => comments.promise;
  const request = fixture.page.loadComments();
  fixture.logout(); fixture.page.refreshSession();
  comments.resolve({ items: [{ id: 1, content: 'private comment' }], totalItems: 1 }); await request;
  assert.equal(fixture.page.data.comments.length, 0);
  assert.equal(fixture.page.data.loadingComments, false);
});
test('mini approved comment refreshes server-filtered hidden body', async () => {
  const fixture = detailPage(); fixture.login(); fixture.page.refreshSession();
  fixture.page.data.commentsEnabled = true;
  fixture.page.data.article = { id: 1, content: 'filtered body', commentCount: 0 };
  fixture.page.data.commentContent = 'hello';
  fixture.api.createComment = async () => ({ id: 3, status: 'APPROVED', content: 'hello' });
  const request = fixture.page.submitComment(); await new Promise(r => setImmediate(r));
  assert.equal(fixture.pending.length, 1);
  fixture.pending[0].resolve({ id: 1, content: 'now authorized body', commentCount: 1 }); await request;
  assert.equal(fixture.page.data.article.content, 'now authorized body');
});
test('mini reusing article id after navigation still rejects the earlier route generation', async () => {
  const fixture = detailPage();
  const request = fixture.page.loadArticle();
  fixture.page.loadAll = () => {};
  fixture.page.onLoad({ id: '2' }); fixture.page.onLoad({ id: '1' });
  fixture.pending[0].resolve({ id: 1, content: 'old route body' }); await request;
  assert.equal(fixture.page.data.article, null);
});
