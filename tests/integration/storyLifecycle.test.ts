/**
 * 故事完整生命周期集成测试
 *
 * 测试覆盖：
 * - 进行中 → 已完成 → 已放弃 的正确流转
 * - 每个状态的合法操作
 * - 故事完整流程（抽卡 → 入局 → 叙事 → 生成手稿）
 */

import { describe, it, expect, beforeEach, vi } from 'vitest'

// ==================== 类型定义 ====================

type StoryStatus = 'draft' | 'in_progress' | 'completed' | 'abandoned'

interface Story {
  id: number
  storyNo: string
  userId: number
  eventCardId: number
  keywordCardIds: number[]
  identityType: number
  status: StoryStatus
  currentChapter: number
  totalChapters: number
  historyDeviation: number
  title?: string
  createdAt: Date
  finishedAt?: Date
}

interface Chapter {
  id: number
  storyId: number
  chapterNo: number
  sceneText: string
  options: Array<{ id: number; text: string }>
  selectedOption?: number
}

interface ChapterResult {
  sceneText: string
  options: Array<{ id: number; text: string }>
  keywordResonance: Record<number, number>
}

type StoryEventHandler = {
  onStatusChange?: (story: Story, newStatus: StoryStatus) => void
  onChapterComplete?: (chapter: Chapter) => void
  onManifestation?: (keywordId: number) => void
}

// ==================== 故事状态机引擎 ====================

class StoryLifecycleEngine {
  private story: Story
  private chapters: Map<number, Chapter> = new Map()
  private handlers: StoryEventHandler = {}

  constructor(storyData: Partial<Story>) {
    this.story = {
      id: storyData.id ?? Date.now(),
      storyNo: storyData.storyNo ?? this.generateStoryNo(),
      userId: storyData.userId ?? 1,
      eventCardId: storyData.eventCardId ?? 1,
      keywordCardIds: storyData.keywordCardIds ?? [],
      identityType: storyData.identityType ?? 1,
      status: 'draft',
      currentChapter: 0,
      totalChapters: storyData.totalChapters ?? 5,
      historyDeviation: 50,
      createdAt: new Date(),
    }
  }

  private generateStoryNo(): string {
    return `SN${Date.now().toString(36).toUpperCase()}`
  }

  setHandlers(handlers: StoryEventHandler): void {
    this.handlers = handlers
  }

  // ==================== 状态转换 ====================

  /**
   * 开始故事（draft → in_progress）
   */
  startStory(): void {
    if (this.story.status !== 'draft') {
      throw new Error(`Cannot start story from status: ${this.story.status}`)
    }

    this.story.status = 'in_progress'
    this.story.currentChapter = 1

    this.handlers.onStatusChange?.(this.story, 'in_progress')
  }

  /**
   * 完成故事（in_progress → completed）
   */
  completeStory(title: string): void {
    if (this.story.status !== 'in_progress') {
      throw new Error(`Cannot complete story from status: ${this.story.status}`)
    }

    if (this.story.currentChapter < this.story.totalChapters) {
      throw new Error(`Cannot complete story before all chapters are done. Current: ${this.story.currentChapter}, Required: ${this.story.totalChapters}`)
    }

    this.story.status = 'completed'
    this.story.title = title
    this.story.finishedAt = new Date()

    this.handlers.onStatusChange?.(this.story, 'completed')
  }

  /**
   * 放弃故事（in_progress → abandoned）
   */
  abandonStory(): void {
    if (this.story.status !== 'in_progress') {
      throw new Error(`Cannot abandon story from status: ${this.story.status}`)
    }

    this.story.status = 'abandoned'
    this.story.finishedAt = new Date()

    this.handlers.onStatusChange?.(this.story, 'abandoned')
  }

  // ==================== 章节操作 ====================

  /**
   * 生成章节内容
   */
  generateChapter(chapterNo: number): ChapterResult {
    if (this.story.status !== 'in_progress') {
      throw new Error(`Cannot generate chapter when story is ${this.story.status}`)
    }

    if (chapterNo !== this.story.currentChapter) {
      throw new Error(`Invalid chapter number. Expected: ${this.story.currentChapter}, Got: ${chapterNo}`)
    }

    const chapter: Chapter = {
      id: Date.now(),
      storyId: this.story.id,
      chapterNo,
      sceneText: `第${chapterNo}章场景描写`,
      options: [
        { id: 1, text: `选项A-${chapterNo}` },
        { id: 2, text: `选项B-${chapterNo}` },
        { id: 3, text: `选项C-${chapterNo}` },
      ],
    }

    this.chapters.set(chapterNo, chapter)

    return {
      sceneText: chapter.sceneText,
      options: chapter.options,
      keywordResonance: {},
    }
  }

