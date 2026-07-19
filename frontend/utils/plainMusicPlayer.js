const activePlayers = new Set()
export const PLAYER_LAYOUT_VERSION = 'v5-3-volume-pct-mobile'

function formatTime(value) {
  if (!Number.isFinite(value) || value <= 0) return '00:00'
  const minutes = Math.floor(value / 60)
  const seconds = Math.floor(value % 60)
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function setWidth(el, ratio) {
  el.style.width = '100%'
  el.style.transform = `scaleX(${Math.max(0, Math.min(1, ratio || 0))})`
}

function clamp01(value) {
  return Math.max(0, Math.min(1, value || 0))
}

function svgIcon(paths, size = 18) {
  return `<svg class="plain-player__icon" viewBox="0 0 24 24" width="${size}" height="${size}" aria-hidden="true" focusable="false" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`
}

const ICONS = {
  play: svgIcon('<path d="M8 5.5v13l11-6.5-11-6.5z" fill="currentColor" stroke="none"></path>', 18),
  pause: svgIcon('<rect x="7" y="5" width="3.5" height="14" rx="1.2" fill="currentColor" stroke="none"></rect><rect x="13.5" y="5" width="3.5" height="14" rx="1.2" fill="currentColor" stroke="none"></rect>', 18),
  prev: svgIcon('<path d="M11 7 4 12l7 5V7z" fill="currentColor" stroke="none"></path><path d="M18 7v10"></path>', 16),
  next: svgIcon('<path d="m13 7 7 5-7 5V7z" fill="currentColor" stroke="none"></path><path d="M6 7v10"></path>', 16),
  loop: svgIcon('<path d="M3 12a7 7 0 0 1 7-7h8"></path><path d="m15 2 3 3-3 3"></path><path d="M21 12a7 7 0 0 1-7 7H6"></path><path d="m9 22-3-3 3-3"></path>', 16),
  volume: svgIcon('<path d="M4 10v4h3l4 3V7L7 10H4z" fill="currentColor" stroke="none"></path><path d="M15 9.5a3.5 3.5 0 0 1 0 5"></path><path d="M17 7.5a6 6 0 0 1 0 9"></path>', 16),
  mute: svgIcon('<path d="M4 10v4h3l4 3V7L7 10H4z" fill="currentColor" stroke="none"></path><path d="m16 9 5 6"></path><path d="m21 9-5 6"></path>', 16)
}

function resolveSource(track = {}) {
  const raw = String(track.sourceType || track.source || '').toLowerCase()
  if (raw === 'resource' || raw.includes('资源')) return { key: 'resource', label: '资源源' }
  return { key: 'play', label: '播放源' }
}

function parseLyric(lyricText = '') {
  const text = String(lyricText || '').trim()
  if (!text) return []
  const synced = []
  const plainLines = []
  const timeTagPattern = /\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]/g
  text.split(/\r?\n/).forEach((line) => {
    const cleanLine = line.trim()
    if (!cleanLine) return
    const timestamps = [...cleanLine.matchAll(timeTagPattern)]
    const lyric = cleanLine.replace(timeTagPattern, '').trim()
    if (!timestamps.length) {
      if (!/^\[[a-z]+:/i.test(cleanLine)) plainLines.push(cleanLine)
      return
    }
    if (!lyric) return
    timestamps.forEach((match) => {
      const minutes = Number(match[1])
      const seconds = Number(match[2])
      const fraction = match[3] ? Number(`0.${match[3].padEnd(3, '0').slice(0, 3)}`) : 0
      synced.push({ time: minutes * 60 + seconds + fraction, text: lyric })
    })
  })
  if (synced.length) return synced.sort((a, b) => a.time - b.time)
  return plainLines.map((line, index) => ({ time: index * 4, text: line }))
}

export function createPlainMusicPlayer(container, track, options = {}) {
  if (!container || !track?.url) return null
  container.innerHTML = ''

  const lyrics = parseLyric(track.lyric)
  const source = resolveSource(track)
  const audio = new Audio(track.url)
  audio.preload = 'metadata'
  audio.loop = true
  audio.volume = 0.85

  const root = document.createElement('div')
  root.className = 'plain-player'
  root.dataset.playing = 'false'
  root.dataset.source = source.key
  root.dataset.layout = PLAYER_LAYOUT_VERSION
  if (!lyrics.length) root.classList.add('plain-player--no-lyrics')

  const body = document.createElement('div')
  body.className = 'plain-player__body'

  const vinyl = document.createElement('div')
  vinyl.className = 'plain-player__vinyl'
  vinyl.setAttribute('aria-hidden', 'true')

  const disc = document.createElement('div')
  disc.className = 'plain-player__disc'

  const label = document.createElement('div')
  label.className = 'plain-player__label'
  if (track.cover) {
    const img = document.createElement('img')
    img.src = track.cover
    img.alt = ''
    img.draggable = false
    img.addEventListener('error', () => {
      img.remove()
      label.classList.add('plain-player__label--empty')
    })
    label.appendChild(img)
  } else {
    label.classList.add('plain-player__label--empty')
  }
  disc.appendChild(label)

  const arm = document.createElement('div')
  arm.className = 'plain-player__arm'
  arm.innerHTML = `
    <span class="plain-player__arm-pivot"></span>
    <span class="plain-player__arm-shaft"></span>
    <span class="plain-player__arm-cartridge"></span>
  `
  vinyl.append(disc, arm)

  const panel = document.createElement('div')
  panel.className = 'plain-player__panel'

  const head = document.createElement('div')
  head.className = 'plain-player__head'
  const title = document.createElement('div')
  title.className = 'plain-player__title'
  title.textContent = track.name || '未命名音乐'
  const artist = document.createElement('div')
  artist.className = 'plain-player__artist'
  artist.textContent = track.artist || '未知歌手'
  head.append(title, artist)

  const lyricList = document.createElement('div')
  lyricList.className = 'plain-player__lyric-list'
  lyricList.setAttribute('role', 'list')
  let activeLyricIndex = -1
  const lyricTrack = document.createElement('div')
  lyricTrack.className = 'plain-player__lyric-track'
  const lyricRows = lyrics.map((line, index) => {
    const row = document.createElement('div')
    row.className = 'plain-player__lyric-row'
    row.dataset.index = String(index)
    row.tabIndex = 0
    row.setAttribute('role', 'listitem')
    row.textContent = line.text
    row.addEventListener('click', () => seekToLyric(index, true))
    row.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') return
      event.preventDefault()
      seekToLyric(index, true)
    })
    lyricTrack.appendChild(row)
    return row
  })
  if (lyrics.length) {
    lyricList.appendChild(lyricTrack)
    lyricRows[0]?.classList.add('is-active')
    activeLyricIndex = 0
  } else {
    const empty = document.createElement('div')
    empty.className = 'plain-player__lyric-empty'
    empty.textContent = '暂无歌词'
    lyricList.appendChild(empty)
  }
  panel.append(head, lyricList)
  body.append(vinyl, panel)

  const footer = document.createElement('div')
  footer.className = 'plain-player__footer'

  const progress = document.createElement('div')
  progress.className = 'plain-player__progress'
  const currentTime = document.createElement('span')
  currentTime.className = 'plain-player__time'
  currentTime.textContent = '00:00'
  const seek = document.createElement('div')
  seek.className = 'plain-player__seek'
  const loaded = document.createElement('div')
  loaded.className = 'plain-player__loaded'
  const played = document.createElement('div')
  played.className = 'plain-player__played'
  const thumb = document.createElement('div')
  thumb.className = 'plain-player__thumb'
  seek.append(loaded, played, thumb)
  const durationTime = document.createElement('span')
  durationTime.className = 'plain-player__time'
  durationTime.textContent = '00:00'
  progress.append(currentTime, seek, durationTime)

  const controls = document.createElement('div')
  controls.className = 'plain-player__controls'

  const previousButton = document.createElement('button')
  previousButton.className = 'plain-player__control'
  previousButton.type = 'button'
  previousButton.setAttribute('aria-label', '后退 10 秒')
  previousButton.innerHTML = ICONS.prev

  const playButton = document.createElement('button')
  playButton.className = 'plain-player__play'
  playButton.type = 'button'
  playButton.setAttribute('aria-label', '播放')
  playButton.innerHTML = ICONS.play

  const nextButton = document.createElement('button')
  nextButton.className = 'plain-player__control'
  nextButton.type = 'button'
  nextButton.setAttribute('aria-label', '前进 10 秒')
  nextButton.innerHTML = ICONS.next

  const loopButton = document.createElement('button')
  loopButton.className = 'plain-player__extra is-on'
  loopButton.type = 'button'
  loopButton.setAttribute('aria-label', '循环播放')
  loopButton.title = '循环播放'
  loopButton.innerHTML = ICONS.loop

  const volumeWrap = document.createElement('div')
  volumeWrap.className = 'plain-player__volume'

  const volumeButton = document.createElement('button')
  volumeButton.className = 'plain-player__extra is-on'
  volumeButton.type = 'button'
  volumeButton.setAttribute('aria-label', '音量')
  volumeButton.title = '音量'
  volumeButton.innerHTML = ICONS.volume

  const volumePanel = document.createElement('div')
  volumePanel.className = 'plain-player__volume-panel'

  const volumeRange = document.createElement('input')
  volumeRange.className = 'plain-player__volume-range'
  volumeRange.type = 'range'
  volumeRange.min = '0'
  volumeRange.max = '100'
  volumeRange.step = '1'
  volumeRange.value = String(Math.round(audio.volume * 100))
  volumeRange.setAttribute('aria-label', '音量调节')

  const volumeValue = document.createElement('span')
  volumeValue.className = 'plain-player__volume-value'
  volumeValue.textContent = volumeRange.value + '%'

  volumePanel.append(volumeRange, volumeValue)
  volumeWrap.append(volumeButton, volumePanel)

  controls.append(previousButton, playButton, nextButton, loopButton, volumeWrap)
  footer.append(progress, controls)
  root.append(body, footer)
  container.appendChild(root)

  let controller
  let pointerTracking = false
  let rafId = 0
  let lastVolume = audio.volume

  function startProgressLoop() {
    if (rafId) return
    rafId = requestAnimationFrame(progressFrame)
  }
  function stopProgressLoop() {
    if (!rafId) return
    cancelAnimationFrame(rafId)
    rafId = 0
  }
  function progressFrame() {
    rafId = 0
    updateProgress()
    if (!audio.paused && !audio.ended) startProgressLoop()
  }
  function setText(el, value) {
    if (el.textContent !== value) el.textContent = value
  }
  function setPlaying(playing) {
    root.dataset.playing = playing ? 'true' : 'false'
    disc.classList.toggle('is-spinning', playing)
    arm.classList.toggle('is-on', playing)
    playButton.setAttribute('aria-label', playing ? '暂停' : '播放')
    playButton.innerHTML = playing ? ICONS.pause : ICONS.play
    if (playing) startProgressLoop()
    else stopProgressLoop()
  }
  function syncVolumeUi() {
    const pct = Math.round((audio.muted ? 0 : audio.volume) * 100)
    volumeRange.value = String(pct)
    volumeValue.textContent = pct + '%'
    volumePanel.style.setProperty('--vol-pct', pct + '%')
    volumeRange.style.setProperty('--vol-pct', pct + '%')
    const muted = audio.muted || audio.volume <= 0.001
    volumeButton.innerHTML = muted ? ICONS.mute : ICONS.volume
    volumeButton.classList.toggle('is-on', !muted)
    volumeButton.setAttribute('aria-label', muted ? '取消静音' : '音量')
  }
  function pause() { audio.pause() }
  function play() {
    if (options.mutex !== false) {
      activePlayers.forEach((item) => {
        if (item !== controller) item.pause()
      })
    }
    const promise = audio.play()
    setPlaying(true)
    if (promise && typeof promise.catch === 'function') {
      promise.catch(() => setPlaying(false))
    }
  }
  function toggle() {
    if (audio.paused) play()
    else pause()
  }
  function toggleLoop() {
    audio.loop = !audio.loop
    loopButton.classList.toggle('is-on', audio.loop)
    loopButton.setAttribute('aria-label', audio.loop ? '循环播放（开）' : '循环播放（关）')
  }
  function toggleMute() {
    if (audio.muted || audio.volume <= 0.001) {
      audio.muted = false
      audio.volume = lastVolume > 0.001 ? lastVolume : 0.85
    } else {
      lastVolume = audio.volume > 0.001 ? audio.volume : lastVolume
      audio.muted = true
      audio.volume = 0
    }
    syncVolumeUi()
  }
  function setVolume(raw) {
    let next = Number(raw)
    if (!Number.isFinite(next)) next = 0
    if (next > 1) next = next / 100
    next = clamp01(next)
    audio.muted = next <= 0.001
    audio.volume = next
    if (next > 0.001) lastVolume = next
    syncVolumeUi()
  }
  function openVolume(open) {
    volumeWrap.classList.toggle('is-open', open)
    if (open) syncVolumeUi()
  }
  function updateProgress() {
    const duration = audio.duration || 0
    const ratio = duration ? audio.currentTime / duration : 0
    setWidth(played, ratio)
    seek.style.setProperty('--plain-player-progress', clamp01(ratio))
    setText(currentTime, formatTime(audio.currentTime))
    setText(durationTime, formatTime(duration))
    updateLyric(audio.currentTime)
  }
  function updateLyric(current) {
    if (!lyrics.length) return
    const index = getLyricIndex(current)
    if (activeLyricIndex === index) return
    activeLyricIndex = index
    lyricRows.forEach((row, rowIndex) => {
      row.classList.toggle('is-active', rowIndex === index)
      row.classList.toggle('is-past', rowIndex < index)
    })
    requestAnimationFrame(() => {
      requestAnimationFrame(() => scrollActiveLyric())
    })
  }
  function getLyricIndex(current) {
    let index = 0
    for (let i = 0; i < lyrics.length; i += 1) {
      if (lyrics[i].time <= current + 0.12) index = i
      else break
    }
    return index
  }
  function scrollActiveLyric() {
    const row = lyricRows[activeLyricIndex]
    if (!row || !lyricList.clientHeight) return
    const listRect = lyricList.getBoundingClientRect()
    const rowRect = row.getBoundingClientRect()
    const rowCenter = rowRect.top - listRect.top + lyricList.scrollTop + rowRect.height / 2
    const top = Math.max(0, rowCenter - lyricList.clientHeight / 2)
    const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    try {
      lyricList.scrollTo({ top, behavior: reduceMotion ? 'auto' : 'smooth' })
    } catch {
      lyricList.scrollTop = top
    }
  }
  function updateLoaded() {
    const duration = audio.duration || 0
    if (!duration || !audio.buffered.length) {
      setWidth(loaded, 0)
      return
    }
    setWidth(loaded, audio.buffered.end(audio.buffered.length - 1) / duration)
  }
  function seekTo(clientX) {
    const duration = audio.duration || 0
    if (!duration) return
    const rect = seek.getBoundingClientRect()
    const ratio = rect.width ? (clientX - rect.left) / rect.width : 0
    audio.currentTime = Math.max(0, Math.min(1, ratio)) * duration
    updateProgress()
  }
  function seekToLyric(index, shouldPlay = false) {
    if (!lyrics[index]) return
    audio.currentTime = Math.max(0, lyrics[index].time)
    updateProgress()
    if (shouldPlay && audio.paused) play()
  }
  function seekBy(seconds) {
    const duration = audio.duration || 0
    if (!duration) return
    audio.currentTime = Math.max(0, Math.min(duration, audio.currentTime + seconds))
    updateProgress()
  }
  function onPointerMove(event) {
    if (pointerTracking) seekTo(event.clientX)
  }
  function onPointerUp() {
    pointerTracking = false
    document.removeEventListener('pointermove', onPointerMove)
    document.removeEventListener('pointerup', onPointerUp)
  }
  function onPointerDown(event) {
    pointerTracking = true
    seekTo(event.clientX)
    document.addEventListener('pointermove', onPointerMove)
    document.addEventListener('pointerup', onPointerUp)
  }
  function onDocPointerDown(event) {
    if (!volumeWrap.classList.contains('is-open')) return
    if (volumeWrap.contains(event.target)) return
    openVolume(false)
  }
  function onError() {
    root.dataset.error = 'true'
    title.textContent = '音乐加载失败'
    artist.textContent = '请更换曲目或来源'
    setPlaying(false)
  }

  playButton.addEventListener('click', toggle)
  previousButton.addEventListener('click', () => seekBy(-10))
  nextButton.addEventListener('click', () => seekBy(10))
  loopButton.addEventListener('click', toggleLoop)
  volumeButton.addEventListener('click', (event) => {
    event.preventDefault()
    event.stopPropagation()
    openVolume(!volumeWrap.classList.contains('is-open'))
  })
  volumeButton.addEventListener('contextmenu', (event) => {
    event.preventDefault()
    toggleMute()
  })
  volumePanel.addEventListener('pointerdown', (event) => event.stopPropagation())
  volumeRange.addEventListener('pointerdown', (event) => event.stopPropagation())
  volumeRange.addEventListener('input', () => setVolume(volumeRange.value))
  volumeRange.addEventListener('change', () => setVolume(volumeRange.value))
  volumeRange.addEventListener('click', (event) => event.stopPropagation())
  seek.addEventListener('pointerdown', onPointerDown)
  document.addEventListener('pointerdown', onDocPointerDown)
  audio.addEventListener('play', () => setPlaying(true))
  audio.addEventListener('pause', () => {
    setPlaying(false)
    updateProgress()
  })
  audio.addEventListener('ended', () => {
    setPlaying(false)
    updateProgress()
  })
  audio.addEventListener('timeupdate', () => {
    if (audio.paused || pointerTracking) updateProgress()
  })
  audio.addEventListener('durationchange', updateProgress)
  audio.addEventListener('progress', updateLoaded)
  audio.addEventListener('loadedmetadata', () => {
    updateProgress()
    updateLoaded()
  })
  audio.addEventListener('error', onError)

  syncVolumeUi()

  controller = {
    pause,
    destroy() {
      pause()
      stopProgressLoop()
      activePlayers.delete(controller)
      document.removeEventListener('pointermove', onPointerMove)
      document.removeEventListener('pointerup', onPointerUp)
      document.removeEventListener('pointerdown', onDocPointerDown)
      audio.removeAttribute('src')
      audio.load()
      if (container.dataset) delete container.dataset.mounted
      container.innerHTML = ''
    }
  }
  activePlayers.add(controller)
  if (lyrics.length) {
    requestAnimationFrame(() => {
      requestAnimationFrame(() => scrollActiveLyric())
    })
  }
  if (options.autoplay) setTimeout(play, 0)
  return controller
}


