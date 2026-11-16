import xss, { type IFilterXSSOptions } from 'xss';

const escapeAttr = (value: string) =>
  (xss as unknown as { escapeAttrValue: (v: string) => string }).escapeAttrValue(value);

/** 仅放行 http(s)/相对路径/mailto/锚点；拦截 javascript:、data:、vbscript: 等。 */
export const isSafeUrl = (raw: string, opts?: { allowDataImage?: boolean; allowMailto?: boolean }) => {
  // 去掉 C0 控制字符与 DEL，避免 URL 协议被拆开绕过（如 java\nscript:）。
  const value = String(raw || '').trim().replace(/[\x00-\x1F\x7F]/g, '');
  if (!value) return false;
  if (value.startsWith('#')) return true;
  if (value.startsWith('/') && !value.startsWith('//')) return true;
  if (opts?.allowMailto !== false && /^mailto:[^\s]+$/i.test(value)) return true;
  if (/^(https?:|\/\/)/i.test(value)) return true;
  if (opts?.allowDataImage && /^data:image\/(png|jpe?g|gif|webp|svg\+xml);/i.test(value)) return true;
  return false;
};

const safeUrlAttr = (name: string, value: string, allowDataImage = false) => {
  if (!isSafeUrl(value, { allowDataImage, allowMailto: name === 'href' })) return '';
  return `${name}="${escapeAttr(value)}"`;
};

const xssDefaults = xss as unknown as { getDefaultWhiteList: () => Record<string, string[]> };

/** 评论：只保留 markdown 常见标签，图片/链接强制安全 URL。 */
export const COMMENT_XSS_OPTIONS: IFilterXSSOptions = {
  whiteList: {
    p: [],
    br: [],
    strong: [],
    b: [],
    em: [],
    i: [],
    u: [],
    s: [],
    del: [],
    code: [],
    pre: [],
    blockquote: [],
    ul: [],
    ol: [],
    li: [],
    a: ['href', 'title', 'target', 'rel'],
    img: ['src', 'alt', 'class', 'loading', 'width', 'height']
  },
  stripIgnoreTag: true,
  stripIgnoreTagBody: ['script', 'style'],
  onTagAttr: (tag, name, value) => {
    if ((tag === 'a' && name === 'href') || (tag === 'img' && name === 'src')) {
      return safeUrlAttr(name, value, false);
    }
    if (tag === 'a' && name === 'target') return 'target="_blank"';
    if (tag === 'a' && name === 'rel') return 'rel="nofollow noopener noreferrer"';
    if (name === 'class' || name === 'alt' || name === 'title' || name === 'loading' || name === 'width' || name === 'height') {
      return undefined;
    }
    return '';
  },
  onIgnoreTagAttr: () => ''
};

const MUSIC_PLAYER_ATTRS = ['source', 'rid', 'q', 'name', 'artist', 'cover'] as const;
const SAFE_CLASS_RE = /^(bd-block|bd-code|md-editor-|hljs|language-)/;
const filterSafeClass = (value: string) => value.split(/\s+/).filter(cls => SAFE_CLASS_RE.test(cls)).join(' ');

/** 文章正文 / 动态预览：在默认白名单上扩展 md-editor 结构，并拦截危险 URL。 */
export const ARTICLE_XSS_OPTIONS: IFilterXSSOptions = {
  whiteList: {
    ...xssDefaults.getDefaultWhiteList(),
    h1: ['id'], h2: ['id'], h3: ['id'], h4: ['id'], h5: ['id'], h6: ['id'],
    div: ['class', 'data-token', 'data-gate'],
    'music-player': [...MUSIC_PLAYER_ATTRS],
    figure: ['class'],
    figcaption: ['class'],
    details: ['class', 'open'],
    summary: ['class'],
    label: ['class', 'for'],
    input: ['type', 'name', 'id', 'checked', 'class', 'disabled'],
    pre: ['class'],
    code: ['class', 'language'],
    span: ['class', 'data-tips', 'data-is-icon', 'rn-wrapper'],
    svg: ['class', 'viewBox', 'width', 'height', 'aria-hidden', 'xmlns', 'fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin'],
    path: ['d', 'fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin'],
    circle: ['cx', 'cy', 'r', 'fill', 'stroke', 'stroke-width'],
    // 仅允许同页 fragment 引用，禁止 data:/javascript: 的 use 向量
    use: ['href', 'xlink:href']
  },
  stripIgnoreTagBody: ['script', 'style'],
  onTagAttr: (tag, name, value) => {
    if (name === 'class' && (tag === 'div' || tag === 'figure' || tag === 'figcaption' || tag === 'details' || tag === 'summary' || tag === 'label' || tag === 'pre' || tag === 'code' || tag === 'span' || tag === 'svg' || tag === 'path' || tag === 'circle')) {
      const safe = filterSafeClass(value);
      return safe ? `class="${safe}"` : '';
    }
    if ((tag === 'a' || tag === 'area') && name === 'href') {
      return safeUrlAttr('href', value, false);
    }
    if ((tag === 'img' || tag === 'video' || tag === 'audio' || tag === 'source') && name === 'src') {
      return safeUrlAttr('src', value, true);
    }
    if (tag === 'use' && (name === 'href' || name === 'xlink:href')) {
      const v = String(value || '').trim();
      if (!v.startsWith('#')) return '';
      return `${name}="${escapeAttr(v)}"`;
    }
    if (tag === 'blockquote' && name === 'cite') {
      return safeUrlAttr('cite', value, false);
    }
    return undefined;
  },
  onIgnoreTagAttr: (tag, name, value) => {
    if (tag === 'music-player' && (MUSIC_PLAYER_ATTRS as readonly string[]).includes(name)) {
      return `${name}="${escapeAttr(value)}"`;
    }
    return undefined;
  }
};

export const FOOTER_XSS_OPTIONS: IFilterXSSOptions = {
  whiteList: {
    a: ['href', 'title', 'target', 'rel'],
    span: ['class'],
    strong: [],
    em: [],
    br: []
  },
  stripIgnoreTag: true,
  stripIgnoreTagBody: ['script', 'style'],
  onTagAttr(tag, name, value) {
    if (tag === 'a' && name === 'href') return safeUrlAttr('href', value, false);
    if (tag === 'a' && name === 'target') return 'target="_blank"';
    if (tag === 'a' && name === 'rel') return 'rel="nofollow noopener noreferrer"';
  }
};

export const sanitizeHtml = (html: string, options: IFilterXSSOptions) => xss(String(html || ''), options);

export const sanitizeCommentHtml = (html: string) => sanitizeHtml(html, COMMENT_XSS_OPTIONS);

export const sanitizeArticleHtml = (html: string) => sanitizeHtml(html, ARTICLE_XSS_OPTIONS);

export const sanitizeFooterHtml = (html: string) => sanitizeHtml(html, FOOTER_XSS_OPTIONS);
