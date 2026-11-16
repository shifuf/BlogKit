import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { ref, reactive, computed } from 'vue';
import * as cropUrls from '../src/utils/imageCropUrl.js';
import MarkdownIt from 'markdown-it';

const read = relative => fs.readFileSync(new URL(`../src/${relative}`, import.meta.url), 'utf8');
const withoutImports = source => source.replace(/^import .*;\r?\n/gm, '').replace(/\bexport /g, '');
function fileApi() {
  const context = { request: value => value };
  vm.runInNewContext(`${withoutImports(read('api/File/index.js'))}\nglobalThis.api = { uploadImage };`, context);
  return context.api;
}
function articleEditor() {
  const requests = [];
  const inserted = [];
  const context = {
    ref, reactive, computed, watch() {}, onMounted() {}, onActivated() {}, onBeforeUnmount() {},
    config(value) { context.editorConfig = value; }, applyBlogContainer() {}, useRoute: () => ({ query: { id: '1' } }), useRouter: () => ({}),
    message: { loading: () => () => {}, success() {}, error() {}, warning() {} },
    ...cropUrls, FormData, cropImage: async files => files,
    uploadImage: async (form, publicAccess) => {
      const request = fileApi().uploadImage(form, publicAccess);
      requests.push(request);
      return { url: '/api/public/files/private-image', previewUrl: '/api/public/files/private-image?expires=100&signature=secret', isPublic: publicAccess === true };
    }
  };
  const script = read('views/Article/edit.vue').match(/<script setup>([\s\S]*?)<\/script>/)[1];
  vm.runInNewContext(`${withoutImports(script)}\nglobalThis.editor = { form, editorRef, insertGallery, uploadEditorImages, mergeMediaPreviews };`, context);
  context.editor.editorRef.value = { insert(fn) { inserted.push(fn().targetValue); }, rerender() {} };
  return { ...context.editor, requests, inserted, editorConfig: context.editorConfig };
}
function cropTools() {
  const context = { ...cropUrls, File, Blob, FormData, console,
    compressImage: async file => file,
    uploadImage: (data, policy) => fileApi().uploadImage(data, policy) };
  vm.runInNewContext(`${withoutImports(read('utils/imageCrop.js'))}\nglobalThis.tools = {uploadImageWithCrop, uploadAnyWithOptionalCrop, cropLibraryImageUrl};`, context);
  return context.tools;
}

test('generic image upload remains private when caller omits policy', () => {
  assert.equal(fileApi().uploadImage(new FormData()).params.publicAccess, false);
});
test('cropping and skipped crop image uploads default to private and honor explicit public option', async () => {
  const tools = cropTools();
  const file = new File(['image'], 'image.png', { type: 'image/png' });
  assert.equal((await tools.uploadImageWithCrop(file)).params.publicAccess, false);
  assert.equal((await tools.uploadAnyWithOptionalCrop(file, { crop: false })).params.publicAccess, false);
  assert.equal((await tools.uploadImageWithCrop(file, { publicAccess: true })).params.publicAccess, true);
});
test('library crop output never persists temporary access signature even without stableUrl option', async () => {
  const url = await cropTools().cropLibraryImageUrl('https://files.test/api/public/files/abc?expires=100&signature=secret', { skipCrop: true });
  assert.equal(url, 'https://files.test/api/public/files/abc');
});
test('editor renders a private cropped image through temporary preview while preserving source markdown', () => {
  const editor = articleEditor();
  const md = new MarkdownIt();
  editor.editorConfig.markdownItConfig(md);
  const stable = 'https://files.test/api/public/files/abc';
  editor.mergeMediaPreviews({ [stable]: `${stable}?expires=100&signature=secret` });
  const markdown = `![private](${stable}?x=2&y=3&w=50&h=20)`;
  const tokens = md.parse(markdown, {});
  const token = tokens[1].children[0];
  const html = md.renderer.render(tokens, md.options, {});
  assert.match(html, /signature=secret/);
  assert.match(html, /x=2/);
  assert.equal(token.attrGet('src'), `${stable}?x=2&y=3&w=50&h=20`);
  assert.doesNotMatch(markdown, /signature|expires/);
  assert.equal(cropUrls.resolveImagePreview(`${stable}?x=2&y=3&w=50&h=20`, {
    [`${stable}?x=2&y=3&w=50&h=20`]: `${stable}?expires=100&signature=secret`
  }), `${stable}?expires=100&signature=secret&x=2&y=3&w=50&h=20`);
});
for (const visibility of ['PRIVATE', 'PASSWORD', 'LOGIN', 'PUBLIC', 'UNLISTED']) {
  test(`${visibility} article image and gallery upload uses its visibility policy and stores stable URLs`, async () => {
    const editor = articleEditor();
    editor.form.visibility = visibility;
    const image = new Blob(['image'], { type: 'image/png' });
    let imageUrls;
    await editor.uploadEditorImages([image], urls => { imageUrls = urls; });
    await editor.insertGallery([image]);
    assert.equal(editor.requests.length, 2);
    for (const request of editor.requests) assert.equal(request.params.publicAccess, ['PUBLIC', 'UNLISTED'].includes(visibility));
    assert.equal(imageUrls[0], '/api/public/files/private-image');
    assert.match(editor.inserted[0], /!\[\]\(\/api\/public\/files\/private-image\)/);
    assert.doesNotMatch(editor.inserted[0], /signature=|expires=/);
  });
}
