/**
 * Card.vue 单元测试 — ink fragrance fade animation
 *
 * 测试覆盖：
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

// ─── Helpers ─────────────────────────────────────────────────────────────────

function extractScopedStyle(src: string): string {
  const match = src.match(/<style scoped>([\s\S]*?)<\/style>/)
  return match ? match[1] : ''
}

// ─── Tests ─────────────────────────────────────────────────────────────────────

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
