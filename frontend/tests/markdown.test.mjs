import { test } from 'node:test';
import assert from 'node:assert/strict';
import { renderMarkdown } from '../utils/render-markdown.ts';
test('markdown SSR renders headings and removes scripts',()=>{
  const html=renderMarkdown('# Readable\n\nHello **world**\n\n<script>alert(1)</script>');
  assert.match(html,/<h1[^>]*>Readable<\/h1>/);
  assert.match(html,/<strong>world<\/strong>/);
  assert.doesNotMatch(html,/<script/);
});
test('markdown renderer keeps server-redacted secret empty',()=>{
  const html=renderMarkdown('::::bd-secret comment\n::::');
  assert.match(html,/bd-block--secret/);
  assert.doesNotMatch(html,/restricted original/);
});

test('SSR headings have stable unique anchors for navigation',()=>{
  const html=renderMarkdown('## Same\n\n## Same');
  assert.match(html,/<h2 id="same">/); assert.match(html,/<h2 id="same-1">/);
});
