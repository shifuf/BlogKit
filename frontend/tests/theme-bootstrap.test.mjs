import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const config = fs.readFileSync(new URL('../nuxt.config.ts', import.meta.url), 'utf8');
const bootstrap = JSON.parse(config.match(/innerHTML:\s*("(?:\\.|[^"\\])*")/)[1]);

function firstPaint({ stored = null, dark = false, storageBlocked = false } = {}) {
  const document = { documentElement: { dataset: {} } };
  const storageWrites = [];
  vm.runInNewContext(bootstrap, {
    document,
    window: { matchMedia: () => ({ matches: dark }) },
    localStorage: {
      getItem() { if (storageBlocked) throw new Error('Storage unavailable'); return stored; },
      setItem(...args) { storageWrites.push(args); }
    }
  });
  return { theme: document.documentElement.dataset.theme, storageWrites };
}

test('first paint follows a dark phone when no theme has been selected', () => {
  assert.equal(firstPaint({ dark: true }).theme, 'dark');
});

test('first paint keeps explicit light and dark selections ahead of the system', () => {
  assert.equal(firstPaint({ stored: 'light', dark: true }).theme, 'light');
  assert.equal(firstPaint({ stored: 'dark', dark: false }).theme, 'dark');
});

test('unrecognized preference and blocked storage still follow system appearance', () => {
  assert.equal(firstPaint({ stored: 'invalid', dark: true }).theme, 'dark');
  assert.equal(firstPaint({ storageBlocked: true, dark: true }).theme, 'dark');
});

test('first paint does not turn system appearance into a saved manual choice', () => {
  assert.deepEqual(firstPaint({ dark: true }).storageWrites, []);
});
