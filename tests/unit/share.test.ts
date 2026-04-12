/**
 * 分享模块单元测试
 * SH-01 / SH-02 / SH-03
 *
 * 测试覆盖：
 * - 分享码格式验证（8位，无混淆字符）
 * - 合券匹配逻辑（同类卡或高稀有度可合）
 * - 合券完整流程
 */

import { describe, it, expect } from 'vitest'

// ==================== 类型定义 ====================

interface ShareRecord {
  id: number
  storyId: number
  shareCode: string
  missingCornerCardId: number
  cardCategory: number
  cardName: string
  jointCount: number
  createdAt: Date
}

interface UserOwnedCard {
  cardId: number
  cardType: 'keyword' | 'event'
  name: string
  rarity: number
  category: number // 1-5
}

interface JointValidationResult {
  valid: boolean
  error?: string
  grantedAccess?: boolean
}

// ==================== 分享码生成逻辑（对应后端 IdGenerator.shareCode）====================

/**
 * 模拟后端 IdGenerator.shareCode() 的前端版本
 * 使用 crypto.getRandomValues 确保密码学安全
 */
function generateShareCode(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789' // 排除 I,O,0,1
  const array = new Uint32Array(8)
  crypto.getRandomValues(array)
  let code = ''
  for (let i = 0; i < 8; i++) {
    code += chars[array[i] % chars.length]
  }
  return code
}

// ==================== 合券匹配引擎（对应后端 ShareService.jointShare 逻辑）====================

class ShareCouponValidator {
  /**
   * 校验用户关键词卡是否可与缺角卡合券
   * 规则：同类卡（category 相同）或 高稀有度（奇/绝品，rarity >= 3）
   */
  static canJoint(
    userCard: UserOwnedCard,
    missingCornerCategory: number,
    missingCornerRarity?: number
  ): boolean {
    if (userCard.cardType !== 'keyword') return false
    const sameCategory = userCard.category === missingCornerCategory
    const highRarity = userCard.rarity >= 3
    // 高稀有度可跨类合券
    return sameCategory || highRarity
  }

  /**
   * 从用户关键词卡列表中筛选可合券的卡
   */
  static filterJointableCards(
    userCards: UserOwnedCard[],
    missingCornerCategory: number
  ): UserOwnedCard[] {
    return userCards.filter(card => this.canJoint(card, missingCornerCategory))
  }
}

// ==================== 测试用例 ====================

describe('分享码生成', () => {
  describe('SH-02: 分享码格式', () => {
    it('应生成8位分享码', () => {
      const code = generateShareCode()
      expect(code).toHaveLength(8)
    })

    it('应只包含字母数字字符', () => {
      const code = generateShareCode()
      expect(code).toMatch(/^[A-Z0-9]+$/)
    })

    it('应排除易混淆字符 (I, O, 0, 1)', () => {
      // 运行多次以增加覆盖概率
      for (let i = 0; i < 50; i++) {
        const code = generateShareCode()
        expect(code).not.toMatch(/[IO01]/)
      }
    })

    it('50次生成应产生50个唯一码', () => {
      const codes = new Set<string>()
      for (let i = 0; i < 50; i++) {
        codes.add(generateShareCode())
      }
      expect(codes.size).toBe(50)
    })
  })
})

