/**
 * Mock Judge Service
 * 模拟判官 AI 计算偏离度、生成批注、评判故事走向
 */

export interface ChoiceResult {
  deviationDelta: number
  judgment: string
  resonanceBonus: Record<number, number>
}

export interface StoryJudgment {
  totalDeviation: number
  deviationLevel: 'minimal' | 'moderate' | 'significant' | 'transgressive'
  overallJudgment: string
  judgeQuote: string
  hiddenPattern?: string
}

/**
 * 判官批注风格
 */
const JUDGE_COMMENTS = [
  '此选择尚在历史正轨之内。',
  '偏离不大，然小节处已见分晓。',
  '人心各异，选择自有其道理。',
  '此道之行，已非正史所能载。',
  '偏离之甚，已入稗官野史之列。',
]

class JudgeService {
  private deviation = 0

  /**
   * 重置判官状态（新故事开始时调用）
   */
  reset(): void {
    this.deviation = 0
  }

  /**
   * 计算当前偏离度
   */
  getDeviation(): number {
    return this.deviation
  }

  /**
   * 记录选择，更新偏离度
   */
  recordChoice(optionId: number, chapterNo: number): ChoiceResult {
    // 模拟不同选择的偏离度
    // 选项 1=义(低偏差)，2=利(中偏差)，3=情(高偏差)
    let deviationDelta = 0
    let judgment = ''
    const resonanceBonus: Record<number, number> = { 1: 0, 2: 0, 3: 0 }

    if (chapterNo === 1) {
      if (optionId === 1) { deviationDelta = 3; judgment = '火攻之策，合乎兵法。'; resonanceBonus[1] = 2 }
      else if (optionId === 2) { deviationDelta = 1; judgment = '持重之道，未足言勇。'; resonanceBonus[2] = 2 }
      else { deviationDelta = 5; judgment = '求和之心，未免示弱于敌。'; resonanceBonus[3] = 2 }
    } else if (chapterNo === 2) {
      if (optionId === 1) { deviationDelta = 4; judgment = '追击过急，恐中伏兵。'; resonanceBonus[1] = 2 }
      else { deviationDelta = 2; judgment = '见好就收，实为稳妥。'; resonanceBonus[2] = 2 }
    } else {
      if (optionId === 1) { deviationDelta = 3; judgment = '取蜀之念，壮志可嘉。'; resonanceBonus[1] = 2 }
      else { deviationDelta = 2; judgment = '休养生息，务实之举。'; resonanceBonus[3] = 2 }
    }

    this.deviation += deviationDelta
    return { deviationDelta, judgment, resonanceBonus }
  }

  /**
   * 生成故事终了时的判词
   */
  getFinalJudgment(totalDeviation: number): StoryJudgment {
    const level: StoryJudgment['deviationLevel'] =
      totalDeviation <= 3 ? 'minimal' :
      totalDeviation <= 6 ? 'moderate' :
      totalDeviation <= 10 ? 'significant' : 'transgressive'

    const overallJudgment = JUDGE_COMMENTS[
      Math.min(Math.floor(totalDeviation / 3), JUDGE_COMMENTS.length - 1)
    ]

    const judgeQuote =
      level === 'minimal' ? '此卷所载，与正史相差无几。' :
      level === 'moderate' ? '此故事略有出入，然不离其宗。' :
      level === 'significant' ? '此卷所言，多为稗官之说，不可全信。' :
      '此卷所述，已入野史之列，与正史大异。'

    return {
      totalDeviation,
      deviationLevel: level,
      overallJudgment,
      judgeQuote,
    }
  }
}

export const judge = new JudgeService()
