import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { parse, compileStyle } from '@vue/compiler-sfc';

test('compiled dark badge selectors style only badges and never the page root', () => {
  const filename = new URL('../components/blog/CommentStatusBadge.vue', import.meta.url).pathname;
  const { descriptor } = parse(fs.readFileSync(new URL('../components/blog/CommentStatusBadge.vue', import.meta.url), 'utf8'), { filename });
  const compiled = compileStyle({ source: descriptor.styles[0].content, filename, id: 'data-v-test', scoped: true });
  assert.deepEqual(compiled.errors, []);
  const darkSelectors = [...compiled.code.matchAll(/([^{}]+)\{[^{}]*\}/g)]
    .map(match => match[1].trim()).filter(selector => selector.includes('data-theme'));
  assert.equal(darkSelectors.length, 2);
  assert.ok(darkSelectors.every(selector => /html\[data-theme=['"]?dark['"]?\]\s+\.comment-status-badge/.test(selector)),
    'Vue must preserve the badge descendant after its global theme selector');
});
