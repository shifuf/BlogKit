import { netdiskIcon } from './netdisk-icons';
const MARKER = 0x3a;
const PREFIX = 'bd-';
const GATE_LABEL = {
  none: '所有人可见',
  login: '登录可见',
  comment: '评论后可见'
};
const LINK_ICON_SVG = '<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path></svg>';
export function encodeBlockPayload(obj) {
  const bytes = new TextEncoder().encode(JSON.stringify(obj));
  let bin = '';
  bytes.forEach(b => {
    bin += String.fromCharCode(b);
  });
  return btoa(bin);
}
export function decodeBlockPayload(b64) {
  try {
    const bin = atob(b64);
    const bytes = Uint8Array.from(bin, c => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return null;
  }
}
function uniquifySvg(svg, salt) {
  const suffix = `-${salt}`;
  const ids = new Set();
  svg.replace(/\bid="([^"]+)"/g, (_m, id) => {
    ids.add(id);
    return _m;
  });
  let out = svg;
  for (const id of ids) {
    const esc = id.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    out = out.replace(new RegExp(`\\bid="${esc}"`, 'g'), `id="${id}${suffix}"`);
    out = out.replace(new RegExp(`url\\(#${esc}\\)`, 'g'), `url(#${id}${suffix})`);
    out = out.replace(new RegExp(`href="#${esc}"`, 'g'), `href="#${id}${suffix}"`);
  }
  return out;
}
export function applyBlogContainer(md) {
  if (md.__bdContainer) return;
  md.__bdContainer = true;
  md.block.ruler.before('fence', 'blog_container', blogContainerRule, {
    alt: ['paragraph', 'reference', 'blockquote', 'list']
  });
  const esc = s => md.utils.escapeHtml(s == null ? '' : String(s));
  let seq = 0;
  const ops = (kind, b64) => `<span class="bd-card__ops">` + `<button type="button" class="bd-card__op" data-bd-action="edit" data-bd-kind="${kind}" data-bd-token="${esc(b64)}">编辑</button>` + `<button type="button" class="bd-card__op bd-card__op--danger" data-bd-action="delete" data-bd-kind="${kind}" data-bd-token="${esc(b64)}">删除</button>` + `</span>`;
  const gateTag = gate => {
    const g = gate || 'none';
    return `<span class="bd-card__gate bd-card__gate--${esc(g)}">${esc(GATE_LABEL[g] || GATE_LABEL.none)}</span>`;
  };
  const renderDownload = b64 => {
    const d = decodeBlockPayload(b64);
    if (!d) {
      return `<div class="bd-card bd-card--download bd-card--broken">下载卡片（无法解析，建议删除重建）${ops('download', b64)}</div>`;
    }
    const items = Array.isArray(d.items) ? d.items : [];
    const list = items.map((it, i) => {
      const info = netdiskIcon(it.platform);
      const icon = uniquifySvg(info.svg, `${seq += 1}-${i}`);
      const name = esc(it.name || info.label);
      const codeTag = it.hasCode ? ' · 含提取码' : '';
      return `<div class="bd-card__item">` + `<span class="bd-card__icon">${icon}</span>` + `<span class="bd-card__meta"><span class="bd-card__name">${name}</span>` + `<span class="bd-card__sub">${esc(info.label)}${codeTag}</span></span>` + `</div>`;
    }).join('');
    const desc = d.desc ? `<p class="bd-card__desc">${esc(d.desc)}</p>` : '';
    return `<div class="bd-card bd-card--download">` + `<div class="bd-card__bar"><span class="bd-card__badge">资源下载</span>` + `<span class="bd-card__title">${esc(d.title || '资源下载')}</span>${gateTag(d.gate)}${ops('download', b64)}</div>` + desc + `<div class="bd-card__items">${list || '<span class="bd-card__empty">暂无网盘项</span>'}</div>` + `</div>`;
  };
  const renderButton = b64 => {
    const d = decodeBlockPayload(b64);
    if (!d) {
      return `<div class="bd-card bd-card--button bd-card--broken">自定义按钮（无法解析，建议删除重建）${ops('button', b64)}</div>`;
    }
    const color = /^#[0-9a-fA-F]{3,8}$/.test(d.color || '') ? d.color : '#3b82f6';
    const icon = d.icon === 'link' || !d.icon ? LINK_ICON_SVG : uniquifySvg(netdiskIcon(d.icon).svg, `btn-${seq += 1}`);
    return `<div class="bd-card bd-card--button">` + `<div class="bd-card__bar">${gateTag(d.gate)}${ops('button', b64)}</div>` + `<span class="bd-card__linkbtn" style="--bd-btn-color:${esc(color)}">` + `<span class="bd-card__linkicon">${icon}</span>${esc(d.text || '按钮')}</span>` + `</div>`;
  };
  md.renderer.rules.bd_secret_open = (tokens, idx) => {
    const gate = (tokens[idx].info || 'login').trim() || 'login';
    const label = gate === 'comment' ? '评论可见' : '登录可见';
    return `<div class="bd-block bd-block--secret" data-gate="${esc(gate)}">` + `<div class="bd-card bd-card--secret"><div class="bd-card__bar">` + `<span class="bd-card__badge bd-card__badge--lock">🔒 ${label}</span>` + `<span class="bd-card__hint">下方内容在前台需${label}</span></div>` + `<div class="bd-card__secret-body">\n`;
  };
  md.renderer.rules.bd_secret_close = () => '</div></div></div>\n';
  // 图片组（九宫格）：包裹内部图片，预览即按网格展示，与前台一致。
  md.renderer.rules.bd_gallery_open = () => '<div class="bd-block bd-block--gallery">\n';
  md.renderer.rules.bd_gallery_close = () => '</div>\n';
  md.renderer.rules.bd_leaf = (tokens, idx) => {
    const token = tokens[idx];
    const value = token.meta && token.meta.value || '';
    if (token.info === 'bd-download') {
      return `<div class="bd-block bd-block--download" data-token="${esc(value)}">${renderDownload(value)}</div>\n`;
    }
    if (token.info === 'bd-button') {
      return `<div class="bd-block bd-block--button" data-token="${esc(value)}">${renderButton(value)}</div>\n`;
    }
    return '';
  };
}
function blogContainerRule(state, startLine, endLine, silent) {
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
