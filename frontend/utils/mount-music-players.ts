import { createPlainMusicPlayer, ensurePlainPlayerStyles } from '~/utils/plainMusicPlayer';
import { unwrapApiData } from '~/utils/apiPayloadCrypto';

type PlayerController = { destroy?: () => void; pause?: () => void };

const playerRegistry = new WeakMap<Element, PlayerController>();

function loadingHtml() {
  return `
    <div class="music-player-state">
      <div class="music-player-state__eq" aria-hidden="true"><span></span><span></span><span></span></div>
      <div class="music-player-state__title">音乐加载中</div>
      <div class="music-player-state__desc">正在解析播放地址与歌词…</div>
    </div>
  `;
}

function errorHtml(title: string, desc: string) {
  return `
    <div class="music-player-state music-player-state--error">
      <div class="music-player-state__icon">!</div>
      <div class="music-player-state__title">${title}</div>
      <div class="music-player-state__desc">${desc}</div>
    </div>
  `;
}

function attr(el: Element, name: string) {
  return (el.getAttribute(name) || '').trim();
}

/**
 * 把 markdown 预览里的 <music-player> 挂成与 F:\音乐组件 一致的黑胶播放器。
 * 只处理尚未替换的 <music-player> 标签；已挂载的 .md-music-player 不会被拆掉。
 */
export async function mountMusicPlayers(root: ParentNode | null | undefined) {
  if (!import.meta.client || !root) return;
  ensurePlainPlayerStyles();

  const nodes = Array.from(root.querySelectorAll('music-player')) as HTMLElement[];
  if (!nodes.length) return;

  await Promise.all(nodes.map(async (el) => {
    if (el.dataset.musicMounted === '1') return;
    const rid = attr(el, 'rid');
    if (!rid) {
      const host = document.createElement('div');
      host.className = 'md-music-player md-music-player--error';
      host.innerHTML = errorHtml('音乐加载失败', '缺少音乐资源 ID');
      el.replaceWith(host);
      return;
    }

    el.dataset.musicMounted = '1';
    const name = attr(el, 'name') || '未命名音乐';
    const artist = attr(el, 'artist') || '未知歌手';
    const cover = attr(el, 'cover');
    const source = attr(el, 'source') || 'play';

    const host = document.createElement('div');
    host.className = 'md-music-player md-music-player--loading';
    host.dataset.rid = rid;
    host.dataset.source = source;
    host.innerHTML = loadingHtml();
    el.replaceWith(host);

    try {
      const response = await fetch(`/api/v1/music/resolve?rid=${encodeURIComponent(rid)}`);
      const payload = await response.json();
      if (!response.ok || !payload?.success) {
        throw new Error(payload?.message || '音乐加载失败');
      }
      const track = await unwrapApiData<{ url?: string; lyric?: string; name?: string; artist?: string }>(payload);
      if (!track?.url) {
        throw new Error(payload?.message || '音乐加载失败');
      }
      host.innerHTML = '';
      host.classList.remove('md-music-player--loading');
      const player = createPlainMusicPlayer(host, {
        name: track.name || name,
        artist: track.artist || artist,
        cover,
        sourceType: source,
        url: track.url,
        lyric: track.lyric || ''
      }, { autoplay: false, mutex: true });
      if (player) playerRegistry.set(host, player);
      host.classList.add('md-music-player--ready');
    } catch (error) {
      const message = error instanceof Error ? error.message : '音乐服务暂时不可用';
      host.classList.remove('md-music-player--loading');
      host.classList.add('md-music-player--error');
      host.innerHTML = errorHtml('音乐加载失败', message);
    }
  }));
}

export function destroyMusicPlayers(root: ParentNode | null | undefined) {
  if (!import.meta.client || !root) return;
  root.querySelectorAll('.md-music-player').forEach((node) => {
    const player = playerRegistry.get(node);
    player?.destroy?.();
    playerRegistry.delete(node);
  });
}