  /**
   * 选择章节选项
   */
  selectOption(chapterNo: number, optionId: number): void {
    if (this.story.status !== 'in_progress') {
      throw new Error(`Cannot select option when story is ${this.story.status}`)
    }

    const chapter = this.chapters.get(chapterNo)
    if (!chapter) {
      throw new Error(`Chapter ${chapterNo} does not exist`)
    }

    const option = chapter.options.find(o => o.id === optionId)
    if (!option) {
      throw new Error(`Option ${optionId} not found in chapter ${chapterNo}`)
    }

    chapter.selectedOption = optionId
    this.handlers.onChapterComplete?.(chapter)

    // 如果不是最后一章，推进到下一章
    if (chapterNo < this.story.totalChapters) {
      this.story.currentChapter = chapterNo + 1
    }
  }

  /**
   * 获取当前章节号
   */
  getCurrentChapter(): number {
    return this.story.currentChapter
  }

  /**
   * 获取故事状态
   */
  getStatus(): StoryStatus {
    return this.story.status
  }

  /**
   * 获取故事完整信息
   */
  getStory(): Story {
    return { ...this.story }
  }

  /**
   * 获取所有章节
   */
  getAllChapters(): Chapter[] {
    return Array.from(this.chapters.values())
  }
}

// ==================== 测试用例 ====================

describe('StoryLifecycleEngine - 故事完整生命周期', () => {
  let engine: StoryLifecycleEngine
  let statusChanges: Array<{ from: StoryStatus; to: StoryStatus }> = []
  let chapterCompletions: number[] = []

  beforeEach(() => {
    statusChanges = []
    chapterCompletions = []

    engine = new StoryLifecycleEngine({
      id: 1,
      userId: 1,
      eventCardId: 101,
      keywordCardIds: [1, 2, 3],
      identityType: 1,
      totalChapters: 3,
    })

    engine.setHandlers({
      onStatusChange: (_, newStatus) => {
        statusChanges.push({ from: 'draft', to: newStatus })
      },
      onChapterComplete: (chapter) => {
        chapterCompletions.push(chapter.chapterNo)
      },
    })
  })

  describe('状态机基础转换', () => {
    it('should start with draft status', () => {
      expect(engine.getStatus()).toBe('draft')
    })

    it('should transition from draft to in_progress on start', () => {
      engine.startStory()
      expect(engine.getStatus()).toBe('in_progress')
    })

    it('should transition from in_progress to completed on finish', () => {
      engine.startStory()
      engine.completeStory('测试标题')
      expect(engine.getStatus()).toBe('completed')
    })

    it('should transition from in_progress to abandoned on abandon', () => {
      engine.startStory()
      engine.abandonStory()
      expect(engine.getStatus()).toBe('abandoned')
    })

    it('should not allow starting an already started story', () => {
      engine.startStory()
      expect(() => engine.startStory()).toThrow('Cannot start story from status')
    })

    it('should not allow completing a draft story', () => {
      expect(() => engine.completeStory('标题')).toThrow('Cannot complete story from status')
    })

    it('should not allow abandoning a draft story', () => {
      expect(() => engine.abandonStory()).toThrow('Cannot abandon story from status')
    })

    it('should not allow completing an already completed story', () => {
      engine.startStory()
      engine.completeStory('标题')
      expect(() => engine.completeStory('标题2')).toThrow('Cannot complete story from status')
    })
  })

  describe('章节流转', () => {
    it('should start at chapter 1 when story starts', () => {
      engine.startStory()
      expect(engine.getCurrentChapter()).toBe(1)
    })

    it('should generate chapter 1 when started', () => {
      engine.startStory()
      const result = engine.generateChapter(1)

      expect(result.sceneText).toContain('第1章')
      expect(result.options.length).toBe(3)
    })

    it('should advance to next chapter after selection', () => {
      engine.startStory()
      engine.generateChapter(1)

      engine.selectOption(1, 1)
      expect(engine.getCurrentChapter()).toBe(2)
    })

    it('should not generate chapter before story starts', () => {
      expect(() => engine.generateChapter(1)).toThrow('Cannot generate chapter when story is draft')
    })

    it('should not generate chapter out of order', () => {
      engine.startStory()
      expect(() => engine.generateChapter(2)).toThrow('Invalid chapter number')
    })

    it('should complete all chapters and then complete story', () => {
      engine.startStory()

      // Chapter 1
      engine.generateChapter(1)
      engine.selectOption(1, 1)

      // Chapter 2
      engine.generateChapter(2)
      engine.selectOption(2, 2)

      // Chapter 3
      engine.generateChapter(3)
      engine.selectOption(3, 3)

      expect(engine.getCurrentChapter()).toBe(3)
      expect(() => engine.completeStory('完整故事')).not.toThrow()
      expect(engine.getStatus()).toBe('completed')
    })

    it('should not complete story before all chapters done', () => {
      engine.startStory()
      engine.generateChapter(1)
      engine.selectOption(1, 1)
      engine.generateChapter(2)
      engine.selectOption(2, 2)

      expect(() => engine.completeStory('不完整故事')).toThrow('Cannot complete story before all chapters are done')
    })
  })

  describe('事件处理', () => {
    it('should trigger status change handler', () => {
      engine.startStory()

      expect(statusChanges.length).toBeGreaterThan(0)
      expect(statusChanges[statusChanges.length - 1].to).toBe('in_progress')
    })

    it('should trigger chapter complete handler', () => {
      engine.startStory()
      engine.generateChapter(1)
      engine.selectOption(1, 1)

      expect(chapterCompletions).toContain(1)
    })

    it('should record all chapter completions', () => {
      engine.startStory()

      engine.generateChapter(1)
      engine.selectOption(1, 1)

      engine.generateChapter(2)
      engine.selectOption(2, 1)

      engine.generateChapter(3)
      engine.selectOption(3, 1)

      expect(chapterCompletions).toEqual([1, 2, 3])
    })
  })

  describe('无效操作检测', () => {
    it('should reject invalid option id', () => {
      engine.startStory()
      engine.generateChapter(1)

      expect(() => engine.selectOption(1, 999)).toThrow('Option 999 not found')
    })

    it('should reject chapter generation after completion', () => {
      engine.startStory()
      engine.generateChapter(1)
      engine.selectOption(1, 1)
      engine.generateChapter(2)
      engine.selectOption(2, 1)
      engine.generateChapter(3)
      engine.selectOption(3, 1)
      engine.completeStory('标题')

      expect(() => engine.generateChapter(1)).toThrow('Cannot generate chapter when story is completed')
    })

    it('should reject option selection after abandonment', () => {
      engine.startStory()
      engine.generateChapter(1)
      engine.abandonStory()

      expect(() => engine.selectOption(1, 1)).toThrow('Cannot select option when story is abandoned')
    })
  })

  describe('故事信息完整性', () => {
    it('should track storyNo correctly', () => {
      const story = engine.getStory()
      expect(story.storyNo).toMatch(/^SN[A-Z0-9]+$/)
    })

    it('should record finishedAt when completed', () => {
      engine.startStory()
      engine.generateChapter(1)
      engine.selectOption(1, 1)
      engine.generateChapter(2)
      engine.selectOption(2, 1)
      engine.generateChapter(3)
      engine.selectOption(3, 1)
      engine.completeStory('标题')

      const story = engine.getStory()
      expect(story.finishedAt).toBeDefined()
    })

    it('should record finishedAt when abandoned', () => {
      engine.startStory()
      engine.abandonStory()

      const story = engine.getStory()
      expect(story.finishedAt).toBeDefined()
    })

    it('should store title when completed', () => {
      engine.startStory()
      engine.generateChapter(1)
      engine.selectOption(1, 1)
      engine.generateChapter(2)
      engine.selectOption(2, 1)
      engine.generateChapter(3)
      engine.selectOption(3, 1)
      engine.completeStory('赤壁往事')

      const story = engine.getStory()
      expect(story.title).toBe('赤壁往事')
    })
  })
})

