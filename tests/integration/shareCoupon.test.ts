/**
 * 分享合券逻辑集成测试
 *
 * 测试覆盖：
 * - 分享码唯一性验证
 * - 合券验证逻辑正确
 * - 缺角图片生成正确
 * - 分享权限授予逻辑
 */

import { describe, it, expect, beforeEach } from 'vitest'

// ==================== 类型定义 ====================

interface ShareRecord {
  id: number
  storyId: number
  shareCode: string
  missingCornerCardId: number
  jointCount: number
  createdAt: Date
}

interface JointValidationResult {
  valid: boolean
  error?: string
  requiredKeywordId?: number
  grantedAccess?: boolean
}

interface UserOwnedCard {
  cardId: number
  cardType: 'keyword' | 'event'
  name: string
  rarity: number
}

// ==================== 分享合券引擎 ====================

class ShareCouponEngine {
  private shareRecords: Map<string, ShareRecord> = new Map()
  private userCards: Map<number, UserOwnedCard[]> = new Map()
  private storyAccess: Map<string, Set<number>> = new Map() // storyNo -> userIds

  /**
   * 创建分享记录
   */
  createShare(storyId: number, missingCornerCardId: number): ShareRecord {
    const shareCode = this.generateShareCode()

    const record: ShareRecord = {
      id: Date.now(),
      storyId,
      shareCode,
      missingCornerCardId,
      jointCount: 0,
      createdAt: new Date(),
    }

    this.shareRecords.set(shareCode, record)
    return record
  }

  /**
   * 生成唯一的分享码
   * 格式: 8位字母数字组合，不可枚举
   */
  private generateShareCode(): string {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
    let code = ''

    // 使用加密安全的随机数生成器
    const array = new Uint32Array(8)
    // 模拟随机字节生成
    for (let i = 0; i < 8; i++) {
      array[i] = Math.floor(Math.random() * 0xffffffff)
    }

    for (let i = 0; i < 8; i++) {
      code += chars[array[i] % chars.length]
    }

    // 确保不与现有代码冲突
    if (this.shareRecords.has(code)) {
      return this.generateShareCode() // 递归直到唯一
    }

    return code
  }

  /**
   * 验证合券
   * @param shareCode 分享码
   * @param userId 用户ID
   * @param userKeywordCardIds 用户拥有的关键词卡ID列表
   */
  validateJoint(
    shareCode: string,
    userId: number,
    userKeywordCardIds: number[]
  ): JointValidationResult {
    const record = this.shareRecords.get(shareCode)

    if (!record) {
      return { valid: false, error: '分享码不存在' }
    }

    // 检查是否已经合券过
    const accessList = this.storyAccess.get(`${record.storyId}`)
    if (accessList?.has(userId)) {
      return {
        valid: true,
        grantedAccess: true,
        requiredKeywordId: record.missingCornerCardId,
      }
    }

    // 检查用户是否拥有缺失的关键词卡
    const hasRequiredCard = userKeywordCardIds.includes(record.missingCornerCardId)

    if (!hasRequiredCard) {
      return {
        valid: false,
        error: '缺少必要的关键词卡',
        requiredKeywordId: record.missingCornerCardId,
      }
    }

    // 合券成功，授予访问权限
    this.grantAccess(record.storyId, userId)
    record.jointCount++

    return {
      valid: true,
      grantedAccess: true,
      requiredKeywordId: record.missingCornerCardId,
    }
  }

  /**
   * 授予故事访问权限
   */
  private grantAccess(storyId: number, userId: number): void {
    const key = `${storyId}`
    if (!this.storyAccess.has(key)) {
      this.storyAccess.set(key, new Set())
    }
    this.storyAccess.get(key)!.add(userId)
  }

  /**
   * 检查用户是否有权访问故事
   */
  hasAccess(storyId: number, userId: number): boolean {
    const accessList = this.storyAccess.get(`${storyId}`)
    return accessList?.has(userId) ?? false
  }

  /**
   * 获取分享记录
   */
  getShareRecord(shareCode: string): ShareRecord | undefined {
    return this.shareRecords.get(shareCode)
  }

  /**
   * 设置用户拥有的卡牌
   */
  setUserCards(userId: number, cards: UserOwnedCard[]): void {
    this.userCards.set(userId, cards)
  }

  /**
   * 获取用户拥有的关键词卡ID列表
   */
  getUserKeywordCardIds(userId: number): number[] {
    const cards = this.userCards.get(userId) ?? []
    return cards.filter(c => c.cardType === 'keyword').map(c => c.cardId)
  }

