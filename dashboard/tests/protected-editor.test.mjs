import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as dates from '../src/utils/date.js';
import fs from 'node:fs';
import vm from 'node:vm';

function previewRules() {
  const source = fs.readFileSync(new URL('../src/utils/blog-markdown.js', import.meta.url), 'utf8')
    .replace(/^import .*;\r?\n/gm, '').replace(/\bexport /g, '');
  const context = { TextEncoder, TextDecoder, atob, btoa };
  vm.runInNewContext(`${source}\nglobalThis.configure = applyBlogContainer;`, context);
  const md = {
    utils: { escapeHtml: value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;'),
      unescapeAll: value => value.replaceAll('&amp;', '&').replaceAll('&quot;', '"').replaceAll('&lt;', '<').replaceAll('&gt;', '>') },
    block: { ruler: { before() {} } },
    renderer: { rules: { html_block: (tokens, index) => tokens[index].content, html_inline: (tokens, index) => tokens[index].content } }
  };
  context.configure(md);
  return md.renderer.rules;
}

test('inserted current time uses Beijing rather than workstation timezone and crosses dates correctly', () => {
  assert.equal(dates.formatBeijingTime?.('2026-11-19T20:05:09Z'), '2026-11-20 04:05:09（北京时间）');
  assert.equal(dates.formatBeijingTime?.('2026-12-31T16:00:00Z'), '2027-01-01 00:00:00（北京时间）');
});
test('editor music preview shows cover song and artist while preserving resource ids only in source', () => {
  const rules = previewRules();
  const html = rules.html_block([{ content: '<music-player rid="INTERNAL_RESOURCE" name="Song" artist="Artist" cover="/cover.jpg"></music-player>' }], 0);
  assert.match(html, /bd-card--music/);
  assert.match(html, /src="\/cover.jpg"/);
  assert.match(html, />Song</);
  assert.match(html, />Artist</);
  assert.doesNotMatch(html, /INTERNAL_RESOURCE|rid=/);
});
test('editor preview excludes dangerous covers and escapes displayed metadata', () => {
  const rules = previewRules();
  const html = rules.html_inline([{ content: '<music-player rid="123" name="&lt;script&gt;bad&lt;/script&gt;" artist="Artist" cover="javascript:alert(1)"></music-player>' }], 0);
  assert.doesNotMatch(html, /<script>|javascript:/);
  assert.match(html, /&lt;script&gt;bad&lt;\/script&gt;/);
});
