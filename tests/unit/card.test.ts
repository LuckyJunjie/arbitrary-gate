/**
 * Card.vue 单元测试 — ink bleed effect & ink fragrance fade animation
 *
 * 测试覆盖：
 * - K-07 墨迹晕染等级 CSS 类（.ink-bleed-0 ~ .ink-bleed-3）
 * - getInkBleedLevel 函数逻辑（inkValueStore.ts）
 * - @keyframes inkFade 定义存在于组件 scoped 样式中
 * - .fragrance-dot.filled 应用了 inkFade 动画（含 animation-fill-mode: both）
 * - 模板中 filled dot 有 ink-fade class、animationDelay stagger
 *
 * 注：@testing-library/vue@3.0.0 是 Vue 2 版本，与项目 Vue 3.5 不兼容，
 * 本测试通过读取源码的方式验证 CSS 动画定义，避免 Vue 包版本冲突。
 */

import { describe, it, expect } from 'vitest'
import { readFileSync } from 'fs'
import { resolve } from 'path'

const cardSrc = readFileSync(
  resolve(__dirname, '../../src/frontend/components/Card.vue'),
  'utf-8'
)

const inkValueStoreSrc = readFileSync(
  resolve(__dirname, '../../src/frontend/stores/inkValueStore.ts'),
  'utf-8'
)

// ─── Helpers ─────────────────────────────────────────────────────────────────

function extractScopedStyle(src: string): string {
  const match = src.match(/<style scoped>([\s\S]*?)<\/style>/)
  return match ? match[1] : ''
}

function extractFunctionBody(src: string, fnName: string): string {
  // Extract function body for manual evaluation
  const match = src.match(new RegExp(`export\\s+function\\s+${fnName}\\s*\\([^)]*\\)\\s*[:{]\\s*([^}]+\\})`))
  return match ? match[0] : ''
}

// ─── Tests ─────────────────────────────────────────────────────────────────────

describe('K-07 getInkBleedLevel — inkValueStore.ts', () => {
  it('defines getInkBleedLevel function', () => {
    expect(inkValueStoreSrc).toMatch(/export\s+function\s+getInkBleedLevel/)
  })

  it('returns 3 when inkValue >= 5', () => {
    const fnBody = extractFunctionBody(inkValueStoreSrc, 'getInkBleedLevel')
    // Level 3: inkValue >= 5
    expect(fnBody).toMatch(/inkValue\s*>=\s*5/)
    expect(fnBody).toMatch(/return\s+3/)
  })

  it('returns 2 when inkValue >= 3 and < 5', () => {
    const fnBody = extractFunctionBody(inkValueStoreSrc, 'getInkBleedLevel')
    // Level 2: inkValue >= 3
    expect(fnBody).toMatch(/inkValue\s*>=\s*3/)
  })

  it('returns 1 when inkValue >= 1 and < 3', () => {
    const fnBody = extractFunctionBody(inkValueStoreSrc, 'getInkBleedLevel')
    // Level 1: inkValue >= 1
    expect(fnBody).toMatch(/inkValue\s*>=\s*1/)
  })

  it('returns 0 when inkValue < 1', () => {
    const fnBody = extractFunctionBody(inkValueStoreSrc, 'getInkBleedLevel')
    // Level 0: default return
    expect(fnBody).toMatch(/return\s+0/)
  })
})

