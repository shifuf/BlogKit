import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

test('article comments begin loading after mount so SSR and initial client state agree', () => {
  const source = fs.readFileSync(new URL('../components/blog/CommentSection.vue', import.meta.url), 'utf8');
  const lifecycle = source.slice(source.indexOf('watch(() => props.articleId'), source.indexOf('</script>')).replaceAll('import.meta.client', 'true');
  let mounted, loads = 0;
  const watchers = [];
  vm.runInNewContext(lifecycle, {
    props: { articleId: 2, interactionCount: 1 }, interactionDelta: { value: 0 },
    watch(_value, callback, options) { watchers.push(callback); if (options?.immediate) callback(); },
    onMounted(callback) { mounted = callback; }, loadRemoteComments() { loads++; },
    loadSiteSettings() {}, applyGuestProfile() {}, form: { author: '' }, replyForm: {}, rememberedGuestName: { value: '' }
  });
  assert.equal(loads, 0, 'No client-only loading state before hydration');
  mounted(); assert.equal(loads, 1, 'First page loads after mount');
  watchers[0](); assert.equal(loads, 2, 'Article changes still reload comments');
});
