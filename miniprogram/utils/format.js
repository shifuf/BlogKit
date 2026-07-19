const { config } = require('./config');

// 网盘平台键 → 中文名（与 admin/netdisk-icons 对齐）。小程序不内联 SVG，只展示名称。
const NETDISK_LABELS = {
  baidu: '百度云盘',
  aliyun: '阿里云盘',
  pan123: '123盘',
  quark: '夸克网盘',
  lanzou: '蓝奏云',
  chengtong: '诚通网盘',
  github: 'GitHub',
  weiyun: '微云',
  link: '链接'
};

// 纯 JS base64 解码（小程序无 atob）。返回 latin1 二进制串。
function atobPoly(input) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  const data = String(input || '').replace(/[^A-Za-z0-9+/=]/g, '');
  let output = '';
  let i = 0;
  while (i < data.length) {
    const h1 = chars.indexOf(data.charAt(i));
    const h2 = chars.indexOf(data.charAt(i + 1));
    const h3 = chars.indexOf(data.charAt(i + 2));
    const h4 = chars.indexOf(data.charAt(i + 3));
    i += 4;
    const bits = (h1 << 18) | ((h2 < 0 ? 0 : h2) << 12) | ((h3 < 0 ? 0 : h3) << 6) | (h4 < 0 ? 0 : h4);
    output += String.fromCharCode((bits >> 16) & 0xff);
    if (h3 >= 0) {
      output += String.fromCharCode((bits >> 8) & 0xff);
    }
    if (h4 >= 0) {
      output += String.fromCharCode(bits & 0xff);
    }
  }
  return output;
}

function utf8Decode(bin) {
  let result = '';
  let i = 0;
  while (i < bin.length) {
    const c = bin.charCodeAt(i);
    i += 1;
    if (c < 0x80) {
      result += String.fromCharCode(c);
    } else if (c >= 0xc0 && c < 0xe0) {
      result += String.fromCharCode(((c & 0x1f) << 6) | (bin.charCodeAt(i) & 0x3f));
      i += 1;
    } else if (c >= 0xe0 && c < 0xf0) {
      result += String.fromCharCode(
        ((c & 0x0f) << 12) | ((bin.charCodeAt(i) & 0x3f) << 6) | (bin.charCodeAt(i + 1) & 0x3f)
      );
      i += 2;
    } else {
      let cp = ((c & 0x07) << 18)
        | ((bin.charCodeAt(i) & 0x3f) << 12)
        | ((bin.charCodeAt(i + 1) & 0x3f) << 6)
        | (bin.charCodeAt(i + 2) & 0x3f);
      i += 3;
      cp -= 0x10000;
      result += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
    }
  }
  return result;
}

function decodeBlockPayload(b64) {
  try {
    return JSON.parse(utf8Decode(atobPoly(b64)));
  } catch (error) {
    return null;
  }
}

function pad(value) {
  return value < 10 ? `0${value}` : `${value}`;
}

