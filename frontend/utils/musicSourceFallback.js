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
    if (!candidate) {
      onExhausted()
      return
    }
    index = nextIndex
    const position = Number.isFinite(audio.currentTime) && audio.currentTime > 0 ? audio.currentTime : 0
    const restorePosition = () => {
      if (!Number.isFinite(audio.duration) || audio.duration <= 0) return
      audio.currentTime = Math.min(position, audio.duration)
    }
    audio.addEventListener('loadedmetadata', restorePosition, { once: true })
    onChange(candidate, index)
    audio.src = candidate.url
    audio.load()
    if (shouldResume()) {
      const playPromise = audio.play()
      if (playPromise && typeof playPromise.catch === 'function') playPromise.catch(() => {})
    }
  }

  audio.addEventListener('error', onError)
  audio.addEventListener('playing', onPlaying)
  return () => {
    disposed = true
    clearTimeout(progressTimer)
    audio.removeEventListener('error', onError)
    audio.removeEventListener('playing', onPlaying)
  }
}
