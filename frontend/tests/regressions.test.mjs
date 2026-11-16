import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import path from 'node:path';
const root=path.resolve(import.meta.dirname,'../..');
function loadPage(relative, blog, app={isLoggedIn:()=>true,getSession:()=>({})}) {
  let definition;
  const context={require:id=>id.includes('services/blog')?blog:{normalizeArticle:x=>x,normalizeSiteSettings:x=>x,resolveAvatar:x=>x,formatDate:x=>x},
    Page:x=>definition=x, getApp:()=>app, wx:{setNavigationBarTitle(){},showToast(){},stopPullDownRefresh(){}}};
  vm.runInNewContext(fs.readFileSync(path.join(root,relative),'utf8'),context);
  return {...definition,data:{...definition.data},setData(patch){Object.assign(this.data,patch)}};
}
function deferred(){let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve};}
for(const kind of ['category','search'])test(`${kind} switching filters ignores stale response and requests new filter`,async()=>{
  const calls=[];const pending=[];
  const page=loadPage(`miniprogram/pages/${kind}/${kind}.js`,{fetchArticles(...args){calls.push(args);const d=deferred();pending.push(d);return d.promise}});
  page.data[kind==='category'?'slug':'keyword']='A';
  const a=page.refresh();
  page.data[kind==='category'?'slug':'keyword']='B';
  const b=page.refresh();
  assert.equal(calls.length,2,'new filter must not be dropped by old loading guard');
  pending[1].resolve({items:[{id:2}],totalPages:1});await b;
  pending[0].resolve({items:[{id:1}],totalPages:1});await a;
  assert.equal(page.data.articles[0].id,2,'old response must not overwrite B');
});
test('personal page waits for configuration before fetching first comments',async()=>{
  const settings=deferred();let count=0;
  const page=loadPage('miniprogram/pages/me/me.js',{fetchSiteSettings:()=>settings.promise,
    fetchProfile:async()=>({}),fetchMyFavoriteArticles:async()=>({items:[]}),fetchMyComments:async()=>{count++;return {items:[{id:1}]}}});
  const done=page.onShow();
  settings.resolve({commentsEnabled:true});await done;await new Promise(r=>setImmediate(r));
  assert.equal(count,1);assert.equal(page.data.comments.length,1);
});
