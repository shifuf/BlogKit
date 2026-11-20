import { test } from 'node:test';
import assert from 'node:assert/strict';
import { reactive, ref, nextTick, unref } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import { tabsStore } from '../src/stores/modules/tabs.js';
import { setupPage, nodes } from './helpers/vue-setup.mjs';

function routeHarness(overrides = {}) {
  const route = reactive({ name: 'articleEdit', path: '/articles/edit', fullPath: '/articles/edit?id=42', query: { id: '42' }, meta: { title: '文章编辑' }, ...overrides });
  const pushes = [], hooks = new Set();
  const router = { currentRoute: ref(route), push: target => { pushes.push(target); return Promise.resolve(); }, afterEach: callback => { hooks.add(callback); return () => hooks.delete(callback); } };
  return { route, router, pushes, hooks, useRouter: () => router, useRoute: () => route };
}

function tabsHarness(routeOverrides = {}) {
  setActivePinia(createPinia());
  const store = tabsStore();
  const route = routeHarness(routeOverrides);
  const page = setupPage('components/Layout/tabs/Tabs.vue', { ...route, tabsStore: () => store, sessionStorage: { getItem: () => null, setItem() {} }, window: { addEventListener() {}, removeEventListener() {} } });
  page.mount();
  return { ...page, ...route, store };
}

test('clicking the already selected editor menu closes the drawer without dropping its article query', async () => {
  const route = routeHarness();
  const emitted = [];
  const page = setupPage('components/Layout/menu/MenuBar.vue', { ...route, defineEmits: () => (...args) => emitted.push(args) });
  const menu = nodes(page.render()).find(node => node.type === 'a-menu');
  assert.equal(typeof menu.props.onClick, 'function', 'Selected items must react to a click, not only a changed selection');
  await menu.props.onClick({ key: 'articleEdit' });
  assert.equal(route.pushes.length, 0);
  assert.equal(route.route.fullPath, '/articles/edit?id=42');
  assert.deepEqual(emitted.map(event => event[0]), ['navigate']);
  page.unmount();
});

test('selecting a different menu destination navigates once and closes the mobile drawer', async () => {
  const route = routeHarness();
  const emitted = [];
  const page = setupPage('components/Layout/menu/MenuBar.vue', { ...route, defineEmits: () => (...args) => emitted.push(args) });
  const menu = nodes(page.render()).find(node => node.type === 'a-menu');
  assert.equal(typeof menu.props.onClick, 'function');
  await menu.props.onClick({ key: 'tags' });
  assert.equal(route.pushes.length, 1);
  assert.equal(route.pushes[0].name, 'tags');
  assert.deepEqual(emitted.map(event => event[0]), ['navigate']);
  page.unmount();
});

test('unmounting the sidebar releases its route listener rather than retaining popup state', () => {
  const route = routeHarness();
  const page = setupPage('components/Layout/menu/MenuBar.vue', { ...route, defineEmits: () => () => {} });
  page.unmount();
  assert.equal(route.hooks.size, 0);
});

test('collapsed sidebar unmounts the menu and mobile navigation closes an open sidebar', async () => {
  const route = routeHarness();
  const store = reactive({ getCollapse: true, setCollapse(value) { this.getCollapse = value; } });
  const page = setupPage('components/Layout/index.vue', { ...route, collapseStore: () => store });
  assert.equal(nodes(page.render()).filter(node => node.type === 'menu-bar').length, 0);
  page.setup.onBreakpoint(true);
  store.setCollapse(false);
  const menu = nodes(page.render()).find(node => node.type === 'menu-bar');
  assert.ok(menu);
  menu.props.onNavigate();
  assert.equal(store.getCollapse, true);
  store.setCollapse(false);
  route.route.path = '/tags';
  route.route.fullPath = '/tags';
  await nextTick();
  assert.equal(store.getCollapse, true);
  page.setup.onBreakpoint(false);
  nodes(page.render()).find(node => node.type === 'menu-bar').props.onNavigate();
  assert.equal(store.getCollapse, false, 'Desktop navigation keeps its sidebar open');
  page.unmount();
});

test('closing an inactive tab leaves the current editor route and active tab untouched', () => {
  const page = tabsHarness();
  page.store.tabsList = [{ path: '/', title: '工作台' }, { path: '/tags', title: '标签管理' }, { path: '/articles/edit', fullPath: '/articles/edit?id=42', title: '文章编辑' }];
  nodes(page.render()).find(node => node.type === 'a-tabs').props.onEdit('/tags', 'remove');
  assert.equal(page.pushes.length, 0);
  assert.equal(unref(page.setup.activeKey), '/articles/edit');
  assert.deepEqual(page.store.tabsList.map(tab => tab.path), ['/', '/articles/edit']);
  page.unmount();
});

test('closing an active tab restores its neighbor including article query parameters', () => {
  const page = tabsHarness({ name: 'tags', path: '/tags', fullPath: '/tags', query: {}, meta: { title: '标签管理' } });
  page.store.tabsList = [{ path: '/', title: '工作台' }, { path: '/articles/edit', fullPath: '/articles/edit?id=42', title: '文章编辑' }, { path: '/tags', title: '标签管理' }];
  page.setup.remove('/tags');
  assert.equal(page.pushes.length, 1);
  const target = page.pushes[0];
  assert.equal(typeof target === 'string' ? target : target.path, '/articles/edit?id=42');
  page.unmount();
});

