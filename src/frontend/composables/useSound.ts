/**
 * useSound.ts — 音效系统 (Web Audio API 合成音)
 *
 * 音效清单：
 *   inkDrop    — 抽卡水滴叮
 *   paperRub   — 擦墨沙沙声
 *   windChime  — 卡片显现风铃
 *   brushTap   — 选择舔笔声
 *   bell       — 章节编钟
 *   chime      — 显灵磬音
 *   jadeClick  — 合券玉击
 *
 * 音量上限 0.3，soundEnabled 存 localStorage
 */

import { ref, watch } from 'vue'

export type SoundType =
  | 'inkDrop'
  | 'paperRub'
  | 'windChime'
  | 'brushTap'
  | 'bell'
  | 'chime'
  | 'jadeClick'

const SOUND_ENABLED_KEY = 'arbitrary_gate_sound_enabled'

// ─── Singleton state ────────────────────────────────────────────────────────

export const soundEnabled = ref<boolean>(
  localStorage.getItem(SOUND_ENABLED_KEY) !== 'false' // 默认开启
)

watch(soundEnabled, (val) => {
  localStorage.setItem(SOUND_ENABLED_KEY, String(val))
})

let audioCtx: AudioContext | null = null

function getCtx(): AudioContext {
  if (!audioCtx) {
    audioCtx = new AudioContext()
  }
  if (audioCtx.state === 'suspended') {
    audioCtx.resume()
  }
  return audioCtx
}

// ─── Volume helper ────────────────────────────────────────────────────────────

const MAX_VOLUME = 0.3

function gainNode(ctx: AudioContext, volume: number): GainNode {
  const g = ctx.createGain()
  g.gain.value = Math.min(volume, MAX_VOLUME)
  g.connect(ctx.destination)
  return g
}

// ─── Noise buffer (用于 paperRub) ────────────────────────────────────────────

function createNoiseBuffer(ctx: AudioContext): AudioBuffer {
  const bufferSize = ctx.sampleRate * 0.5
  const buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate)
  const data = buffer.getChannelData(0)
  for (let i = 0; i < bufferSize; i++) {
    data[i] = Math.random() * 2 - 1
  }
  return buffer
}

// ─── Internal sound generators ───────────────────────────────────────────────

function genInkDrop(): void {
  const ctx = getCtx()
  const gain = gainNode(ctx, 0.25)
  const osc = ctx.createOscillator()
  osc.type = 'sine'
  osc.frequency.setValueAtTime(880, ctx.currentTime)
  osc.frequency.exponentialRampToValueAtTime(440, ctx.currentTime + 0.15)
  const oscGain = ctx.createGain()
  oscGain.gain.setValueAtTime(0.6, ctx.currentTime)
  oscGain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3)
  osc.connect(oscGain)
  oscGain.connect(gain)
  osc.start(ctx.currentTime)
  osc.stop(ctx.currentTime + 0.3)
}

function genPaperRub(): void {
  const ctx = getCtx()
  const gain = gainNode(ctx, 0.2)
  const noiseBuffer = createNoiseBuffer(ctx)
  const source = ctx.createBufferSource()
  source.buffer = noiseBuffer
  const filter = ctx.createBiquadFilter()
  filter.type = 'bandpass'
  filter.frequency.value = 800
  filter.Q.value = 0.5
  source.connect(filter)
  filter.connect(gain)
  gain.gain.setValueAtTime(0.001, ctx.currentTime)
  gain.gain.linearRampToValueAtTime(0.4, ctx.currentTime + 0.05)
  gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.4)
  source.start(ctx.currentTime)
  source.stop(ctx.currentTime + 0.4)
}

function genWindChime(): void {
  const ctx = getCtx()
  const gain = gainNode(ctx, 0.15)
  const frequencies = [1047, 1319, 1568, 2093]
  frequencies.forEach((freq, i) => {
    const osc = ctx.createOscillator()
    osc.type = 'sine'
    osc.frequency.value = freq
    const g = ctx.createGain()
    g.gain.setValueAtTime(0, ctx.currentTime + i * 0.08)
    g.gain.linearRampToValueAtTime(0.3, ctx.currentTime + i * 0.08 + 0.02)
    g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + i * 0.08 + 0.8)
    osc.connect(g)
    g.connect(gain)
    osc.start(ctx.currentTime + i * 0.08)
    osc.stop(ctx.currentTime + i * 0.08 + 0.8)
  })
}