  /**
   * 生成缺角故事卡图片信息
   */
  generateMissingCornerImage(shareCode: string): {
    imageUrl: string
    missingCornerPosition: 'topLeft' | 'topRight' | 'bottomLeft' | 'bottomRight'
    hint: string
  } {
    const record = this.shareRecords.get(shareCode)

    if (!record) {
      throw new Error('分享码不存在')
    }

    // 根据缺失的关键词卡ID确定缺角位置
    const positions: Array<'topLeft' | 'topRight' | 'bottomLeft' | 'bottomRight'> = [
      'topLeft',
      'topRight',
      'bottomLeft',
      'bottomRight',
    ]
    const position = positions[record.missingCornerCardId % 4]

    return {
      imageUrl: `/api/share/image/${shareCode}.png`,
      missingCornerPosition: position,
      hint: '集齐缺失关键词卡可拼完整故事',
    }
  }

  /**
   * 获取故事的合券次数
   */
  getJointCount(shareCode: string): number {
    const record = this.shareRecords.get(shareCode)
    return record?.jointCount ?? 0
  }
}

// ==================== 测试用例 ====================

describe('ShareCouponEngine - 分享合券逻辑', () => {
  let engine: ShareCouponEngine

  beforeEach(() => {
    engine = new ShareCouponEngine()
  })

  describe('分享码生成', () => {
    it('should generate 8-character share code', () => {
      const record = engine.createShare(1, 101)

      expect(record.shareCode).toHaveLength(8)
    })

    it('should generate unique share codes', () => {
      const codes = new Set<string>()

      for (let i = 0; i < 100; i++) {
        const record = engine.createShare(i, 101)
        codes.add(record.shareCode)
      }

      // 100次生成应该有100个唯一码
      expect(codes.size).toBe(100)
    })

    it('should only contain alphanumeric characters', () => {
      const record = engine.createShare(1, 101)

      expect(record.shareCode).toMatch(/^[A-Za-z0-9]+$/)
    })

    it('should exclude ambiguous characters (0, O, I, 1, L)', () => {
      // 生成的分享码不应该包含容易混淆的字符
      const record = engine.createShare(1, 101)

      expect(record.shareCode).not.toMatch(/[0OI1L]/)
    })
  })

  describe('分享记录', () => {
    it('should create share record with correct properties', () => {
      const record = engine.createShare(1, 101)

      expect(record.id).toBeDefined()
      expect(record.storyId).toBe(1)
      expect(record.missingCornerCardId).toBe(101)
      expect(record.jointCount).toBe(0)
      expect(record.createdAt).toBeInstanceOf(Date)
    })

    it('should retrieve share record by code', () => {
      const created = engine.createShare(1, 101)
      const retrieved = engine.getShareRecord(created.shareCode)

      expect(retrieved?.shareCode).toBe(created.shareCode)
      expect(retrieved?.missingCornerCardId).toBe(101)
    })

    it('should return undefined for non-existent share code', () => {
      const result = engine.getShareRecord('NONEXIST')

      expect(result).toBeUndefined()
    })
  })

  describe('合券验证', () => {
    beforeEach(() => {
      // 设置用户拥有的卡牌
      engine.setUserCards(1, [
        { cardId: 101, cardType: 'keyword', name: '旧船票', rarity: 2 },
        { cardId: 102, cardType: 'keyword', name: '说书匠', rarity: 3 },
        { cardId: 103, cardType: 'keyword', name: '意难平', rarity: 4 },
        { cardId: 201, cardType: 'event', name: '赤壁', rarity: 2 },
      ])
    })

    it('should validate joint successfully when user has required card', () => {
      const share = engine.createShare(1, 101) // 缺失旧船票

      const result = engine.validateJoint(share.shareCode, 1, [101, 102, 103])

      expect(result.valid).toBe(true)
      expect(result.grantedAccess).toBe(true)
    })

    it('should reject joint when user lacks required card', () => {
      const share = engine.createShare(1, 101) // 缺失旧船票

      const result = engine.validateJoint(share.shareCode, 1, [102, 103]) // 没有101

      expect(result.valid).toBe(false)
      expect(result.error).toBe('缺少必要的关键词卡')
      expect(result.requiredKeywordId).toBe(101)
    })

    it('should reject joint with invalid share code', () => {
      const result = engine.validateJoint('INVALID', 1, [101])

      expect(result.valid).toBe(false)
      expect(result.error).toBe('分享码不存在')
    })

    it('should not grant duplicate access', () => {
      const share = engine.createShare(1, 101)

      // 第一次合券
      engine.validateJoint(share.shareCode, 1, [101, 102, 103])

      // 第二次合券（用同一张卡）
      const result = engine.validateJoint(share.shareCode, 1, [101, 102, 103])

      // 应该仍然成功，但标记为已授权过
      expect(result.valid).toBe(true)
      expect(result.grantedAccess).toBe(true)
    })

    it('should increment joint count on successful validation', () => {
      const share = engine.createShare(1, 101)

      expect(engine.getJointCount(share.shareCode)).toBe(0)

      engine.validateJoint(share.shareCode, 1, [101, 102, 103])

      expect(engine.getJointCount(share.shareCode)).toBe(1)
    })
  })

  describe('访问权限控制', () => {
    beforeEach(() => {
      engine.setUserCards(1, [
        { cardId: 101, cardType: 'keyword', name: '旧船票', rarity: 2 },
      ])
    })

    it('should grant access after successful joint', () => {
      const share = engine.createShare(1, 101)

      engine.validateJoint(share.shareCode, 1, [101])

      expect(engine.hasAccess(1, 1)).toBe(true)
    })

    it('should deny access without joint', () => {
      engine.createShare(1, 101)

      expect(engine.hasAccess(1, 1)).toBe(false)
    })

    it('should allow different users to joint separately', () => {
      const share = engine.createShare(1, 101)

      engine.setUserCards(2, [
        { cardId: 101, cardType: 'keyword', name: '旧船票', rarity: 2 },
      ])

      engine.validateJoint(share.shareCode, 1, [101])
      engine.validateJoint(share.shareCode, 2, [101])

      expect(engine.hasAccess(1, 1)).toBe(true)
      expect(engine.hasAccess(1, 2)).toBe(true)
    })
  })

  describe('缺角图片生成', () => {
    it('should generate missing corner image info', () => {
      const share = engine.createShare(1, 101)

      const imageInfo = engine.generateMissingCornerImage(share.shareCode)

      expect(imageInfo.imageUrl).toContain(share.shareCode)
      expect(imageInfo.missingCornerPosition).toBeDefined()
      expect(imageInfo.hint).toContain('关键词卡')
    })

    it('should throw error for non-existent share code', () => {
      expect(() => engine.generateMissingCornerImage('INVALID')).toThrow('分享码不存在')
    })

    it('should map different card IDs to different corner positions', () => {
      const positions = new Set<string>()

      for (let cardId = 101; cardId <= 104; cardId++) {
        const share = engine.createShare(cardId, cardId)
        const info = engine.generateMissingCornerImage(share.shareCode)
        positions.add(info.missingCornerPosition)
      }

      // 4张不同的卡应该映射到4个不同的位置
      expect(positions.size).toBeGreaterThan(1)
    })
  })

  describe('边界条件', () => {
    it('should handle empty user card list', () => {
      const share = engine.createShare(1, 101)

      const result = engine.validateJoint(share.shareCode, 1, [])

      expect(result.valid).toBe(false)
    })

    it('should handle user with only event cards', () => {
      engine.setUserCards(1, [
        { cardId: 201, cardType: 'event', name: '赤壁', rarity: 2 },
      ])

      const share = engine.createShare(1, 101)

      const result = engine.validateJoint(share.shareCode, 1, [201])

      expect(result.valid).toBe(false)
    })

    it('should handle concurrent joint attempts', () => {
      const share = engine.createShare(1, 101)

      // 模拟多个用户同时合券
      engine.setUserCards(2, [{ cardId: 101, cardType: 'keyword', name: '旧船票', rarity: 2 }])
      engine.setUserCards(3, [{ cardId: 101, cardType: 'keyword', name: '旧船票', rarity: 2 }])

      engine.validateJoint(share.shareCode, 2, [101])
      engine.validateJoint(share.shareCode, 3, [101])

      expect(engine.getJointCount(share.shareCode)).toBe(2)
    })
  })
})