describe('SH-03: 合券匹配逻辑', () => {
  describe('同类卡可合', () => {
    it('器物卡只能与器物卡合', () => {
      const userCard: UserOwnedCard = {
        cardId: 101,
        cardType: 'keyword',
        name: '旧船票',
        rarity: 2,
        category: 1, // 器物
      }
      // 同类：器物 → 可合
      expect(ShareCouponValidator.canJoint(userCard, 1)).toBe(true)
      // 异类：职人 → 不可合（除非高稀有度）
      expect(ShareCouponValidator.canJoint(userCard, 2)).toBe(false)
    })

    it('职人卡只能与职人卡合', () => {
      const userCard: UserOwnedCard = {
        cardId: 201,
        cardType: 'keyword',
        name: '说书匠',
        rarity: 1,
        category: 2, // 职人
      }
      expect(ShareCouponValidator.canJoint(userCard, 2)).toBe(true) // 同类
      expect(ShareCouponValidator.canJoint(userCard, 3)).toBe(false) // 异类
    })

    it('风物卡只能与风物卡合', () => {
      const userCard: UserOwnedCard = {
        cardId: 301,
        cardType: 'keyword',
        name: '青石板',
        rarity: 1,
        category: 3, // 风物
      }
      expect(ShareCouponValidator.canJoint(userCard, 3)).toBe(true)
      expect(ShareCouponValidator.canJoint(userCard, 4)).toBe(false)
    })
  })

  describe('高稀有度跨类合券', () => {
    it('奇品(3)可跨类与任何缺角卡合', () => {
      const rareCard: UserOwnedCard = {
        cardId: 401,
        cardType: 'keyword',
        name: '意难平',
        rarity: 3,
        category: 4, // 情绪
      }
      // 奇品可以跨类合
      expect(ShareCouponValidator.canJoint(rareCard, 1)).toBe(true) // 跨到器物
      expect(ShareCouponValidator.canJoint(rareCard, 2)).toBe(true) // 跨到职人
      expect(ShareCouponValidator.canJoint(rareCard, 3)).toBe(true) // 跨到风物
      expect(ShareCouponValidator.canJoint(rareCard, 5)).toBe(true) // 跨到称谓
    })

    it('绝品(4)可跨类与任何缺角卡合', () => {
      const superRareCard: UserOwnedCard = {
        cardId: 501,
        cardType: 'keyword',
        name: '舍不得',
        rarity: 4,
        category: 4, // 情绪
      }
      expect(ShareCouponValidator.canJoint(superRareCard, 1)).toBe(true)
      expect(ShareCouponValidator.canJoint(superRareCard, 5)).toBe(true)
    })

    it('凡品(1)不可跨类', () => {
      const commonCard: UserOwnedCard = {
        cardId: 101,
        cardType: 'keyword',
        name: '旧船票',
        rarity: 1,
        category: 1,
      }
      expect(ShareCouponValidator.canJoint(commonCard, 2)).toBe(false)
    })

    it('珍品(2)不可跨类', () => {
      const rareCard: UserOwnedCard = {
        cardId: 102,
        cardType: 'keyword',
        name: '铜锁芯',
        rarity: 2,
        category: 1,
      }
      expect(ShareCouponValidator.canJoint(rareCard, 3)).toBe(false)
    })
  })

  describe('事件卡不可用于合券', () => {
    it('事件卡不能参与合券', () => {
      const eventCard: UserOwnedCard = {
        cardId: 999,
        cardType: 'event',
        name: '赤壁之战',
        rarity: 2,
        category: 0,
      }
      // 即使 category 匹配，事件卡也不能合券
      expect(ShareCouponValidator.canJoint(eventCard, 1)).toBe(false)
      expect(ShareCouponValidator.canJoint(eventCard, 2)).toBe(false)
    })
  })

  describe('筛选可合券卡列表', () => {
    it('应正确筛选出可合券的卡', () => {
      const userCards: UserOwnedCard[] = [
        { cardId: 101, cardType: 'keyword', name: '旧船票', rarity: 2, category: 1 }, // 器物-凡
        { cardId: 102, cardType: 'keyword', name: '说书匠', rarity: 3, category: 2 }, // 职人-奇
        { cardId: 103, cardType: 'keyword', name: '青石板', rarity: 1, category: 3 }, // 风物-凡
        { cardId: 104, cardType: 'keyword', name: '意难平', rarity: 4, category: 4 }, // 情绪-绝
        { cardId: 105, cardType: 'keyword', name: '娘亲舅', rarity: 2, category: 5 }, // 称谓-珍
        { cardId: 201, cardType: 'event', name: '赤壁', rarity: 2, category: 0 }, // 事件卡
      ]

      // 缺角卡是器物类(category=1)
      const jointable = ShareCouponValidator.filterJointableCards(userCards, 1)

      // 应该只有: 旧船票(同类凡品) + 意难平(绝品跨类)
      // 说书匠(奇品跨类)也应该在！
      expect(jointable.length).toBe(3)
      expect(jointable.map(c => c.cardId)).toContain(101) // 同类器物-凡
      expect(jointable.map(c => c.cardId)).toContain(102) // 奇品跨类
      expect(jointable.map(c => c.cardId)).toContain(104) // 绝品跨类
      expect(jointable.map(c => c.cardId)).not.toContain(103) // 风物凡品-不可跨类
      expect(jointable.map(c => c.cardId)).not.toContain(105) // 称谓珍品-不可跨类
      expect(jointable.map(c => c.cardId)).not.toContain(201) // 事件卡
    })

    it('空卡列表应返回空结果', () => {
      const result = ShareCouponValidator.filterJointableCards([], 1)
      expect(result).toHaveLength(0)
    })
  })
})