function genBrushTap(): void {
  const ctx = getCtx()
  const gain = gainNode(ctx, 0.2)
  const osc = ctx.createOscillator()
  osc.type = 'triangle'
  osc.frequency.setValueAtTime(2000, ctx.currentTime)
  osc.frequency.exponentialRampToValueAtTime(600, ctx.currentTime + 0.05)
  const g = ctx.createGain()
  g.gain.setValueAtTime(0.5, ctx.currentTime)
  g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.08)
  osc.connect(g)
  g.connect(gain)
  osc.start(ctx.currentTime)
  osc.stop(ctx.currentTime + 0.08)
}

function genBell(): void {
  const ctx = getCtx()
  const gain = gainNode(ctx, 0.25)
  const fundamentals = [523, 659, 784, 1047]
  fundamentals.forEach((freq, i) => {
    const osc = ctx.createOscillator()
    osc.type = 'sine'
    osc.frequency.value = freq
    const g = ctx.createGain()
    const amp = [0.5, 0.25, 0.15, 0.1][i]
    g.gain.setValueAtTime(amp, ctx.currentTime)
    g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 1.5)
    osc.connect(g)
    g.connect(gain)
    osc.start(ctx.currentTime)
    osc.stop(ctx.currentTime + 1.5)
  })
}

function genChime(): void {
  const ctx = getCtx()
  const gain = gainNode(ctx, 0.25)
  const frequencies = [1200, 1600, 2000, 2400]
  frequencies.forEach((freq, i) => {
    const osc = ctx.createOscillator()
    osc.type = 'sine'
    osc.frequency.value = freq
    const g = ctx.createGain()
    g.gain.setValueAtTime(0, ctx.currentTime + i * 0.06)
    g.gain.linearRampToValueAtTime(0.3, ctx.currentTime + i * 0.06 + 0.01)
    g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + i * 0.06 + 1.0)
    osc.connect(g)
    g.connect(gain)
    osc.start(ctx.currentTime + i * 0.06)
    osc.stop(ctx.currentTime + i * 0.06 + 1.0)
  })
}

function genJadeClick(): void {
  const ctx = getCtx()
  const gain = gainNode(ctx, 0.2)
  const osc = ctx.createOscillator()
  osc.type = 'sine'
  osc.frequency.setValueAtTime(1500, ctx.currentTime)
  osc.frequency.exponentialRampToValueAtTime(800, ctx.currentTime + 0.04)
  const g = ctx.createGain()
  g.gain.setValueAtTime(0.6, ctx.currentTime)
  g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.1)
  osc.connect(g)
  g.connect(gain)
  osc.start(ctx.currentTime)
  osc.stop(ctx.currentTime + 0.1)
}

// ─── Sound player map ────────────────────────────────────────────────────────

const soundPlayers: Record<SoundType, () => void> = {
  inkDrop: genInkDrop,
  paperRub: genPaperRub,
  windChime: genWindChime,
  brushTap: genBrushTap,
  bell: genBell,
  chime: genChime,
  jadeClick: genJadeClick,
}

// ─── Composable ──────────────────────────────────────────────────────────────

export function useSound() {
  /**
   * 触发一个音效（如果 soundEnabled 为 true）
   */
  function playSound(type: SoundType): void {
    if (!soundEnabled.value) return
    try {
      soundPlayers[type]()
    } catch (err) {
      console.warn('[useSound] playback error:', err)
    }
  }

  return {
    soundEnabled,
    playSound,
  }
}

// ─── Convenience named exports ──────────────────────────────────────────────

/** 抽卡成功 */
export function playInkDrop() {
  if (soundEnabled.value) genInkDrop()
}

/** 擦墨 */
export function playPaperRub() {
  if (soundEnabled.value) genPaperRub()
}

/** 卡片显现 */
export function playWindChime() {
  if (soundEnabled.value) genWindChime()
}

/** 选择手势 */
export function playBrushTap() {
  if (soundEnabled.value) genBrushTap()
}

/** 章节结束 */
export function playBell() {
  if (soundEnabled.value) genBell()
}

/** 关键词显灵 */
export function playChime() {
  if (soundEnabled.value) genChime()
}

/** 合券成功 */
export function playJadeClick() {
  if (soundEnabled.value) genJadeClick()
}
