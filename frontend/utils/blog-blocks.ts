export interface DownloadDescriptor {
  gate: string;
  title?: string;
  desc?: string;
  items: Array<{
    platform: string;
    name?: string;
    hasCode?: boolean;
  }>;
  token: string;
}
export interface ButtonDescriptor {
  gate: string;
  text: string;
  icon: string;
  color?: string;
  token: string;
}
export function decodeBlockPayload<T>(b64: string): T | null {
  try {
    const bin = atob(b64);
    const bytes = Uint8Array.from(bin, c => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as T;
  } catch {
    return null;
  }
}
export function uniquifySvg(svg: string, salt: string | number): string {
  const suffix = `-${salt}`;
  const ids = new Set<string>();
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
export function hasCommentedLocally(articleId: number): boolean {
  if (!import.meta.client || !articleId) return false;
  try {
    return localStorage.getItem(`bd-commented-${articleId}`) === '1';
  } catch {
    return false;
  }
}
export function markCommentedLocally(articleId: number): void {
  if (!import.meta.client || !articleId) return;
  try {
    localStorage.setItem(`bd-commented-${articleId}`, '1');
  } catch {}
}

// 游客标识：未登录访客点赞用的稳定 key，存 localStorage，跨会话保持。
// 后端按此 key 对游客点赞去重，实现游客可点赞/取消。
export function getGuestKey(): string {
  if (!import.meta.client) return '';
  try {
    let key = localStorage.getItem('bd-guest-key');
    if (!key) {
      const uuid = typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
      key = `g_${uuid}`.replace(/-/g, '').slice(0, 64);
      localStorage.setItem('bd-guest-key', key);
    }
    return key;
  } catch {
    return '';
  }
}
