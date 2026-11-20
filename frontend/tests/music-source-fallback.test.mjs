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
