import type MarkdownIt from 'markdown-it';
const MARKER = 0x3a;
const PREFIX = 'bd-';
export function applyBlogContainer(md: MarkdownIt): void {
  const flagged = md as unknown as {
    __bdContainer?: boolean;
  };
  if (flagged.__bdContainer) return;
  flagged.__bdContainer = true;
  md.block.ruler.before('fence', 'blog_container', blogContainerRule, {
    alt: ['paragraph', 'reference', 'blockquote', 'list']
  });
  md.renderer.rules.bd_secret_open = (tokens, idx) => {
    const token = tokens[idx];
    const [rawGate = 'login', rawId = ''] = (token?.info || 'login').trim().split(/\s+/);
    const gate = ['login', 'comment', 'password', 'users'].includes(rawGate) ? rawGate : 'invalid';
    const id = /^section-\d+$/.test(rawId) ? rawId : '';
    return `<div class="bd-block bd-block--secret" data-gate="${gate}" data-section-id="${id}">\n`;
  };
  md.renderer.rules.bd_secret_close = () => '</div>\n';
  // 图片组（九宫格）：包裹内部的图片，前台按网格展示，超 9 张折叠+查看更多。
  md.renderer.rules.bd_gallery_open = () => '<div class="bd-block bd-block--gallery">\n';
  md.renderer.rules.bd_gallery_close = () => '</div>\n';
  md.renderer.rules.bd_leaf = (tokens, idx) => {
    const token = tokens[idx];
    if (!token) return '';
    const value = md.utils.escapeHtml(token.meta && token.meta.value || '');
    if (token.info === 'bd-download') {
      return `<div class="bd-block bd-block--download" data-token="${value}"></div>\n`;
    }
    if (token.info === 'bd-button') {
      return `<div class="bd-block bd-block--button" data-token="${value}"></div>\n`;
    }
    return '';
  };
}
function blogContainerRule(state: any, startLine: number, endLine: number, silent: boolean): boolean {
  const pos = state.bMarks[startLine] + state.tShift[startLine];
  const max = state.eMarks[startLine];
  if (pos + 3 > max) return false;
  if (state.src.charCodeAt(pos) !== MARKER) return false;
  let markerCount = 0;
  let p = pos;
  while (p < max && state.src.charCodeAt(p) === MARKER) {
    p += 1;
    markerCount += 1;
  }
  if (markerCount < 3) return false;
  const params = state.src.slice(p, max).trim();
  const firstWord = params.split(/\s+/)[0] || '';
  if (!firstWord.startsWith(PREFIX)) return false;
  if (silent) return true;
  const markerStr = ':'.repeat(markerCount);
  let nextLine = startLine;
  let autoClosed = false;
  for (;;) {
    nextLine += 1;
    if (nextLine >= endLine) break;
    const lineStart = state.bMarks[nextLine] + state.tShift[nextLine];
    const lineMax = state.eMarks[nextLine];
    if (lineStart < lineMax && state.sCount[nextLine] < state.blkIndent) break;
    if (state.src.charCodeAt(lineStart) !== MARKER) continue;
    if (state.sCount[nextLine] - state.blkIndent >= 4) continue;
    let cnt = 0;
    let pp = lineStart;
    while (pp < lineMax && state.src.charCodeAt(pp) === MARKER) {
      pp += 1;
      cnt += 1;
    }
    if (cnt < markerCount) continue;
    pp = state.skipSpaces(pp);
    if (pp < lineMax) continue;
    autoClosed = true;
    break;
  }
  const oldParent = state.parentType;
  const oldLineMax = state.lineMax;
  state.parentType = 'blog_container';
  state.lineMax = nextLine;
  const type = firstWord;
  const arg = params.slice(firstWord.length).trim();
  if (type === 'bd-secret') {
    const openToken = state.push('bd_secret_open', 'div', 1);
    openToken.markup = markerStr;
    openToken.block = true;
    openToken.info = arg;
    openToken.map = [startLine, nextLine];
    state.md.block.tokenize(state, startLine + 1, nextLine);
    const closeToken = state.push('bd_secret_close', 'div', -1);
    closeToken.markup = markerStr;
    closeToken.block = true;
  } else if (type === 'bd-gallery') {
    const openToken = state.push('bd_gallery_open', 'div', 1);
    openToken.markup = markerStr;
    openToken.block = true;
    openToken.map = [startLine, nextLine];
    state.md.block.tokenize(state, startLine + 1, nextLine);
    const closeToken = state.push('bd_gallery_close', 'div', -1);
    closeToken.markup = markerStr;
    closeToken.block = true;
  } else {
    const leaf = state.push('bd_leaf', 'div', 0);
    leaf.markup = markerStr;
    leaf.block = true;
    leaf.info = type;
    leaf.meta = {
      value: arg
    };
    leaf.map = [startLine, nextLine];
  }
  state.parentType = oldParent;
  state.lineMax = oldLineMax;
  state.line = autoClosed ? nextLine + 1 : nextLine;
  return true;
}
