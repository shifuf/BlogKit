import {
  createPlainMusicPlayer,
  ensurePlainPlayerStyles,
  PLAIN_PLAYER_CSS
} from '~/utils/plainMusicPlayer';
import { unwrapApiData } from '~/utils/apiPayloadCrypto';

type PlayerController = { destroy?: () => void; pause?: () => void };

const HOST_CSS = `
:host {
  display: block;
  width: min(100%, 560px);
  margin: 12px auto;
  container-type: inline-size;
  container-name: music-player;
}
:host(.in-card) {
  width: 100%;
  max-width: 100%;
  margin: 6px 0 0;
}
.music-player-state {
  min-height: 160px;
  display: grid;
  place-items: center;
  gap: 6px;
  padding: 22px 16px;
  border-radius: 26px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, .06);
  box-shadow: 0 16px 40px rgba(15, 23, 42, .08);
  color: #6b7280;
  font-size: 12px;
}
.music-player-state__eq { display:flex; align-items:flex-end; gap:3px; height:14px; }
.music-player-state__eq span { width:2.5px; height:7px; border-radius:99px; background:#8b9cf7; animation: mp-eq .9s ease-in-out infinite; }
.music-player-state__eq span:nth-child(2){ height:11px; animation-delay:.12s; }
.music-player-state__eq span:nth-child(3){ height:8px; animation-delay:.24s; }
.music-player-state__icon {
  width:24px; height:24px; display:grid; place-items:center;
  border-radius:8px; border:1px solid #fecaca; background:#fef2f2; color:#dc2626; font-weight:800;
}
.music-player-state__title { color:#1f2937; font-size:13px; font-weight:700; }
.music-player-state__desc { color:#6b7280; font-size:12px; }
.music-player-state--error { color:#dc2626; }
@keyframes mp-eq { 0%,100%{transform:scaleY(.4);opacity:.55} 50%{transform:scaleY(1);opacity:1} }
.mount { width: 100%; }
`;

function escapeHtml(value: string) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function pickText(...values: Array<string | null | undefined>) {
  for (const value of values) {
    const text = String(value || '').trim();
    if (text) return text;
  }
  return '';
}

/** 注册前台 <music-player> 自定义元素（不再依赖 public/music-player.js）。 */
export function defineMusicPlayerElement() {
  if (typeof window === 'undefined' || typeof customElements === 'undefined') return;
  ensurePlainPlayerStyles();
  if (customElements.get('music-player')) return;

  class BlogMusicPlayer extends HTMLElement {
    private _player: PlayerController | null = null;
    private _loading = false;
    private _loadedRid = '';

    static get observedAttributes() {
      return ['rid', 'name', 'artist', 'cover'];
    }

    connectedCallback() {
      if (!this.shadowRoot) this.attachShadow({ mode: 'open' });
      this.classList.toggle('in-card', Boolean(this.closest('.moment__bubble, .moment__text, .blog-md-preview')));
      void this.load();
    }

    disconnectedCallback() {
      this._player?.pause?.();
    }

    attributeChangedCallback(name: string, oldValue: string | null, newValue: string | null) {
      if (oldValue === newValue || !this.isConnected) return;
      if (name === 'rid' || !this._player) {
        this._player?.destroy?.();
        this._player = null;
        this._loadedRid = '';
        void this.load();
      }
    }

    private state(title: string, detail: string, error = false) {
      if (!this.shadowRoot) return;
      this.shadowRoot.innerHTML = `
        <style>${HOST_CSS}</style>
        <div class="music-player-state${error ? ' music-player-state--error' : ''}">
          ${error
            ? '<div class="music-player-state__icon">!</div>'
            : '<div class="music-player-state__eq" aria-hidden="true"><span></span><span></span><span></span></div>'}
          <div class="music-player-state__title">${escapeHtml(title)}</div>
          <div class="music-player-state__desc">${escapeHtml(detail)}</div>
        </div>
      `;
    }

    private async load() {
      const rid = (this.getAttribute('rid') || '').trim();
      if (!this.shadowRoot) this.attachShadow({ mode: 'open' });
      if (!rid) {
        this.state('音乐加载失败', '缺少音乐资源 ID', true);
        return;
      }
      if (this._loading || (this._player && this._loadedRid === rid)) return;

      this._loading = true;
      this.state('音乐加载中', '正在解析播放地址与歌词…');
      try {
        const response = await fetch(`/api/v1/music/resolve?rid=${encodeURIComponent(rid)}`);
        const payload = await response.json();
        if (!response.ok || !payload?.success) {
          throw new Error(payload?.message || '音乐加载失败');
        }
        const track = await unwrapApiData<{ url?: string; lyric?: string; name?: string; artist?: string; cover?: string; candidates?: Array<{ source: string; url: string; cover?: string; lyric?: string }> }>(payload);
        if (!track?.url) {
          throw new Error(payload?.message || '音乐加载失败');
        }
        if ((this.getAttribute('rid') || '').trim() !== rid) return;

        const name = pickText(this.getAttribute('name'), track.name, '未命名音乐');
        const artist = pickText(this.getAttribute('artist'), track.artist, '未知歌手');

        ensurePlainPlayerStyles();
        this.shadowRoot!.innerHTML = `<style>${HOST_CSS}\n${PLAIN_PLAYER_CSS}</style><div class="mount"></div>`;
        const mount = this.shadowRoot!.querySelector('.mount') as HTMLElement;
        this._player?.destroy?.();
        this._player = createPlainMusicPlayer(mount, {
          name,
          artist,
          cover: track.cover || this.getAttribute('cover') || '',
          sourceType: 'combined',
          url: track.url,
          lyric: track.lyric || '',
          candidates: track.candidates || []
        }, { autoplay: false, mutex: true });
        this._loadedRid = rid;
      } catch (error) {
        const message = error instanceof Error ? error.message : '音乐服务暂时不可用';
        this.state('音乐加载失败', message, true);
      } finally {
        this._loading = false;
      }
    }
  }

  customElements.define('music-player', BlogMusicPlayer);
}
