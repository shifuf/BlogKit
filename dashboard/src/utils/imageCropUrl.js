/**
 * 统一文件管理（UFM）公开图床裁切 URL 工具。
 *
 * UFM 公开访问：`GET /api/public/files/:fileId`
 * 支持参数（见统一文件管理 image-transform.util.ts）：
 * - 有 x/y 时：w/h 为裁切框（像素）
 * - 无 x/y 时：w/h 为缩放
 * - format=webp|avif|auto
 *
 * 例：https://files.example.com/api/public/files/abc?x=100&y=80&w=800&h=450&format=webp
 */

const UFM_PUBLIC_PATH_RE = /\/api\/public\/files\/[^/?#]+/i;
const TRANSFORM_KEYS = new Set(['x', 'y', 'w', 'h', 'cw', 'ch', 'format']);

/** Temporary UFM credentials belong to previews and must never be saved in article URLs. */
export function stripUfmAccessParams(sourceUrl) {
  if (!isUfmPublicFileUrl(sourceUrl)) return sourceUrl || '';
  try {
    const parsed = new URL(sourceUrl, typeof window !== 'undefined' ? window.location.origin : 'http://localhost');
    parsed.searchParams.delete('expires');
    parsed.searchParams.delete('signature');
    return sourceUrl.startsWith('/') ? `${parsed.pathname}${parsed.search}${parsed.hash}` : parsed.toString();
  } catch {
    return sourceUrl;
  }
}

/** Resolve a stable image/crop URL for display without changing markdown. */
export function resolveImagePreview(sourceUrl, previews = {}) {
  if (!sourceUrl) return '';
  const stable = stripUfmAccessParams(sourceUrl);
  const base = stripUfmTransformParams(stable);
  const preview = previews[stable] || previews[base];
  if (!preview) return stable;
  try {
    const parsed = new URL(preview, typeof window !== 'undefined' ? window.location.origin : 'http://localhost');
    const crop = new URL(stable, typeof window !== 'undefined' ? window.location.origin : 'http://localhost');
    for (const key of TRANSFORM_KEYS) if (crop.searchParams.has(key)) parsed.searchParams.set(key, crop.searchParams.get(key));
    return preview.startsWith('/') ? `${parsed.pathname}${parsed.search}${parsed.hash}` : parsed.toString();
  } catch {
    return preview;
  }
}

/**
 * 是否为 UFM 公开文件直链（可拼 crop 参数、服务端裁切）。
 */
export function isUfmPublicFileUrl(url) {
  if (!url || typeof url !== 'string') return false;
  try {
    const parsed = new URL(url, typeof window !== 'undefined' ? window.location.origin : 'http://localhost');
    return UFM_PUBLIC_PATH_RE.test(parsed.pathname);
  } catch {
    return UFM_PUBLIC_PATH_RE.test(url);
  }
}

function toInt(value, fallback = 0) {
  const n = Math.round(Number(value));
  return Number.isFinite(n) ? n : fallback;
}

/**
 * 规范化 cropper / 业务侧裁切框为像素整数。
 * 接受 { x, y, width, height } 或 { left, top, width, height }
 */
export function normalizeCropRect(crop) {
  if (!crop || typeof crop !== 'object') return null;
  const x = Math.max(0, toInt(crop.x ?? crop.left, 0));
  const y = Math.max(0, toInt(crop.y ?? crop.top, 0));
  const width = Math.max(1, toInt(crop.width ?? crop.w, 0));
  const height = Math.max(1, toInt(crop.height ?? crop.h, 0));
  if (!width || !height) return null;
  return { x, y, width, height };
}

/**
 * 在原图 URL 上拼接 UFM 裁切参数（不上传新图）。
 * 会剥离旧的 x/y/w/h/cw/ch/format，保留 expires/signature 等鉴权参数。
 *
 * @param {string} sourceUrl
 * @param {{ x?: number, y?: number, left?: number, top?: number, width?: number, height?: number, w?: number, h?: number }} crop
 * @param {{ format?: 'webp' | 'avif' | 'auto' | false }} [options]
 * @returns {string}
 */
export function buildUfmCropUrl(sourceUrl, crop, options = {}) {
  if (!sourceUrl) return '';
  const rect = normalizeCropRect(crop);
  if (!rect) return sourceUrl;

  let parsed;
  try {
    parsed = new URL(sourceUrl, typeof window !== 'undefined' ? window.location.origin : 'http://localhost');
  } catch {
    return sourceUrl;
  }

  for (const key of TRANSFORM_KEYS) {
    parsed.searchParams.delete(key);
  }

  parsed.searchParams.set('x', String(rect.x));
  parsed.searchParams.set('y', String(rect.y));
  parsed.searchParams.set('w', String(rect.width));
  parsed.searchParams.set('h', String(rect.height));

  const format = options.format === false ? null : (options.format || 'webp');
  if (format) {
    parsed.searchParams.set('format', format);
  }

  // 相对路径输入时保持相对路径输出
  if (!/^https?:\/\//i.test(sourceUrl) && sourceUrl.startsWith('/')) {
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  }
  return parsed.toString();
}

/**
 * 去掉裁切/缩放参数，得到原图访问 URL（保留签名参数）。
 */
export function stripUfmTransformParams(sourceUrl) {
  if (!sourceUrl) return '';
  try {
    const parsed = new URL(sourceUrl, typeof window !== 'undefined' ? window.location.origin : 'http://localhost');
    for (const key of TRANSFORM_KEYS) {
      parsed.searchParams.delete(key);
    }
    if (!/^https?:\/\//i.test(sourceUrl) && sourceUrl.startsWith('/')) {
      return `${parsed.pathname}${parsed.search}${parsed.hash}`;
    }
    return parsed.toString();
  } catch {
    return sourceUrl;
  }
}