// ==================== 完整流程测试 ====================

describe('StoryLifecycleEngine - 完整故事流程', () => {
  it('should complete full story lifecycle: draft → in_progress → completed', () => {
    const engine = new StoryLifecycleEngine({
      totalChapters: 2,
    })

    engine.startStory()
    expect(engine.getStatus()).toBe('in_progress')

    // Chapter 1
    const ch1 = engine.generateChapter(1)
    expect(ch1.options.length).toBe(3)
    engine.selectOption(1, ch1.options[0].id)

    // Chapter 2
    const ch2 = engine.generateChapter(2)
    engine.selectOption(2, ch2.options[0].id)

    engine.completeStory('完整的故事')

    const story = engine.getStory()
    expect(story.status).toBe('completed')
    expect(story.title).toBe('完整的故事')
    expect(story.finishedAt).toBeDefined()
  })

  it('should handle abandoned story lifecycle', () => {
    const engine = new StoryLifecycleEngine({
      totalChapters: 3,
    })

    engine.startStory()

    // 只完成第一章就放弃
    engine.generateChapter(1)
    engine.selectOption(1, 1)
    engine.abandonStory()

    const story = engine.getStory()
    expect(story.status).toBe('abandoned')
    expect(story.currentChapter).toBe(2) // 放弃时停在第2章
  })

  it('should track all chapters after completion', () => {
    const engine = new StoryLifecycleEngine({
      totalChapters: 3,
    })

    engine.startStory()

    for (let i = 1; i <= 3; i++) {
      engine.generateChapter(i)
      engine.selectOption(i, 1)
    }

    engine.completeStory('标题')

    const chapters = engine.getAllChapters()
    expect(chapters.length).toBe(3)
    expect(chapters.map(c => c.chapterNo)).toEqual([1, 2, 3])
  })
})
