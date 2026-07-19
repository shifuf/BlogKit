import { uploadImage, uploadFile } from '@/api/File';
import { compressImage } from '@/utils/imageCompress';
import { buildUfmCropUrl, isUfmPublicFileUrl, stripUfmTransformParams } from '@/utils/imageCropUrl';

let cropHost = null;

/** 注册全局裁剪弹窗宿主（App 挂载 / HMR 后调用）。传最新 open，避免 HMR 后仍指向旧闭包。 */
export function registerCropHost(api) {
  cropHost = api;
}

/** 开发态：取当前宿主（供调试） */
export function getCropHost() {
  return cropHost;
}

function toFileArray(input) {
  if (!input) return [];
  if (Array.isArray(input)) return input.filter(Boolean);
  if (typeof FileList !== 'undefined' && input instanceof FileList) return Array.from(input);
  return [input];
}

function isGif(file) {
  return String(file?.type || '').toLowerCase() === 'image/gif'
    || /\.gif$/i.test(file?.name || '');
}

function isImageFile(file) {
  return String(file?.type || '').toLowerCase().startsWith('image/')
    || /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(file?.name || '');
}

function guessFileName(url, fileName, type) {
  let name = fileName;
  if (!name) {
    try {
      const path = new URL(url, window.location.origin).pathname;
      name = decodeURIComponent(path.split('/').pop() || '') || `image-${Date.now()}.jpg`;
    } catch {
      name = `image-${Date.now()}.jpg`;
    }
  }
  if (!/\.[a-z0-9]+$/i.test(name)) {
    const ext = type.includes('png') ? '.png' : type.includes('webp') ? '.webp' : '.jpg';
    name += ext;
  }
  return name;
}

function sniffImageMime(blobOrBuffer) {
  // 优先用响应 Content-Type；octet-stream 时按魔数识别，避免 img 预览失败
  if (blobOrBuffer instanceof Blob && blobOrBuffer.type && blobOrBuffer.type.startsWith('image/')) {
    return blobOrBuffer.type;
  }
  return null;
}

async function blobWithImageType(blob) {
  const typed = sniffImageMime(blob);
  if (typed) return { blob, type: typed };
  try {
    const buf = new Uint8Array(await blob.slice(0, 16).arrayBuffer());
    let type = 'image/jpeg';
    if (buf.length > 3) {
      if (buf[0] === 0xFF && buf[1] === 0xD8) type = 'image/jpeg';
      else if (buf[0] === 0x89 && buf[1] === 0x50) type = 'image/png';
      else if (buf[0] === 0x47 && buf[1] === 0x49 && buf[2] === 0x46) type = 'image/gif';
      else if (buf[0] === 0x52 && buf[1] === 0x49 && buf[2] === 0x46 && buf[3] === 0x46) type = 'image/webp';
    }
    const next = blob.type === type ? blob : new Blob([blob], { type });
    return { blob: next, type };
  } catch {
    return { blob, type: blob.type && blob.type !== 'application/octet-stream' ? blob.type : 'image/jpeg' };
  }
}

/**
 * 从远程 URL 拉成 File，供图库选图后裁剪。
 * 优先走后端同源代理，彻底绕开 files.* 的 CORS/CSP。
 */
export async function urlToFile(url, fileName = '') {
  if (!url) throw new Error('图片地址为空');

  // 1) 后端代理（推荐）
  try {
    const request = (await import('@/utils/request')).default;
    const data = await request.get('/admin/attachments/proxy-image', {
      params: { url },
      responseType: 'blob',
      // 告诉拦截器这是二进制，不要当 JSON 业务体处理
      transformResponse: [d => d]
    });
    const raw = data instanceof Blob
      ? data
      : new Blob([data], { type: 'image/jpeg' });
    const { blob, type } = await blobWithImageType(raw);
    return new File([blob], guessFileName(url, fileName, type), { type, lastModified: Date.now() });
  } catch (proxyErr) {
    console.warn('[imageCrop] proxy failed, fallback', proxyErr);
  }

  // 2) Image + canvas
  try {
    const blob = await new Promise((resolve, reject) => {
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.onload = () => {
        try {
          const canvas = document.createElement('canvas');
          canvas.width = img.naturalWidth || img.width;
          canvas.height = img.naturalHeight || img.height;
          const ctx = canvas.getContext('2d');
          if (!ctx) {
            reject(new Error('canvas 不可用'));
            return;
          }
          ctx.drawImage(img, 0, 0);
          canvas.toBlob(b => (b ? resolve(b) : reject(new Error('导出图片失败'))), 'image/png');
        } catch (error) {
          reject(error);
        }
      };
      img.onerror = () => reject(new Error('图片加载失败'));
      img.src = url;
    });
    const type = blob.type || 'image/png';
    return new File([blob], guessFileName(url, fileName, type), { type, lastModified: Date.now() });
  } catch {
    // fall through
  }

  // 3) 裸 fetch（通常被 CORS/CSP 拦）
  const response = await fetch(url, { credentials: 'omit', mode: 'cors' });
  if (!response.ok) throw new Error(`加载图片失败 (${response.status})`);
  const blob = await response.blob();
  const type = blob.type || 'image/jpeg';
  return new File([blob], guessFileName(url, fileName, type), { type, lastModified: Date.now() });
}