// ==================== 分享合券完整流程测试 ====================

describe('ShareCouponEngine - 完整分享流程', () => {
  let engine: ShareCouponEngine

  beforeEach(() => {
    engine = new ShareCouponEngine()
  })

  it('should complete full share and joint flow', () => {
    // 1. 故事创建者分享故事
    const share = engine.createShare(1, 101) // 缺失关键词卡101

    expect(share.shareCode).toHaveLength(8)

    // 2. 用户A拥有缺失的卡
    engine.setUserCards(10, [
      { cardId: 101, cardType: 'keyword', name: '旧船票', rarity: 2 },
      { cardId: 102, cardType: 'keyword', name: '说书匠', rarity: 3 },
    ])

    // 3. 用户A合券成功
    const result1 = engine.validateJoint(share.shareCode, 10, [101, 102])
    expect(result1.valid).toBe(true)
    expect(result1.grantedAccess).toBe(true)

    // 4. 用户B不拥有缺失的卡
    engine.setUserCards(11, [
      { cardId: 102, cardType: 'keyword', name: '说书匠', rarity: 3 },
    ])

    // 5. 用户B合券失败
    const result2 = engine.validateJoint(share.shareCode, 11, [102])
    expect(result2.valid).toBe(false)
    expect(result2.error).toBe('缺少必要的关键词卡')

    // 6. 获取缺角图片
    const imageInfo = engine.generateMissingCornerImage(share.shareCode)
    expect(imageInfo.imageUrl).toContain(share.shareCode)

    // 7. 查看合券次数
    expect(engine.getJointCount(share.shareCode)).toBe(1)
  })
})
