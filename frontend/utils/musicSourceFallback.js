/** Switches a failed audio element to the next same-song source in order. */
export function attachMusicSourceFallback(audio, candidates, {
  shouldResume = () => false,
  onChange = () => {},
  onExhausted = () => {}
} = {}) {
  const sources = Array.isArray(candidates) ? candidates.filter(candidate => candidate?.url) : []
  let index = 0
  let disposed = false
  let progressTimer = 0
  let pendingPosition = null
  let lastPosition = Number.isFinite(audio.currentTime) ? Math.max(0, audio.currentTime) : 0
  let loadedAt = Date.now()
  let retry = 0
  let exhausted = false
  // Mobile browsers can reset currentTime to zero before firing an error for an
  // expired Range request. Keep the user's target until the seek has completed.
  const restorePosition = () => {
    if (disposed || pendingPosition === null || !Number.isFinite(audio.duration) || audio.duration <= 0) return
    try { audio.currentTime = Math.min(pendingPosition, audio.duration) } catch { /* wait for metadata */ }
  }
  const onTimeUpdate = () => {
    if (pendingPosition === null && Number.isFinite(audio.currentTime)) lastPosition = Math.max(0, audio.currentTime)
  }
  const onSeeked = () => {
    if (Number.isFinite(audio.currentTime)) lastPosition = Math.max(0, audio.currentTime)
    pendingPosition = null
  }
  const loadSource = (candidate, refresh = false) => {
    loadedAt = Date.now()
    const separator = candidate.url.includes('?') ? '&' : '?'
    audio.src = refresh ? `${candidate.url}${separator}_music_retry=${loadedAt}-${++retry}` : candidate.url
    audio.load()
    if (shouldResume()) {
      const promise = audio.play()
      if (promise && typeof promise.catch === 'function') promise.catch(() => {})
    }
  }
  const onPlaying = () => {
    clearTimeout(progressTimer)
    const start = audio.currentTime
    progressTimer = setTimeout(() => {
      if (!disposed && !audio.paused && audio.currentTime - start < .25) onError()
    }, 10000)
  }

  const onError = () => {
    if (disposed) return
    clearTimeout(progressTimer)
    const nextIndex = index + 1
    const candidate = sources[nextIndex]
    if (pendingPosition === null) {
      const currentPosition = Number.isFinite(audio.currentTime) ? Math.max(0, audio.currentTime) : 0
      pendingPosition = currentPosition === 0 && lastPosition > 0 ? lastPosition : currentPosition
    }
    if (Date.now() - loadedAt > 120000 && sources[index]) {
      loadSource(sources[index], true)
      return
    }
    if (!candidate) {
      if (!exhausted) onExhausted()
      exhausted = true
      return
    }
    index = nextIndex
    onChange(candidate, index)
    loadSource(candidate)
  }

  audio.addEventListener('error', onError)
  audio.addEventListener('playing', onPlaying)
  audio.addEventListener('loadedmetadata', restorePosition)
  audio.addEventListener('durationchange', restorePosition)
  audio.addEventListener('timeupdate', onTimeUpdate)
  audio.addEventListener('seeked', onSeeked)
  const dispose = () => {
    disposed = true
    clearTimeout(progressTimer)
    audio.removeEventListener('error', onError)
    audio.removeEventListener('playing', onPlaying)
    audio.removeEventListener('loadedmetadata', restorePosition)
    audio.removeEventListener('durationchange', restorePosition)
    audio.removeEventListener('timeupdate', onTimeUpdate)
    audio.removeEventListener('seeked', onSeeked)
  }
  dispose.seekTo = position => {
    if (disposed || !Number.isFinite(position)) return
    pendingPosition = Math.max(0, position)
    exhausted = false
    if (Date.now() - loadedAt > 120000 && sources[index]) loadSource(sources[index], true)
    else {
      try { audio.currentTime = Number.isFinite(audio.duration) && audio.duration > 0 ? Math.min(pendingPosition, audio.duration) : pendingPosition }
      catch { if (sources[index]) loadSource(sources[index], true) }
    }
  }
  return dispose
}