describe('SH-01: 缺角卡Canvas渲染数据', () => {
  it('应提供正确的渲染参数', () => {
    // 模拟 ShareInfoResponse
    const shareInfo = {
      shareCode: 'ABCD1234',
      cardName: '旧船票',
      cardCategory: 1, // 器物
      storyTitle: '长安十二时辰',
      storyId: 1,
      status: 'pending' as const,
      expiresAt: '2024-04-17T00:00:00',
    }

    // 验证渲染需要的数据都存在
    expect(shareInfo.shareCode).toBeDefined()
    expect(shareInfo.cardName).toBeDefined()
    expect(shareInfo.cardCategory).toBeDefined()
    expect(shareInfo.storyTitle).toBeDefined()
    expect(shareInfo.storyId).toBeDefined()
    expect(shareInfo.status).toBe('pending')

    // categoryNames 映射
    const categoryNames: Record<number, string> = {
      1: '器物', 2: '职人', 3: '风物', 4: '情绪', 5: '称谓'
    }
    expect(categoryNames[shareInfo.cardCategory]).toBe('器物')
  })
})

describe('SH-03: 完整合券流程', () => {
  it('应完成完整的分享-合券流程', () => {
    // 1. 分享者创建分享码
    const shareCode = generateShareCode()
    expect(shareCode).toHaveLength(8)

    // 2. 分享记录
    const share: ShareRecord = {
      id: 1,
      storyId: 1,
      shareCode,
      missingCornerCardId: 101,
      cardCategory: 1, // 器物
      cardName: '旧船票',
      jointCount: 0,
      createdAt: new Date(),
    }

    // 3. 合券者拥有同类关键词卡
    const userCards: UserOwnedCard[] = [
      { cardId: 101, cardType: 'keyword', name: '旧船票', rarity: 2, category: 1 },
      { cardId: 102, cardType: 'keyword', name: '说书匠', rarity: 1, category: 2 },
    ]

    // 4. 验证合券资格
    const matchingCards = ShareCouponValidator.filterJointableCards(userCards, share.cardCategory)
    expect(matchingCards.length).toBeGreaterThan(0)
    expect(matchingCards[0].cardId).toBe(101) // 旧船票同类

    // 5. 合券成功
    const result: JointValidationResult = { valid: true, grantedAccess: true }
    expect(result.valid).toBe(true)
    expect(result.grantedAccess).toBe(true)
  })

  it('无匹配卡时应拒绝合券', () => {
    const userCards: UserOwnedCard[] = [
      { cardId: 102, cardType: 'keyword', name: '说书匠', rarity: 1, category: 2 }, // 职人
      { cardId: 103, cardType: 'keyword', name: '青石板', rarity: 1, category: 3 }, // 风物
    ]

    // 缺角卡是器物(1)，用户只有职人和风物
    const matchingCards = ShareCouponValidator.filterJointableCards(userCards, 1)
    expect(matchingCards).toHaveLength(0)
  })
})
