import fs from 'node:fs';
import vm from 'node:vm';
import * as Vue from 'vue';
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc';

// Execute the real setup script and compiled template; only browser/router/API
// boundaries are supplied by each test. No component behavior is reimplemented.
export function setupPage(relative, supplied = {}) {
  const filename = new URL(`../../src/${relative}`, import.meta.url);
  const source = fs.readFileSync(filename, 'utf8');
  const { descriptor } = parse(source);
  const bindings = compileScript(descriptor, { id: relative }).bindings;
  const mounted = [], unmounted = [];
  const scope = Vue.effectScope();
  const context = {
    ...Vue,
    onMounted: callback => mounted.push(callback),
    onBeforeUnmount: callback => unmounted.push(callback),
    onUnmounted: callback => unmounted.push(callback),
    ...supplied
  };
  const script = descriptor.scriptSetup.content.replace(/^import[\s\S]*?from ['"][^'"]+['"];?\r?\n/gm, '');
  scope.run(() => vm.runInNewContext(`${script}\nglobalThis.setup = {${Object.keys(bindings).map(key => `${key}: typeof ${key} === 'undefined' ? undefined : ${key}`).join(',')}};`, context));
  const template = compileTemplate({ source: descriptor.template.content, filename: filename.pathname, id: relative });
  if (template.errors.length) throw template.errors[0];
  const compiled = template.code.replace(/import \{([^}]+)\} from "vue"/g, (_, imports) =>
    `const {${imports.replace(/\bas\b/g, ':')}} = Vue;`).replace('export function render', 'function render');
  const renderer = { Vue: { ...Vue, resolveComponent: name => ({ name, __testName: name }) } };
  vm.runInNewContext(`${compiled}\nglobalThis.render = render;`, renderer);
  return {
    setup: context.setup,
    render: () => renderer.render(Vue.proxyRefs(context.setup), []),
    mount: () => mounted.forEach(callback => callback()),
    unmount: () => { unmounted.forEach(callback => callback()); scope.stop(); }
  };
}

export function nodes(root) {
  const result = [];
  function visit(node) {
    if (Array.isArray(node)) return node.forEach(visit);
    if (!node || typeof node !== 'object') return;
    result.push(node.type?.__testName ? { ...node, type: node.type.__testName } : node);
    if (Array.isArray(node.children)) visit(node.children);
    else if (node.children && typeof node.children === 'object') {
      for (const name of ['default', 'rightExtra', 'overlay']) {
        if (typeof node.children[name] === 'function') visit(node.children[name]({ Component: undefined }));
      }
    }
  }
  visit(root);
  return result;
}
