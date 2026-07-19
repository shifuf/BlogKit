// 零依赖顶部进度条：路由切换时即时反馈，缩短「点击后无响应」的感知等待。
let bar = null;
let timer = null;
let progress = 0;

function ensureBar() {
  if (bar || typeof document === 'undefined') return bar;
  bar = document.createElement('div');
  bar.className = 'app-route-progress';
  Object.assign(bar.style, {
    position: 'fixed',
    top: '0',
    left: '0',
    height: '2px',
    width: '0',
    zIndex: '2000',
    background: 'linear-gradient(90deg, #2f54eb, #40a9ff)',
    boxShadow: '0 0 8px rgba(47, 84, 235, 0.6)',
    opacity: '0',
    transition: 'width 0.2s ease, opacity 0.3s ease',
    pointerEvents: 'none'
  });
  document.body.appendChild(bar);
  return bar;
}

function render() {
  const el = ensureBar();
  if (!el) return;
  el.style.opacity = '1';
  el.style.width = `${progress}%`;
}

export function start() {
  if (typeof window === 'undefined') return;
  clearInterval(timer);
  progress = 8;
  render();
  // 缓慢爬升到 ~90%，等真正加载完成由 done() 收尾到 100%。
  timer = setInterval(() => {
    progress = Math.min(90, progress + Math.max(0.5, (90 - progress) * 0.12));
    render();
  }, 160);
}

export function done() {
  if (typeof window === 'undefined') return;
  clearInterval(timer);
  const el = ensureBar();
  if (!el) return;
  progress = 100;
  el.style.width = '100%';
  setTimeout(() => {
    el.style.opacity = '0';
    setTimeout(() => {
      progress = 0;
      el.style.width = '0';
    }, 300);
  }, 200);
}