function formatDate(value) {
  if (!value) {
    return '';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

// 静态资源（前台 public / layout-interaction / seed-covers）走 webOrigin；
// 上传文件与 API 文件走 backendOrigin。
function normalizeImageUrl(url) {
  if (!url) {
    return '';
  }

  const raw = String(url).trim();
  if (!raw) {
    return '';
  }

  if (/^https?:\/\//i.test(raw) || /^data:/i.test(raw) || /^wxfile:\/\//i.test(raw)) {
    return raw;
  }

  // 前台静态资源（头像 mark、seed 封面等）必须走 web 源，后端 8080 没有这些路径。
  if (
    raw.startsWith('/layout-interaction')
    || raw.startsWith('/seed-covers')
    || raw.startsWith('/_nuxt')
    || raw.startsWith('/static')
  ) {
    return `${config.webOrigin}${raw}`;
  }

  if (raw.startsWith('/uploads') || raw.startsWith('/api/')) {
    return `${config.backendOrigin}${raw}`;
  }

  // 其余相对路径：优先 webOrigin（站点资源），避免拼到错误 host。
  return `${config.webOrigin}${raw.startsWith('/') ? raw : `/${raw}`}`;
}

// 统一文件管理缩略：?w=&format=webp
// 白名单已含 servicewechat.com，小程序可正常拉缩略图（原图 1MB+ 会慢且糊感强）。
function buildThumbUrl(url, width) {
  if (!url || !Number.isFinite(width) || width <= 0) {
    return url || '';
  }
  if (!/\/api\/public\/files\//i.test(url)) {
    return url;
  }
  // 去掉旧参数再拼，避免叠加
  const bare = url.replace(/[?&](w|format)=[^&]*/g, '').replace(/\?$/, '');
  const separator = bare.includes('?') ? '&' : '?';
  return `${bare}${separator}w=${Math.round(width)}&format=webp`;
}

// 本地兜底图（打包进小程序，避免依赖网络）。
const PLACEHOLDER_COVER = '/images/no-cover.png';
const PLACEHOLDER_AVATAR = '/images/default-avatar.png';

// 文章封面：有真实封面用真图（svg 视为无效封面），否则用本地占位图。
function resolveCover(url, width = 750) {
  const normalized = normalizeImageUrl(url);
  if (!normalized || /\.svg($|\?)/i.test(normalized)) {
    return PLACEHOLDER_COVER;
  }
  return buildThumbUrl(normalized, width);
}

// 头像：有接口头像用真图，否则用本地默认头像。
function resolveAvatar(url, width = 200) {
  const normalized = normalizeImageUrl(url);
  if (!normalized) {
    return PLACEHOLDER_AVATAR;
  }
  return buildThumbUrl(normalized, width);
}

function stripHtml(value) {
  return (value || '').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
}

function isHtml(value) {
  return /<\/?[a-z][\s\S]*>/i.test(value || '');
}

function isImageUrl(value) {
  return /\.(png|jpe?g|gif|webp|bmp|avif|svg)(\?.*)?$/i.test(value || '')
    || /\/uploads\//i.test(value || '')
    || /\/api\/public\/files\//i.test(value || '');
}

function extractMarkdownUrl(value) {
  const imageMatch = /^!\[[^\]]*\]\(([^)\s]+)(?:\s+["'][^"']*["'])?\)$/.exec(value);
  if (imageMatch) {
    return imageMatch[1];
  }

  const linkMatch = /^\[[^\]]*\]\(([^)\s]+)(?:\s+["'][^"']*["'])?\)$/.exec(value);
  if (linkMatch && isImageUrl(linkMatch[1])) {
    return linkMatch[1];
  }

  return '';
}

