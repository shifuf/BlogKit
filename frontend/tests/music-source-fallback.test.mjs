import test from 'node:test'
import assert from 'node:assert/strict'
import { attachMusicSourceFallback } from '../utils/musicSourceFallback.js'

test('audio errors advance to the next candidate and stop after the final source', () => {
  class FakeAudio extends EventTarget {
    constructor() { super(); this.src = ''; this.loads = 0; this.plays = 0; this.currentTime = 42; this.duration = 100 }
    load() { this.loads += 1 }
    play() { this.plays += 1; return Promise.resolve() }
  }

  const audio = new FakeAudio()
  const candidates = [{ source: 'qq', url: '/qq' }, { source: 'chksz_qq', url: '/chksz' }]
  const changes = []
  let exhausted = 0
  const dispose = attachMusicSourceFallback(audio, candidates, {
    shouldResume: () => true,
    onChange: candidate => changes.push(candidate.source),
    onExhausted: () => { exhausted += 1 }
  })

  audio.dispatchEvent(new Event('error'))
  assert.equal(audio.src, '/chksz')
  assert.equal(audio.loads, 1)
  assert.equal(audio.plays, 1)
  audio.dispatchEvent(new Event('loadedmetadata'))
  assert.equal(audio.currentTime, 42)
  assert.deepEqual(changes, ['chksz_qq'])

  audio.dispatchEvent(new Event('error'))
  assert.equal(exhausted, 1)
  dispose()
})

test('source fallback does not restart a player the listener paused', () => {
  class FakeAudio extends EventTarget {
    constructor() { super(); this.src = ''; this.loads = 0; this.plays = 0; this.currentTime = 42; this.duration = 100 }
    load() { this.loads += 1 }
    play() { this.plays += 1; return Promise.resolve() }
  }
  const audio = new FakeAudio()
  attachMusicSourceFallback(audio, [{ url: '/qq' }, { url: '/chksz' }], { shouldResume: () => false })
  audio.dispatchEvent(new Event('error'))
  assert.equal(audio.src, '/chksz')
  assert.equal(audio.plays, 0)
})

test('seeking after hours reloads the same proxy and restores the requested position while paused', t => {
  t.mock.timers.enable({ apis: ['Date'], now: 1000 })
  class FakeAudio extends EventTarget {
    constructor() { super(); this.src = '/stream?rid=one'; this.currentTime = 12; this.duration = 100; this.loads = 0; this.plays = 0 }
    load() { this.loads++; this.currentTime = 0; this.duration = NaN }
    play() { this.plays++; return Promise.resolve() }
  }
  const audio = new FakeAudio()
  const dispose = attachMusicSourceFallback(audio, [{ url: '/stream?rid=one' }], { shouldResume: () => false })
  t.mock.timers.setTime(3 * 60 * 60 * 1000)
  assert.equal(typeof dispose.seekTo, 'function')
  dispose.seekTo(65)
  assert.equal(audio.loads, 1)
  assert.match(audio.src, /^\/stream\?rid=one&/)
  audio.dispatchEvent(new Event('loadedmetadata')) // Some mobile streams report no duration yet.
  audio.duration = 100
  audio.dispatchEvent(new Event('durationchange'))
  assert.equal(audio.currentTime, 65)
  assert.equal(audio.plays, 0)
  dispose()
})

test('expired source recovery uses the last playback position if the browser resets before error', t => {
  t.mock.timers.enable({ apis: ['Date'], now: 1000 })
  class FakeAudio extends EventTarget {
    constructor() { super(); this.src = '/stream?rid=one'; this.currentTime = 80; this.duration = 120; this.loads = 0; this.plays = 0 }
    load() { this.loads++; this.currentTime = 0; this.duration = NaN }
    play() { this.plays++; return Promise.resolve() }
  }
  const audio = new FakeAudio()
  const dispose = attachMusicSourceFallback(audio, [{ url: '/stream?rid=one' }], { shouldResume: () => true })

  audio.dispatchEvent(new Event('timeupdate'))
  t.mock.timers.setTime(3 * 60 * 1000)
  audio.currentTime = 0 // The browser resets its Range position before emitting error.
  audio.dispatchEvent(new Event('error'))
  assert.equal(audio.loads, 1)
  assert.equal(audio.plays, 1)
  audio.dispatchEvent(new Event('loadedmetadata'))
  audio.duration = 120
  audio.dispatchEvent(new Event('durationchange'))
  assert.equal(audio.currentTime, 80)
  dispose()
})

test('failed seeks preserve the target through fallback, and disposal removes pending restoration', () => {
  class FakeAudio extends EventTarget {
    constructor() { super(); this.src = '/first'; this.currentTime = 12; this.duration = 100; this.plays = 0 }
    load() { this.currentTime = 0; this.duration = NaN }
    play() { this.plays++; return Promise.resolve() }
  }
  const audio = new FakeAudio()
  const dispose = attachMusicSourceFallback(audio, [{ url: '/first' }, { url: '/second' }])
  assert.equal(typeof dispose.seekTo, 'function')
  dispose.seekTo(75)
  audio.currentTime = 0 // Mobile browser clears its time when a Range request fails.
  audio.dispatchEvent(new Event('error'))
  audio.duration = 100
  audio.dispatchEvent(new Event('loadedmetadata'))
  assert.equal(audio.currentTime, 75)
  dispose()
  audio.currentTime = 3
  audio.dispatchEvent(new Event('durationchange'))
  assert.equal(audio.currentTime, 3)
})
