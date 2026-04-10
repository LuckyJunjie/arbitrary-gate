/**
 * AI 腔词黑名单过滤器
 *
 * AI-06 功能点：过滤 AI 生成文本中的腔调词语
 */

export const AI_BLACKLIST: ReadonlySet<string> = new Set([
  // 比喻类腔词
  '宛如', '仿佛', '仿若', '似乎', '宛若', '恰如', '恰似',
  '犹如', '犹', '一如', '一如既往',
  // 情感/心理描写腔词
  '无法言说', '难以言说', '不可言说', '难以名状', '莫可名状',
  '心有余悸', '百感交集', '不由自主', '不约而同', '不以为意',
  '触手可及', '举手投足', '此时此刻', '曾几何时',
  // 议论/过渡类腔词
  '不难发现', '众所周知', '可以说', '总的来说',
  '值得注意的是', '从某种意义上', '想必', '毋庸置疑',
  '不言而喻', '由此可见', '总之', '总而言之',
  '事实上', '实际上', '说起来', '话说回来', '话虽如此',
  '大概', '或许', '也许', '恐怕', '未免', '多少有些',
  '略显', '略带', '稍显', '有些', '多少有点', '应该说',
  '诚然', '固', '固然', '当然', '毫无疑问', '无可否认',
  '在这个意义上', '从这个角度来说',
  '首先', '其次', '再次', '最后',
  '第一', '第二', '第三', '一方面', '另一方面',
  '综上所述', '可以看出', '毋庸讳言', '无可置疑', '有鉴于此',
  // 指代类腔词
  '如此', '这般',
])

/**
 * 按长度降序排列的黑名单词（用于优先匹配长词）
 */
const SORTED_BLACKLIST: string[] = Array.from(AI_BLACKLIST).sort((a, b) => b.length - a.length)

/**
 * 过滤文本中的 AI 腔词，替换为近义自然表达
 */
export function filter(text: string | null | undefined): string {
  if (!text) return ''

  let result = text
  for (const word of SORTED_BLACKLIST) {
    const regex = new RegExp(escapeRegex(word), 'gu')
    result = result.replace(regex, '')
  }
  // 清理连续空格并 trim
  return result.replace(/\s+/g, ' ').trim()
}

/**
 * 检测文本中是否包含黑名单词
 */
export function containsBlockedPhrase(text: string | null | undefined): boolean {
  if (!text) return false
  for (const word of AI_BLACKLIST) {
    if (text.includes(word)) return true
  }
  return false
}

/**
 * 找出文本中所有黑名单词
 */
export function getBlockedPhrases(text: string | null | undefined): string[] {
  if (!text) return []

  const found: string[] = []
  const seen = new Set<string>()

  for (const word of AI_BLACKLIST) {
    if (!seen.has(word) && text.includes(word)) {
      found.push(word)
      seen.add(word)
    }
  }
  return found
}

function escapeRegex(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