test('closing an unknown tab or the workbench never changes the current page', () => {
  const page = tabsHarness();
  page.store.tabsList = [{ path: '/', title: '工作台' }, { path: '/articles/edit', fullPath: '/articles/edit?id=42', title: '文章编辑' }];
  page.setup.remove('/missing');
  page.setup.remove('/');
  assert.equal(page.pushes.length, 0);
  assert.equal(page.store.tabsList.length, 2);
  page.unmount();
});

test('clicking the active tab does not navigate or clear the editor query', () => {
  const page = tabsHarness();
  page.setup.tabClick('/articles/edit');
  assert.equal(page.pushes.length, 0);
  assert.equal(page.route.query.id, '42');
  page.unmount();
});

test('tab selection restores the full saved destination instead of opening an empty editor', () => {
  const page = tabsHarness({ name: 'tags', path: '/tags', fullPath: '/tags', query: {}, meta: { title: '标签管理' } });
  page.store.tabsList = [{ path: '/tags', title: '标签管理' }, { path: '/articles/edit', fullPath: '/articles/edit?id=42', title: '文章编辑' }];
  page.setup.tabClick('/articles/edit');
  assert.equal(page.pushes[0], '/articles/edit?id=42');
  page.unmount();
});

test('tab store updates the saved query when the same editor path opens a different article', () => {
  setActivePinia(createPinia());
  const store = tabsStore();
  store.addTab({ path: '/articles/edit', fullPath: '/articles/edit?id=42', title: '文章编辑' });
  store.addTab({ path: '/articles/edit', fullPath: '/articles/edit?id=51', title: '文章编辑' });
  assert.equal(store.tabsList.length, 1);
  assert.equal(store.tabsList[0].fullPath, '/articles/edit?id=51');
});

test('query-only article changes update the saved tab without replacing its active route', async () => {
  const page = tabsHarness();
  page.route.query.id = '51';
  page.route.fullPath = '/articles/edit?id=51';
  await nextTick();
  assert.equal(page.store.tabsList.length, 1);
  assert.equal(page.store.tabsList[0].fullPath, '/articles/edit?id=51');
  assert.equal(unref(page.setup.activeKey), '/articles/edit');
  assert.equal(page.pushes.length, 0);
  page.unmount();
});

test('closing the only closable tab returns to the workbench', () => {
  const page = tabsHarness();
  page.setup.remove('/articles/edit');
  assert.equal(page.pushes[0], '/');
  page.unmount();
});

test('overflow exposes a native click-triggered menu with working tab navigation', () => {
  const page = tabsHarness();
  page.store.addTab({ path: '/tags', title: '标签管理' });
  const dropdown = nodes(page.render()).find(node => node.type === 'a-dropdown');
  assert.ok(dropdown, 'The open tab list must have a touch-operable menu');
  assert.deepEqual(Array.from(dropdown.props.trigger), ['click']);
  const menu = nodes(dropdown).find(node => node.type === 'a-menu');
  menu.props.onClick({ key: '/tags' });
  assert.equal(page.pushes[0], '/tags');
  page.unmount();
});

test('closing an inactive item from the overflow preserves the open menu and current editor', () => {
  const page = tabsHarness();
  page.store.tabsList = [{ path: '/', title: '工作台' }, { path: '/tags', title: '标签管理' }, { path: '/articles/edit', fullPath: '/articles/edit?id=42', title: '文章编辑' }];
  const dropdown = nodes(page.render()).find(node => node.type === 'a-dropdown');
  assert.ok(dropdown, 'The open tab list must have a touch-operable menu');
  dropdown.props['onUpdate:open'](true);
  page.setup.remove('/tags');
  assert.equal(nodes(page.render()).find(node => node.type === 'a-dropdown').props.open, true);
  assert.equal(page.pushes.length, 0);
  assert.equal(unref(page.setup.activeKey), '/articles/edit');
  page.unmount();
});

for (const [kind, label] of [['Category', '分类名称'], ['Tag', '标签名称']]) {
  test(`${kind} mobile table reserves its first content column for readable names and does not pin actions over it`, async () => {
    const screens = ref({ md: false });
    const page = setupPage(`views/${kind}/index.vue`, { Grid: { useBreakpoint: () => screens }, useRouter: () => ({}), message: {} });
    const table = nodes(page.render()).find(node => node.type === 'a-table');
    const columns = table.props.columns;
    assert.equal(columns[0].dataIndex, 'name');
    assert.equal(columns[0].title, label);
    assert.ok(columns[0].width >= 160, 'Names keep a readable width instead of being squeezed to zero');
    assert.ok(!columns.find(column => column.dataIndex === 'action').fixed, 'Mobile actions must not obscure the leading name column');
    assert.ok(table.props.scroll.x >= columns.reduce((width, column) => width + (column.width || 0), 0));
    screens.value = { md: true };
    await nextTick();
    assert.equal(nodes(page.render()).find(node => node.type === 'a-table').props.columns.find(column => column.dataIndex === 'action').fixed, 'right');
    page.unmount();
  });
}