function escapeHtml(value) {
  return String(value || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function inlineMarkdown(text) {
  let out = text;
  out = out.replace(/!\[([^\]]*)\]\(([^)\s]+)(?:[^)]*)?\)/g,
    (m, alt, url) => `<img src="${normalizeImageUrl(url)}" alt="${alt}" style="max-width:100%" />`);
  out = out.replace(/\[([^\]]+)\]\(([^)\s]+)(?:[^)]*)?\)/g, (m, t, u) => `<a href="${u}">${t}</a>`);
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>').replace(/__([^_]+)__/g, '<strong>$1</strong>');
  out = out.replace(/(^|[^*])\*([^*\s][^*]*)\*/g, '$1<em>$2</em>');
  out = out.replace(/~~([^~]+)~~/g, '<del>$1</del>');
  out = out.replace(/`([^`]+)`/g, '<code>$1</code>');
  return out;
}

function markdownToHtml(md) {
  const lines = (md || '').split('\n');
  let html = '';
  let i = 0;
  let inList = false;
  let listTag = '';
  const closeList = () => {
    if (inList) {
      html += `</${listTag}>`;
      inList = false;
      listTag = '';
    }
  };
  while (i < lines.length) {
    const line = lines[i];
    const trimmed = line.trim();
    if (/^```/.test(trimmed)) {
      closeList();
      const code = [];
      i += 1;
      while (i < lines.length && !/^```/.test(lines[i].trim())) {
        code.push(lines[i]);
        i += 1;
      }
      i += 1;
      html += `<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`;
      continue;
    }
    if (!trimmed) {
      closeList();
      i += 1;
      continue;
    }
    const heading = /^(#{1,6})\s+(.*)$/.exec(trimmed);
    if (heading) {
      closeList();
      const lvl = heading[1].length;
      html += `<h${lvl}>${inlineMarkdown(escapeHtml(heading[2]))}</h${lvl}>`;
      i += 1;
      continue;
    }
    if (/^(-{3,}|\*{3,}|_{3,})$/.test(trimmed)) {
      closeList();
      html += '<hr />';
      i += 1;
      continue;
    }
    if (/^>\s?/.test(trimmed)) {
      closeList();
      html += `<blockquote>${inlineMarkdown(escapeHtml(trimmed.replace(/^>\s?/, '')))}</blockquote>`;
      i += 1;
      continue;
    }
    const ul = /^[-*+]\s+(.*)$/.exec(trimmed);
    if (ul) {
      if (!inList || listTag !== 'ul') {
        closeList();
        html += '<ul>';
        inList = true;
        listTag = 'ul';
      }
      html += `<li>${inlineMarkdown(escapeHtml(ul[1]))}</li>`;
      i += 1;
      continue;
    }
    const ol = /^\d+\.\s+(.*)$/.exec(trimmed);
    if (ol) {
      if (!inList || listTag !== 'ol') {
        closeList();
        html += '<ol>';
        inList = true;
        listTag = 'ol';
      }
      html += `<li>${inlineMarkdown(escapeHtml(ol[1]))}</li>`;
      i += 1;
      continue;
    }
    closeList();
    html += `<p>${inlineMarkdown(escapeHtml(trimmed))}</p>`;
    i += 1;
  }
  closeList();
  return html;
}

function soleImageUrl(line) {
  const url = extractMarkdownUrl(line);
  if (url) {
    return url;
  }
  if (!/\s/.test(line) && isImageUrl(line)) {
    return line;
  }
  return '';
}

// 九宫格：最多显示前 9 张；超过时第 9 张叠「+N」遮罩（N = 总数 - 9）。
// 列表用缩略图；预览用原图数组 images（按 index 取，不走 data-urls）。
function buildGalleryGrid(urls) {
  const images = (urls || []).filter(Boolean).map((u) => normalizeImageUrl(u));
  const grid = images.slice(0, 9);
  const extra = images.length > 9 ? images.length - 9 : 0;
  const gridItems = grid.map((src, index) => ({
    key: `g-${index}`,
    index,
    // 列表缩略：清晰且快（~26KB vs 原图 1MB+）
    src: buildThumbUrl(src, 720)
  }));
  return {
    images,
    grid,
    gridItems,
    count: images.length,
    extra
  };
}

function toContentBlocks(content) {
  const lines = (content || '').split('\n');
  const blocks = [];
  let buffer = [];
  const flushMarkdown = () => {
    if (!buffer.length) {
      return;
    }
    const html = markdownToHtml(buffer.join('\n'));
    if (html.trim()) {
      blocks.push({ type: 'html', html });
    }
    buffer = [];
  };
  let i = 0;
  while (i < lines.length) {
    const line = lines[i].trim();
    const open = /^:::\s*(bd-download|bd-button|bd-secret|bd-gallery)\s*(.*)$/.exec(line);
    if (open) {
      flushMarkdown();
      const kind = open[1];
      const arg = (open[2] || '').trim();
      const inner = [];
      i += 1;
      while (i < lines.length && lines[i].trim() !== ':::') {
        inner.push(lines[i]);
        i += 1;
      }
      i += 1;
      if (kind === 'bd-download') {
        const d = decodeBlockPayload(arg);
        if (d) {
          blocks.push({
            type: 'download',
            gate: d.gate || 'none',
            title: d.title || '资源下载',
            desc: d.desc || '',
            token: d.token || '',
            items: (d.items || []).map((it) => ({
              platform: it.platform,
              label: NETDISK_LABELS[it.platform] || it.platform || '网盘',
              name: it.name || '',
              hasCode: Boolean(it.hasCode)
            }))
          });
        }
      } else if (kind === 'bd-button') {
        const d = decodeBlockPayload(arg);
        if (d) {
          blocks.push({ type: 'button', gate: d.gate || 'none', text: d.text || '按钮', token: d.token || '' });
        }
      } else if (kind === 'bd-secret') {
        blocks.push({ type: 'secret', gate: arg || 'login', blocks: toContentBlocks(inner.join('\n')) });
      } else if (kind === 'bd-gallery') {
        const imgs = [];
        inner.forEach((raw) => {
          const l = raw.trim();
          const url = soleImageUrl(l);
          if (url) {
            imgs.push(url);
          }
        });
        if (imgs.length) {
          const g = buildGalleryGrid(imgs);
          blocks.push({
            type: 'gallery',
            images: g.images,
            grid: g.grid,
            gridItems: g.gridItems,
            count: g.count,
            extra: g.extra
          });
        }
      }
      continue;
    }
    const img = soleImageUrl(line);
    if (img) {
      flushMarkdown();
      blocks.push({ type: 'image', src: normalizeImageUrl(img) });
      i += 1;
      continue;
    }
    buffer.push(lines[i]);
    i += 1;
  }
  flushMarkdown();
  return blocks;
}

function collectImageSrcs(blocks) {
  const out = [];
  blocks.forEach((block) => {
    if (block.type === 'image') {
      out.push(block.src);
    } else if (block.type === 'gallery' && Array.isArray(block.images)) {
      block.images.forEach((src) => out.push(src));
    } else if (block.type === 'secret' && Array.isArray(block.blocks)) {
      collectImageSrcs(block.blocks).forEach((src) => out.push(src));
    }
  });
  return out;
}

function normalizeArticle(article) {
  // 封面一定有值：真实封面或本地占位图。coverUrl 供 <image> 直接使用。
  const coverUrl = resolveCover(article.coverImageUrl, 750);
  return {
    ...article,
    coverUrl,
    authorAvatarUrl: resolveAvatar(article.authorAvatarUrl),
    tagNames: Array.isArray(article.tagNames) ? article.tagNames : [],
    summary: article.summary || stripHtml(article.content).slice(0, 80),
    displayTime: formatDate(article.publishedAt)
  };
}

function normalizeArticleDetail(article) {
  const normalized = normalizeArticle(article);
  const content = article.content || '';
  const hasBlocks = /:::\s*bd-(download|secret|button)/.test(content);
  const contentBlocks = toContentBlocks(content);
  return {
    ...normalized,
    content,
    contentIsHtml: isHtml(content) && !hasBlocks,
    contentBlocks,
    contentImages: collectImageSrcs(contentBlocks),
    visibility: article.visibility || 'PUBLIC',
    locked: article.locked === true,
    requiresPassword: article.requiresPassword === true
  };
}

function renderCommentText(content) {
  return String(content || '').replace(
    /!\[([^\]]*)\]\([^)]*\/emoji\/fluent\/[^)]*\)/g,
    (m, alt) => alt || ''
  );
}

function normalizeComment(comment) {
  return {
    ...comment,
    content: renderCommentText(comment.content),
    replies: Array.isArray(comment.replies) ? comment.replies.map(normalizeComment) : [],
    displayTime: formatDate(comment.createdAt)
  };
}

function formatDateTime(value) {
  if (!value) {
    return '';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const now = new Date();
  const hm = `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  const sameYear = date.getFullYear() === now.getFullYear();
  const sameDay = sameYear && date.getMonth() === now.getMonth() && date.getDate() === now.getDate();
  if (sameDay) {
    return `今天 ${hm}`;
  }
  if (sameYear) {
    return `${date.getMonth() + 1}月${date.getDate()}日 ${hm}`;
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${hm}`;
}

function normalizeDynamic(dynamic) {
  const raw = dynamic.content || '';
  const images = [];
  const imgRegex = /!\[[^\]]*\]\(\s*([^)\s]+)[^)]*\)/g;
  let match;
  while ((match = imgRegex.exec(raw)) !== null) {
    images.push(match[1]);
  }
  const textMarkdown = raw
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/^\s*:::.*$/gm, '')
    .trim();
  const textHtml = textMarkdown ? markdownToHtml(textMarkdown) : '';
  const gallery = buildGalleryGrid(images);
  return {
    ...dynamic,
    images: gallery.images,
    grid: gallery.grid,
    gridItems: gallery.gridItems,
    imageCount: gallery.count,
    extra: gallery.extra,
    textHtml,
    hasText: Boolean(textHtml && textHtml.trim()),
    locationText: dynamic.location && !/^-?\d+(\.\d+)?,-?\d+(\.\d+)?$/.test(String(dynamic.location).trim())
      ? String(dynamic.location).trim()
      : '',
    displayTime: formatDateTime(dynamic.publishedAt || dynamic.createdAt),
    liked: false,
    likeCount: Number(dynamic.likeCount || 0),
    commentCount: Number(dynamic.commentCount || 0)
  };
}

function normalizeDynamicComment(comment) {
  return {
    ...comment,
    replyTo: comment.replyTo || '',
    replies: Array.isArray(comment.replies) ? comment.replies.map(normalizeDynamicComment) : [],
    displayTime: formatDateTime(comment.createdAt)
  };
}

// 站点设置：归一化头像 / 动态背景 / 首页主图字段，供各页直接使用。
// profileName 就是用户看到的作者名（默认「站点管理员」）；头像无值时用本地默认头像兜底。
function normalizeSiteSettings(settings) {
  const s = settings || {};
  return {
    ...s,
    profileAvatar: resolveAvatar(s.profileAvatarUrl, 240),
    profileName: s.profileName || s.siteName || '博客',
    profileBio: s.profileBio || '',
    dynamicsBackground: s.homeDynamicsBackground ? normalizeImageUrl(s.homeDynamicsBackground) : '',
    dynamicsSignature: s.homeDynamicsSignature || s.profileBio || '',
    heroImage: s.homeHeroImageUrl ? normalizeImageUrl(s.homeHeroImageUrl) : ''
  };
}

module.exports = {
  formatDate,
  formatDateTime,
  normalizeArticle,
  normalizeArticleDetail,
  normalizeComment,
  normalizeDynamic,
  normalizeDynamicComment,
  normalizeSiteSettings,
  normalizeImageUrl,
  resolveCover,
  resolveAvatar,
  buildThumbUrl,
  PLACEHOLDER_COVER,
  PLACEHOLDER_AVATAR
};
