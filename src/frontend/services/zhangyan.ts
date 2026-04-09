/**
 * 掌眼 Agent Service
 *
 * 职责：剔除 AI 腔词汇，提高文学质感
 *
 * 集成方式：
 * - 主要逻辑在 Java 后端 ZhangyanAgent，故事结束生成后自动过滤
 * - 前端可调用 filterLocal() 进行本地预过滤（可选）
 *
 * 黑名单词表（与后端保持同步）：
 * 宛如、仿佛、无法言说、不禁、缓缓说道、轻声说道、
 * 目光中满是、心中一动、似乎在诉说
 */

import { finishStory, type Manuscript } from './api'

/** 黑名单词表 */
export const BLACKLIST = [
  '宛如',
  '仿佛',
  '无法言说',
  '不禁',
  '缓缓说道',
  '轻声说道',
  '目光中满是',
  '心中一动',
  '似乎在诉说',
] as const

type BlacklistWord = typeof BLACKLIST[number]

/** 替换映射表 */
const REPLACEMENTS: Record<BlacklistWord, string> = {
  '宛如': '像',
  '仿佛': '好像',
  '无法言说': '说不清',
  '不禁': '',
  '缓缓说道': '说',
  '轻声说道': '低声道',
  '目光中满是': '眼里尽是',
  '心中一动': '心头一紧',
  '似乎在诉说': '像是在说',
}

/**
 * 本地预过滤 — 在调用 finishStory API 前对 manuscript 文本进行预处理
 * 用于前端展示优化，不影响实际存储
 *
 * @param text 原始文本
 * @returns 过滤后文本
 */
export function filterLocal(text: string): string {
  if (!text) return text

  let result = text

  for (const [word, replacement] of Object.entries(REPLACEMENTS)) {
    // 匹配中文词汇（前后有汉字边界，或在标点/空格旁）
    const pattern = new RegExp(
      `(?<=[\\u4e00-\\u9fff])${word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?=[\\u4e00-\\u9fff\\s。，、；：！？])`,
      'g'
    )
    result = result.replace(pattern, replacement)
  }

  // 清理连续空格
  result = result.replace(/\s{2,}/g, ' ')
  // 清理句首残留标点
  result = result.replace(/^[，。、；：！？,\s]+/, '')

  return result
}

/**
 * 查找文本中包含的黑名单词（用于调试/分析）
 *
 * @param text 待检查文本
 * @returns 被捕获的黑名单词列表
 */
export function findBlacklistWords(text: string): BlacklistWord[] {
  if (!text) return []
  return BLACKLIST.filter(word => text.includes(word)) as BlacklistWord[]
}

/**
 * 集成点：在 finishStory 返回后，对 manuscript 字段进行本地过滤
 *
 * 用法示例：
 * ```ts
 * const result = await finishStory(storyId)
 * result.manuscript = filterLocal(result.manuscript)
 * ```
 *
 * @param manuscript 原始手稿文本
 * @returns 过滤后手稿文本
 */
export function filterManuscript(manuscript: string): string {
  return filterLocal(manuscript)
}

/**
 * 带报告的过滤 — 返回被替换的词列表
 *
 * @param text 原始文本
 * @returns 包含过滤结果和替换词列表
 */
export function filterWithReport(text: string): {
  filtered: string
  replacedWords: BlacklistWord[]
} {
  const replacedWords = findBlacklistWords(text)
  const filtered = filterLocal(text)
  return { filtered, replacedWords }
}