/**
 * 弹裁剪框。多图串行，取消返回 null 并中止剩余。
 * GIF 自动跳过裁剪（保留动画）。
 * @returns {Promise<File|File[]|null>}
 */
export async function cropImage(input, options = {}) {
  const files = toFileArray(input);
  if (!files.length) return null;

  if (!cropHost) {
    console.warn('[imageCrop] host not registered, skip crop');
    return files.length === 1 ? files[0] : files;
  }

  const results = [];
  for (let i = 0; i < files.length; i += 1) {
    const file = files[i];
    if (isGif(file)) {
      results.push(file);
      continue;
    }
    // eslint-disable-next-line no-await-in-loop
    const cropped = await cropHost.open({
      file,
      index: i,
      total: files.length,
      defaultPreset: options.defaultPreset || 'free',
      title: options.title,
      objectUrl: options.objectUrl
    });
    if (!cropped) return null;
    results.push(cropped);
  }
  return files.length === 1 ? results[0] : results;
}

/**
 * 从图库 URL 弹裁剪（可选）。cancel 返回 null；skipCrop 直接返回原 File。
 * 默认行为：拉图 → 裁剪 → 返回 File（旧链路，需再上传）。
 *
 * 若 options.returnCropParams=true：只返回 { skipped, crop }，不导出文件。
 */
export async function cropImageFromUrl(url, options = {}) {
  if (!url) return null;
  if (options.skipCrop) {
    if (options.returnCropParams) {
      return { skipped: true, crop: null };
    }
    return urlToFile(url, options.fileName);
  }

  // 参数模式（UFM 不重传）：直接用原图 URL 预览取裁切坐标，不走代理拉整图。
  // 只读 getData 不需要 canvas 导出，因此无需 CORS/blob；比代理快且不会卡在「获取图片」。
  if (options.returnCropParams) {
    if (!cropHost) {
      console.warn('[imageCrop] host not registered, skip crop params');
      return { skipped: true, crop: null };
    }
    return cropHost.open({
      index: 0,
      total: 1,
      defaultPreset: options.defaultPreset || 'free',
      title: options.title,
      returnCropParams: true,
      objectUrl: url,
      ownObjectUrl: false
    });
  }

  const file = await urlToFile(url, options.fileName);
  return cropImage(file, options);
}

/**
 * 图库选图 + 裁切：UFM 直链拼 crop 参数，不重传；其它图床回退「裁剪后上传」。
 * @returns {Promise<string|null>} 最终可用图片 URL；取消为 null
 */
export async function cropLibraryImageUrl(url, options = {}) {
  if (!url) return null;
  const baseUrl = stripUfmTransformParams(url) || url;

  if (isUfmPublicFileUrl(baseUrl)) {
    if (options.skipCrop) return baseUrl;
    const result = await cropImageFromUrl(baseUrl, {
      ...options,
      returnCropParams: true
    });
    if (!result) return null;
    if (result.skipped || !result.crop) return baseUrl;
    return buildUfmCropUrl(baseUrl, result.crop, { format: options.format ?? 'webp' });
  }

  // 非 UFM：保持旧行为——裁剪后上传
  if (options.skipCrop) return baseUrl;
  const cropped = await cropImageFromUrl(baseUrl, options);
  if (!cropped) return null;
  let target = Array.isArray(cropped) ? cropped[0] : cropped;
  if (!target) return null;
  if (target instanceof File) {
    target = await compressImage(target, options.compress || {});
    const formData = new FormData();
    formData.append('file', target);
    const uploaded = await uploadImage(formData);
    return uploaded?.url || baseUrl;
  }
  return baseUrl;
}

/**
 * 裁剪后上传。取消返回 null。
 */
export async function uploadImageWithCrop(file, options = {}) {
  const cropped = await cropImage(file, options);
  if (!cropped) return null;
  let target = Array.isArray(cropped) ? cropped[0] : cropped;
  if (!target) return null;
  // 裁剪后再压缩，减小上传体积。
  target = await compressImage(target, options.compress || {});
  const formData = new FormData();
  formData.append('file', target);
  return uploadImage(formData);
}

/**
 * 通用文件上传：图片可走裁剪，其它类型直接上传。
 */
export async function uploadAnyWithOptionalCrop(file, options = {}) {
  if (!file) return null;
  if (isImageFile(file) && options.crop !== false && !isGif(file)) {
    return uploadImageWithCrop(file, options);
  }
  if (isImageFile(file)) {
    const formData = new FormData();
    formData.append('file', file);
    return uploadImage(formData);
  }
  const formData = new FormData();
  formData.append('file', file);
  return uploadFile(formData);
}