export const PLAIN_PLAYER_STYLE_ID = 'blog-plain-music-player-style'
export const PLAIN_PLAYER_CSS = "/* Soft white card player v5 — cleaner colors + volume */\r\n.md-music-player {\r\n  width: 100%;\r\n  margin: 0;\r\n  container-type: inline-size;\r\n  container-name: music-player;\r\n  background: transparent;\r\n  border: 0;\r\n  box-shadow: none;\r\n}\r\n\r\n.music-player-state {\r\n  min-height: 160px;\r\n  display: grid;\r\n  place-items: center;\r\n  gap: 6px;\r\n  padding: 22px 16px;\r\n  border-radius: 24px;\r\n  background: #fff;\r\n  border: 1px solid rgba(15, 23, 42, .07);\r\n  box-shadow: 0 14px 36px rgba(15, 23, 42, .07);\r\n  color: #64748b;\r\n  font-size: 12px;\r\n}\r\n.music-player-state__eq { display:flex; align-items:flex-end; gap:3px; height:14px; }\r\n.music-player-state__eq span { width:2.5px; height:7px; border-radius:99px; background:#3b82f6; animation: mp-eq .9s ease-in-out infinite; }\r\n.music-player-state__eq span:nth-child(2){ height:11px; animation-delay:.12s; }\r\n.music-player-state__eq span:nth-child(3){ height:8px; animation-delay:.24s; }\r\n.music-player-state__icon {\r\n  width:24px; height:24px; display:grid; place-items:center;\r\n  border-radius:8px; border:1px solid #fecaca; background:#fef2f2; color:#dc2626; font-weight:800;\r\n}\r\n.music-player-state__title { color:#0f172a; font-size:13px; font-weight:700; }\r\n.music-player-state__desc { color:#64748b; font-size:12px; }\r\n.music-player-state--error { color:#dc2626; }\r\n@keyframes mp-eq { 0%,100%{transform:scaleY(.4);opacity:.55} 50%{transform:scaleY(1);opacity:1} }\r\n\r\n.plain-player {\r\n  --mp-text: #0f172a;\r\n  --mp-sub: #64748b;\r\n  --mp-muted: #94a3b8;\r\n  --mp-line: rgba(15, 23, 42, .08);\r\n  --mp-accent: #3b82f6;\r\n  --mp-accent-soft: #eff6ff;\r\n  --mp-accent-deep: #2563eb;\r\n  --mp-dock: #f8fafc;\r\n  --mp-vinyl: 156px;\r\n  --mp-disc: 138px;\r\n  --mp-radius: 24px;\r\n  display: flex;\r\n  flex-direction: column;\r\n  overflow: hidden;\r\n  border: 1px solid var(--mp-line);\r\n  border-radius: var(--mp-radius);\r\n  background: #ffffff;\r\n  color: var(--mp-text);\r\n  box-shadow:\r\n    0 1px 0 rgba(255,255,255,.95) inset,\r\n    0 14px 36px rgba(15, 23, 42, .07);\r\n  font-family: \"Noto Sans SC\", \"SF Pro Display\", \"Microsoft YaHei\", system-ui, sans-serif;\r\n}\r\n.plain-player[data-source='play'] {\r\n  --mp-accent: #3b82f6;\r\n  --mp-accent-soft: #eff6ff;\r\n  --mp-accent-deep: #2563eb;\r\n}\r\n.plain-player[data-source='resource'] {\r\n  --mp-accent: #0d9488;\r\n  --mp-accent-soft: #f0fdfa;\r\n  --mp-accent-deep: #0f766e;\r\n}\r\n.plain-player[data-playing='true'] {\r\n  border-color: color-mix(in srgb, var(--mp-accent) 22%, var(--mp-line));\r\n  box-shadow:\r\n    0 1px 0 rgba(255,255,255,.95) inset,\r\n    0 16px 40px color-mix(in srgb, var(--mp-accent) 10%, rgba(15, 23, 42, .06));\r\n}\r\n\r\n.plain-player__body {\r\n  display: grid;\r\n  grid-template-columns: var(--mp-vinyl) minmax(0, 1fr);\r\n  gap: 16px;\r\n  align-items: center;\r\n  padding: 16px 16px 10px;\r\n}\r\n\r\n.plain-player__vinyl {\r\n  position: relative;\r\n  width: var(--mp-vinyl);\r\n  height: var(--mp-vinyl);\r\n  border-radius: 20px;\r\n  background: linear-gradient(160deg, #f8fafc, #eef2f7);\r\n  box-shadow:\r\n    0 10px 22px rgba(15, 23, 42, .08),\r\n    0 1px 0 rgba(255,255,255,.95) inset;\r\n  display: grid;\r\n  place-items: center;\r\n  overflow: visible;\r\n}\r\n.plain-player__disc {\r\n  position: relative;\r\n  width: var(--mp-disc);\r\n  height: var(--mp-disc);\r\n  border-radius: 50%;\r\n  background:\r\n    radial-gradient(circle at 50% 50%, rgba(255,255,255,.12) 0 9%, transparent 10%),\r\n    repeating-radial-gradient(circle at 50% 50%, rgba(255,255,255,.1) 0 1px, transparent 1px 5px),\r\n    linear-gradient(145deg, #3f4651, #1c2027 55%, #12151a);\r\n  box-shadow: 0 8px 16px rgba(0,0,0,.18);\r\n  animation: mp-spin 12s linear infinite;\r\n  animation-play-state: paused;\r\n  will-change: transform;\r\n}\r\n.plain-player__disc.is-spinning,\r\n.plain-player[data-playing='true'] .plain-player__disc { animation-play-state: running; }\r\n.plain-player__label {\r\n  position: absolute;\r\n  inset: 18%;\r\n  overflow: hidden;\r\n  border-radius: 50%;\r\n  background: #dbe3ef;\r\n  box-shadow: 0 0 0 2px rgba(255,255,255,.16);\r\n}\r\n.plain-player__label img { width:100%; height:100%; object-fit:cover; display:block; }\r\n.plain-player__label--empty::before {\r\n  content:'♪'; position:absolute; inset:0; display:grid; place-items:center; color:#8b98a8; font-size:22px;\r\n}\r\n\r\n.plain-player__arm {\r\n  position: absolute;\r\n  top: 8px; right: 8px;\r\n  width: 18px; height: 104px;\r\n  transform-origin: 9px 9px;\r\n  transform: rotate(0deg);\r\n  transition: transform 1s cubic-bezier(.22,1,.36,1);\r\n  pointer-events: none;\r\n  z-index: 6;\r\n  filter: drop-shadow(0 2px 5px rgba(15,23,42,.14));\r\n}\r\n.plain-player__arm-pivot {\r\n  position:absolute; top:0; left:50%;\r\n  width:18px; height:18px; border-radius:50%;\r\n  background: radial-gradient(circle at 35% 30%, #fff 0 18%, transparent 19%), linear-gradient(160deg,#fff 0%,#e5e7eb 48%,#c4c9d1 100%);\r\n  box-shadow: 0 0 0 2px #f3f5f8, 0 1px 2px rgba(15,23,42,.1);\r\n  transform: translateX(-50%); z-index:3;\r\n}\r\n.plain-player__arm-pivot::after {\r\n  content:''; position:absolute; top:50%; left:50%;\r\n  width:6px; height:6px; border-radius:50%;\r\n  background: linear-gradient(160deg,#f3f4f6,#a1a8b3);\r\n  transform: translate(-50%,-50%);\r\n}\r\n.plain-player__arm-shaft {\r\n  position:absolute; top:15px; left:50%;\r\n  width:2.5px; height:68px; border-radius:999px;\r\n  background: linear-gradient(180deg,#f8fafc 0%,#d1d5db 40%,#9ca3af 100%);\r\n  transform: translateX(-50%); z-index:1;\r\n}\r\n.plain-player__arm-shaft::before {\r\n  content:''; position:absolute; top:28px; left:50%;\r\n  width:7px; height:7px; border-radius:50%;\r\n  background: linear-gradient(160deg,#f8fafc,#b8bfc8);\r\n  transform: translateX(-50%);\r\n}\r\n.plain-player__arm-cartridge {\r\n  position:absolute; left:50%; bottom:0;\r\n  width:13px; height:15px;\r\n  border-radius:2px 2px 5px 5px;\r\n  background: linear-gradient(180deg,#f3f4f6 0%,#aab1bb 52%,#6b7280 100%);\r\n  transform: translateX(-50%); z-index:2;\r\n}\r\n.plain-player__arm-cartridge::before {\r\n  content:''; position:absolute; top:2px; left:50%;\r\n  width:7px; height:6px; border-radius:2px;\r\n  background: linear-gradient(180deg,#fff,#c5cad2);\r\n  transform: translateX(-50%);\r\n}\r\n.plain-player__arm-cartridge::after {\r\n  content:''; position:absolute; left:50%; bottom:1px;\r\n  width:3px; height:3px; border-radius:1px; background:#4b5563;\r\n  transform: translateX(-50%);\r\n}\r\n.plain-player[data-playing='true'] .plain-player__arm,\r\n.plain-player__arm.is-on { transform: rotate(12deg); }\r\n\r\n.plain-player__panel {\r\n  min-width: 0;\r\n  display: flex;\r\n  flex-direction: column;\r\n  justify-content: center;\r\n  gap: 8px;\r\n  min-height: var(--mp-vinyl);\r\n  padding: 2px 2px 0 0;\r\n}\r\n.plain-player__head { min-width: 0; }\r\n.plain-player__title {\r\n  overflow: hidden;\r\n  color: var(--mp-text);\r\n  font-size: 20px;\r\n  font-weight: 800;\r\n  line-height: 1.2;\r\n  letter-spacing: -.02em;\r\n  text-overflow: ellipsis;\r\n  white-space: nowrap;\r\n}\r\n.plain-player__artist {\r\n  margin-top: 4px;\r\n  overflow: hidden;\r\n  color: var(--mp-sub);\r\n  font-size: 13px;\r\n  font-weight: 500;\r\n  text-overflow: ellipsis;\r\n  white-space: nowrap;\r\n}\r\n\r\n.plain-player__lyric-list {\r\n  position: relative;\r\n  flex: 1 1 auto;\r\n  min-height: 84px;\r\n  max-height: 96px;\r\n  overflow-x: hidden;\r\n  overflow-y: auto;\r\n  mask-image: linear-gradient(180deg, transparent 0%, #000 10%, #000 90%, transparent 100%);\r\n  -webkit-overflow-scrolling: touch;\r\n}\r\n.plain-player__lyric-list::-webkit-scrollbar { width:0; height:0; }\r\n.plain-player__lyric-track {\r\n  display: flex;\r\n  flex-direction: column;\r\n  gap: 3px;\r\n  padding: 20px 0;\r\n}\r\n.plain-player__lyric-row {\r\n  min-height: 20px;\r\n  padding: 2px 0;\r\n  color: var(--mp-muted);\r\n  font-size: 13.5px;\r\n  line-height: 1.45;\r\n  cursor: pointer;\r\n  opacity: .78;\r\n  transition: color .2s ease, opacity .2s ease;\r\n}\r\n.plain-player__lyric-row:hover { color:#475569; opacity:1; }\r\n.plain-player__lyric-row.is-past { opacity:.42; }\r\n.plain-player__lyric-row.is-active {\r\n  color: var(--mp-accent-deep);\r\n  font-weight: 700;\r\n  opacity: 1;\r\n}\r\n.plain-player__lyric-empty {\r\n  height: 100%;\r\n  display: grid;\r\n  place-items: center;\r\n  color: var(--mp-muted);\r\n  font-size: 12px;\r\n}\r\n.plain-player--no-lyrics .plain-player__lyric-list {\r\n  min-height: 28px;\r\n  max-height: 28px;\r\n}\r\n\r\n.plain-player__footer {\r\n  display: flex;\r\n  flex-direction: column;\r\n  gap: 0;\r\n  border-top: 1px solid rgba(15, 23, 42, .05);\r\n  background: linear-gradient(180deg, #fcfdff 0%, var(--mp-dock) 100%);\r\n}\r\n.plain-player__progress {\r\n  display: grid;\r\n  grid-template-columns: 36px minmax(0, 1fr) 36px;\r\n  gap: 8px;\r\n  align-items: center;\r\n  padding: 12px 18px 0;\r\n}\r\n.plain-player__time {\r\n  color: #94a3b8;\r\n  font-size: 10px;\r\n  font-weight: 600;\r\n  font-variant-numeric: tabular-nums;\r\n  line-height: 1;\r\n}\r\n.plain-player__time:last-child { text-align: right; }\r\n.plain-player__seek {\r\n  --plain-player-progress: 0;\r\n  position: relative;\r\n  height: 4px;\r\n  border-radius: 999px;\r\n  background: rgba(148,163,184,.22);\r\n  cursor: pointer;\r\n  overflow: visible;\r\n}\r\n.plain-player__seek:hover { height: 5px; }\r\n.plain-player__loaded,\r\n.plain-player__played {\r\n  position: absolute;\r\n  inset: 0 auto 0 0;\r\n  width: 100%;\r\n  border-radius: inherit;\r\n  transform: scaleX(0);\r\n  transform-origin: left center;\r\n  transition: transform .08s linear;\r\n  will-change: transform;\r\n}\r\n.plain-player__loaded { background: rgba(148,163,184,.28); }\r\n.plain-player__played { background: var(--mp-accent); }\r\n.plain-player__thumb {\r\n  position: absolute;\r\n  top: 50%;\r\n  left: calc(var(--plain-player-progress, 0) * 100%);\r\n  width: 11px;\r\n  height: 11px;\r\n  border: 2px solid #fff;\r\n  border-radius: 50%;\r\n  background: var(--mp-accent);\r\n  box-shadow: 0 1px 4px rgba(15,23,42,.14);\r\n  transform: translate(-50%, -50%) scale(.7);\r\n  opacity: 0;\r\n  pointer-events: none;\r\n  transition: opacity .14s ease, transform .14s ease;\r\n}\r\n.plain-player__seek:hover .plain-player__thumb,\r\n.plain-player[data-playing='true'] .plain-player__thumb {\r\n  opacity: 1;\r\n  transform: translate(-50%, -50%) scale(1);\r\n}\r\n\r\n.plain-player__controls {\r\n  display: flex;\r\n  align-items: center;\r\n  justify-content: center;\r\n  gap: 14px;\r\n  padding: 10px 14px 14px;\r\n  position: relative;\r\n}\r\n.plain-player__control,\r\n.plain-player__play,\r\n.plain-player__extra {\r\n  border: 0;\r\n  background: transparent;\r\n  color: inherit;\r\n  cursor: pointer;\r\n}\r\n.plain-player__control,\r\n.plain-player__extra {\r\n  width: 38px;\r\n  height: 38px;\r\n  display: grid;\r\n  place-items: center;\r\n  border-radius: 50%;\r\n  color: #334155;\r\n  transition: background .14s ease, color .14s ease, transform .14s ease;\r\n}\r\n.plain-player__control:hover,\r\n.plain-player__extra:hover {\r\n  background: rgba(15,23,42,.05);\r\n  transform: translateY(-0.5px);\r\n}\r\n.plain-player__extra.is-on {\r\n  color: var(--mp-accent-deep);\r\n  background: var(--mp-accent-soft);\r\n}\r\n.plain-player__control .plain-player__icon,\r\n.plain-player__extra .plain-player__icon { width: 18px; height: 18px; }\r\n\r\n.plain-player__play {\r\n  width: 54px;\r\n  height: 54px;\r\n  display: grid;\r\n  place-items: center;\r\n  border-radius: 50%;\r\n  background: var(--mp-accent);\r\n  color: #fff;\r\n  box-shadow: 0 10px 20px color-mix(in srgb, var(--mp-accent) 28%, transparent);\r\n  transition: transform .14s ease, filter .14s ease, box-shadow .14s ease;\r\n}\r\n.plain-player__play:hover {\r\n  transform: scale(1.05);\r\n  filter: brightness(1.04);\r\n  box-shadow: 0 12px 24px color-mix(in srgb, var(--mp-accent) 34%, transparent);\r\n}\r\n.plain-player__play .plain-player__icon { width: 18px; height: 18px; }\r\n.plain-player[data-error='true'] .plain-player__play {\r\n  background: #cbd5e1;\r\n  color: #fff;\r\n  cursor: not-allowed;\r\n  transform: none;\r\n  box-shadow: none;\r\n  filter: none;\r\n}\r\n\r\n.plain-player__volume {\r\n  position: relative;\r\n  display: flex;\r\n  align-items: center;\r\n}\r\n.plain-player__volume-panel {\r\n  position: absolute;\r\n  left: 50%;\r\n  bottom: calc(100% + 10px);\r\n  transform: translateX(-50%);\r\n  width: min(176px, calc(100vw - 48px));\r\n  min-width: 156px;\r\n  height: 42px;\r\n  padding: 0 12px 0 14px;\r\n  border-radius: 999px;\r\n  background: #fff;\r\n  border: 1px solid rgba(15, 23, 42, .1);\r\n  box-shadow: 0 12px 28px rgba(15, 23, 42, .14);\r\n  display: none;\r\n  align-items: center;\r\n  gap: 10px;\r\n  z-index: 40;\r\n  pointer-events: auto;\r\n  box-sizing: border-box;\r\n}\r\n.plain-player__volume.is-open .plain-player__volume-panel {\r\n  display: flex;\r\n}\r\n.plain-player__volume-panel::after {\r\n  content: '';\r\n  position: absolute;\r\n  left: 50%;\r\n  bottom: -5px;\r\n  width: 10px;\r\n  height: 10px;\r\n  background: #fff;\r\n  border-right: 1px solid rgba(15, 23, 42, .1);\r\n  border-bottom: 1px solid rgba(15, 23, 42, .1);\r\n  transform: translateX(-50%) rotate(45deg);\r\n}\r\n.plain-player__volume-value {\r\n  min-width: 36px;\r\n  flex: 0 0 auto;\r\n  color: #64748b;\r\n  font-size: 12px;\r\n  font-weight: 700;\r\n  font-variant-numeric: tabular-nums;\r\n  text-align: right;\r\n  white-space: nowrap;\r\n  line-height: 1;\r\n}\r\n.plain-player__volume-range {\r\n  -webkit-appearance: none;\r\n  appearance: none;\r\n  flex: 1 1 auto;\r\n  width: 90px;\r\n  height: 18px;\r\n  margin: 0;\r\n  background: transparent;\r\n  cursor: pointer;\r\n}\r\n.plain-player__volume-range:focus { outline: none; }\r\n.plain-player__volume-range::-webkit-slider-runnable-track {\r\n  height: 6px;\r\n  border-radius: 999px;\r\n  background: linear-gradient(90deg, var(--mp-accent) var(--vol-pct, 85%), rgba(148,163,184,.28) var(--vol-pct, 85%));\r\n}\r\n.plain-player__volume-range::-webkit-slider-thumb {\r\n  -webkit-appearance: none;\r\n  appearance: none;\r\n  width: 14px;\r\n  height: 14px;\r\n  margin-top: -4px;\r\n  border-radius: 50%;\r\n  background: var(--mp-accent);\r\n  border: 2px solid #fff;\r\n  box-shadow: 0 1px 4px rgba(15,23,42,.18);\r\n}\r\n.plain-player__volume-range::-moz-range-track {\r\n  height: 6px;\r\n  border-radius: 999px;\r\n  background: rgba(148,163,184,.28);\r\n}\r\n.plain-player__volume-range::-moz-range-progress {\r\n  height: 6px;\r\n  border-radius: 999px;\r\n  background: var(--mp-accent);\r\n}\r\n.plain-player__volume-range::-moz-range-thumb {\r\n  width: 14px;\r\n  height: 14px;\r\n  border-radius: 50%;\r\n  background: var(--mp-accent);\r\n  border: 2px solid #fff;\r\n  box-shadow: 0 1px 4px rgba(15,23,42,.18);\r\n}\r\n\r\n@keyframes mp-spin { from{transform:rotate(0)} to{transform:rotate(360deg)} }\r\n\r\n@container music-player (max-width: 420px) {\r\n  .plain-player {\r\n    --mp-vinyl: 124px;\r\n    --mp-disc: 110px;\r\n    --mp-radius: 22px;\r\n  }\r\n  .plain-player__body { gap: 12px; padding: 14px 14px 8px; }\r\n  .plain-player__vinyl { border-radius: 18px; }\r\n  .plain-player__title { font-size: 17px; }\r\n  .plain-player__artist { font-size: 12.5px; }\r\n  .plain-player__lyric-list { min-height: 68px; max-height: 78px; }\r\n  .plain-player__lyric-row { font-size: 12.5px; }\r\n  .plain-player__arm { height: 86px; }\r\n  .plain-player__arm-shaft { height: 54px; }\r\n  .plain-player__controls { gap: 10px; padding: 8px 10px 12px; }\r\n  .plain-player__play { width: 48px; height: 48px; }\r\n  .plain-player__progress { padding: 10px 14px 0; }\r\n}\r\n\r\n@container music-player (max-width: 340px) {\r\n  .plain-player {\r\n    --mp-vinyl: 100px;\r\n    --mp-disc: 88px;\r\n    --mp-radius: 18px;\r\n  }\r\n  .plain-player__body { gap: 10px; padding: 12px 12px 6px; }\r\n  .plain-player__vinyl { border-radius: 16px; }\r\n  .plain-player__title { font-size: 15px; }\r\n  .plain-player__artist { font-size: 12px; }\r\n  .plain-player__lyric-list { min-height: 54px; max-height: 60px; }\r\n  .plain-player__lyric-row { font-size: 12px; min-height: 16px; }\r\n  .plain-player__arm { top:6px; right:6px; height:70px; width:16px; transform-origin:8px 8px; }\r\n  .plain-player__arm-pivot { width:16px; height:16px; }\r\n  .plain-player__arm-shaft { height:44px; }\r\n  .plain-player__controls { gap: 8px; padding: 8px 8px 12px; }\r\n  .plain-player__control, .plain-player__extra { width: 34px; height: 34px; }\r\n  .plain-player__play { width: 44px; height: 44px; }\r\n  .plain-player__progress { padding: 8px 12px 0; grid-template-columns: 30px minmax(0,1fr) 30px; }\r\n  .plain-player__time { font-size: 9.5px; }\r\n}\r\n\r\n@container music-player (max-width: 280px) {\r\n  .plain-player {\r\n    --mp-vinyl: 86px;\r\n    --mp-disc: 76px;\r\n  }\r\n  .plain-player__body { gap: 8px; padding: 10px; }\r\n  .plain-player__title { font-size: 14px; }\r\n  .plain-player__lyric-list { min-height: 44px; max-height: 50px; }\r\n  .plain-player__controls { gap: 6px; }\r\n  .plain-player__play { width: 40px; height: 40px; }\r\n}\r\n\r\n\r\n@container music-player (max-width: 420px) {\r\n  .plain-player__volume-panel {\r\n    left: auto;\r\n    right: 0;\r\n    transform: none;\r\n    width: 168px;\r\n    min-width: 168px;\r\n  }\r\n  .plain-player__volume-panel::after {\r\n    left: auto;\r\n    right: 14px;\r\n    transform: rotate(45deg);\r\n  }\r\n  .plain-player__volume-value {\r\n    min-width: 38px;\r\n    font-size: 12px;\r\n  }\r\n}\r\n\r\n@media (prefers-reduced-motion: reduce) {\r\n  .music-player-state__eq span { animation: none !important; }\r\n  .plain-player__arm { transition: transform 1s cubic-bezier(.22,1,.36,1) !important; }\r\n}\r\n"

export function ensurePlainPlayerStyles() {
  if (typeof document === 'undefined') return
  const style = document.getElementById(PLAIN_PLAYER_STYLE_ID)
  if (style) {
    if (style.textContent !== PLAIN_PLAYER_CSS) style.textContent = PLAIN_PLAYER_CSS
    return
  }
  const el = document.createElement('style')
  el.id = PLAIN_PLAYER_STYLE_ID
  el.textContent = PLAIN_PLAYER_CSS
  document.head.appendChild(el)
}
