/**
 * 客户端图片压缩：超宽/超大体积时压到 JPEG/WebP。
 * GIF 原样返回；不支持 canvas 时原样返回。
 */
export async function compressImage(file, options = {}) {
  if (!file || !file.type?.startsWith('image/')) return file;
  if (file.type === 'image/gif' || /\.gif$/i.test(file.name || '')) return file;

  const maxWidth = options.maxWidth || 1920;
  const maxHeight = options.maxHeight || 1920;
  const quality = options.quality ?? 0.82;
  const preferWebp = options.preferWebp !== false;
  // 小于阈值不压，避免无意义重编码
  if (file.size < (options.minBytes || 280 * 1024) && !options.force) return file;

  const bitmap = await createImageBitmap(file).catch(() => null);
  if (!bitmap) return file;

  let { width, height } = bitmap;
  const ratio = Math.min(1, maxWidth / width, maxHeight / height);
  width = Math.max(1, Math.round(width * ratio));
  height = Math.max(1, Math.round(height * ratio));

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    bitmap.close?.();
    return file;
  }
  ctx.drawImage(bitmap, 0, 0, width, height);
  bitmap.close?.();

  const tryTypes = preferWebp
    ? ['image/webp', file.type === 'image/png' ? 'image/png' : 'image/jpeg']
    : [file.type === 'image/png' ? 'image/png' : 'image/jpeg'];

  for (const type of tryTypes) {
    // eslint-disable-next-line no-await-in-loop
    const blob = await new Promise(resolve => canvas.toBlob(resolve, type, quality));
    if (!blob || blob.size >= file.size) continue;
    const ext = type === 'image/webp' ? 'webp' : type === 'image/png' ? 'png' : 'jpg';
    const base = String(file.name || 'image').replace(/\.[^.]+$/, '');
    return new File([blob], `${base}.${ext}`, { type, lastModified: Date.now() });
  }
  return file;
}