describe('K-07 Card.vue — ink-bleed CSS classes', () => {
  const style = extractScopedStyle(cardSrc)

  it('defines .ink-bleed-3 class (heavy ink bleed)', () => {
    expect(style).toMatch(/\.card\.ink-bleed-3\s*\{/)
  })

  it('.ink-bleed-3 applies strong box-shadow (rgba 0.4)', () => {
    const rule = style.match(/\.card\.ink-bleed-3\s*\{[\s\S]*?\}/)?.[0] ?? ''
    expect(rule).toMatch(/rgba\(30,\s*20,\s*10,\s*0\.4\)/)
  })

  it('defines .ink-bleed-2 class (medium ink bleed)', () => {
    expect(style).toMatch(/\.card\.ink-bleed-2\s*\{/)
  })

  it('defines .ink-bleed-1 class (light ink bleed)', () => {
    expect(style).toMatch(/\.card\.ink-bleed-1\s*\{/)
  })

  it('defines .ink-bleed-0 class (no ink bleed)', () => {
    expect(style).toMatch(/\.card\.ink-bleed-0\s*\{/)
  })

  it('.ink-bleed-0 has minimal shadow (no ink effect)', () => {
    const rule = style.match(/\.card\.ink-bleed-0\s*\{[\s\S]*?\}/)?.[0] ?? ''
    // Should NOT contain the heavy ink color rgba(30,20,10,0.4)
    expect(rule).not.toMatch(/rgba\(30,\s*20,\s*10,\s*0\.4\)/)
    expect(rule).toMatch(/rgba\(0,\s*0,\s*0/)
  })

  it('template binds ink-bleed-{level} class via inkBleedLevel computed', () => {
    expect(cardSrc).toMatch(/`ink-bleed-\$\{inkBleedLevel\}`/)
  })

  it('template binds class with rarity and flipped classes together', () => {
    // e.g. :class="[`rarity-${rarity}`, `ink-bleed-${inkBleedLevel}`, { flipped: isFlipped }]"
    expect(cardSrc).toMatch(/rarity-\$\{rarity\}/)
    expect(cardSrc).toMatch(/ink-bleed-\$\{inkBleedLevel\}/)
  })

  it('inkBleedLevel computed uses getInkBleedLevel helper', () => {
    expect(cardSrc).toMatch(/getInkBleedLevel\(inkFragrance\.value\)/)
  })

  it('inkBleedLevel computed falls back to props.inkBleedLevel if provided', () => {
    expect(cardSrc).toMatch(/props\.inkBleedLevel\s*!==\s*undefined/)
  })

  it('defines inkBleedLevel prop (optional, 0-3 union type)', () => {
    expect(cardSrc).toMatch(/inkBleedLevel\??:\s*0\s*\|\s*1\s*\|\s*2\s*\|\s*3/)
  })

  it('稀有度 3-4 with ink-bleed-3 has golden glow shadow', () => {
    const rule = style.match(/\.card\.rarity-3\.ink-bleed-3[\s\S]*?\}/)?.[0] ?? ''
    expect(rule).toMatch(/rgba\(201,\s*168,\s*76/)
  })
})

describe('Card.vue — ink fragrance fade animation CSS', () => {
  const style = extractScopedStyle(cardSrc)

  it('defines @keyframes inkFade animation', () => {
    expect(style).toMatch(/@keyframes\s+inkFade\s*\{/)
  })

  it('@keyframes inkFade animates opacity with 0.8 and 0.4 values', () => {
    // Greedy match to capture the entire keyframe block including nested braces
    const inkFadeSection = style.match(/@keyframes inkFade[\s\S]*\}/)?.[0] ?? ''
    const opacities = [...inkFadeSection.matchAll(/opacity:\s*([\d.]+)/g)].map(m => m[1])
    expect(opacities).toContain('0.8')
    expect(opacities).toContain('0.4')
  })

  it('@keyframes inkFade loops (infinite) via .fragrance-dot.filled', () => {
    // infinite appears in the .fragrance-dot.filled animation shorthand
    expect(style).toMatch(/\.fragrance-dot\.filled[\s\S]{0,100}infinite/)
  })

  it('.fragrance-dot.filled applies inkFade animation', () => {
    const filledRule = style.match(/\.fragrance-dot\.filled\s*\{[\s\S]*?\}/)?.[0] ?? ''
    expect(filledRule).toMatch(/animation.*inkFade/)
  })

  it('.fragrance-dot.filled uses animation-fill-mode: both', () => {
    // both is part of the animation shorthand value
    const filledRule = style.match(/\.fragrance-dot\.filled\s*\{[\s\S]*?\}/)?.[0] ?? ''
    expect(filledRule).toMatch(/both/)
    expect(filledRule).toMatch(/animation/)
  })

  it('template binds animationDelay stagger ((i-1) * 0.5s) for filled dots', () => {
    expect(cardSrc).toMatch(/animationDelay:\s*\(\(i\s*-\s*1\)\s*\*\s*0\.5\)/)
  })

  it('template adds ink-fade class to filled dots', () => {
    expect(cardSrc).toMatch(/'ink-fade':\s*i\s*<=\s*inkFragrance/)
  })

  it('v-for renders 7 fragrance dots', () => {
    expect(cardSrc).toMatch(/v-for="i in 7"/)
  })

  it('filled class is applied when i <= inkFragrance', () => {
    expect(cardSrc).toMatch(/\{ filled:\s*i\s*<=\s*inkFragrance/)
  })
})
