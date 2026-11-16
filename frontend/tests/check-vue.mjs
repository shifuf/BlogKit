import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(new URL('../package.json', import.meta.url));
const compiler = require('@vue/compiler-sfc');
let count = 0;
function check(dir) {
 for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
  if (['node_modules', '.nuxt', '.output', 'dist'].includes(entry.name)) continue;
  const file = path.join(dir, entry.name);
  if (entry.isDirectory()) { check(file); continue; }
  if (!file.endsWith('.vue')) continue;
  const { descriptor, errors } = compiler.parse(fs.readFileSync(file, 'utf8'), { filename: file });
  if (errors.length) throw new Error(file + ': ' + errors.join(', '));
  const script = descriptor.script || descriptor.scriptSetup ? compiler.compileScript(descriptor, { id: file }) : null;
  if (descriptor.template) {
   const result = compiler.compileTemplate({ filename: file, id: file, source: descriptor.template.content, compilerOptions: { bindingMetadata: script?.bindings } });
   if (result.errors.length) throw new Error(file + ': ' + result.errors.join(', '));
  }
  count++;
 }
}
check(path.resolve(import.meta.dirname, '..')); check(path.resolve(import.meta.dirname, '../../dashboard/src'));
console.log(`Compiled ${count} Vue components`);
