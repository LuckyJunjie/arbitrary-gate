/**
 * Mock StoryTeller Service
 * 模拟说书人 AI 生成故事章节和小说话稿
 * 风格：白描、无AI腔（禁用"宛如""仿佛""无法言说""似乎"）
 */

import type { Chapter, Manuscript } from './api'

interface StoryContext {
  storyId: string
  chapterCount: number
  keywords: string[]
  eventName: string
  entryAnswers: { 义?: string; 利?: string; 情?: string }
  chapterDeviations: number[]
}

interface StoryChapterData {
  sceneText: string
  options: Array<{ id: number; text: string }>
  keywordResonance: Record<number, number>
}

// ─── 故事内容库（基于赤壁·东风骤起）─────────────────────────────────────────

const CHAPTERS: Record<number, StoryChapterData> = {
  1: {
    sceneText: `江风扑面，带着水草与焦木的气息。

你站在赤壁对岸的山崖上，眼前是连绵的曹营。火光已经熄灭了大半，只剩几处残烟袅袅升起。

三日之前，这里还是樯橹如林、帆影蔽日。如今，江面上漂浮着断裂的船板和旗幡，偶尔可见浮尸。东风吹过，空气中弥漫着血腥与灰烬的味道。

你的主公站在不远处，面沉如水。他是三日前力主火攻的人，如今大胜，却并无喜色。

"东风吹了一夜。"他说，声音很轻，"周瑜和黄盖，赌赢了这一局。"

一名亲兵快步走来，低声禀报："禀将军，江边有人求见，说是曹营旧识。"

你的主公看了你一眼，目光中有一种你读不懂的东西。`,

    options: [
      { id: 1, text: '劝主公收纳此人，或可探得敌军内情' },
      { id: 2, text: '进言此人来路不明，恐怕有诈' },
      { id: 3, text: '不表态，静观其变' },
    ],
    keywordResonance: { 1: 1, 2: 0, 3: 2 },
  },
  2: {
    sceneText: `那人被带了上来。

是个中年文士，衣衫褴褛，左臂上缠着布带，渗出暗红的血迹。他自称姓徐，原是曹营中的谋士，兵败后匿于江边渔村，藏了三日。

"曹操败走华容道，留我等断后。"他低着头，声音沙哑，"将军若肯收留，我愿将曹营布防、粮草虚实尽数相告。"

你的主公没有说话，只是看着你。

那文士又道："曹营八十万众，如今十不存一。将军若北上，一鼓可定天下。此乃天赐良机，不可失也。"

你的主公终于开口："你觉得呢？"

江风忽然大了，吹得营帐猎猎作响。`,

    options: [
      { id: 1, text: '建议主公接纳此人，借机北上灭曹' },
      { id: 2, text: '劝主公见好就收，此人留中观察为上' },
      { id: 3, text: '保持沉默，此事不宜多言' },
    ],
    keywordResonance: { 1: 2, 2: 1, 3: 3 },
  },
  3: {
    sceneText: `一个月后。

那徐先生的话应验了一半——曹操确实北撤了，但北方的格局比你想象的复杂得多。你的主公占据了荆州，却也因此成为了曹操和孙权共同的敌人。

更大的变故来自内部。那徐先生在被接纳后的第三周突然失踪，留下一封语焉不详的信，说他"另有使命"。你隐约觉得，这整件事从头到尾都像是一个精心设计的棋局，而你和你主公，都只是棋盘上的棋子。

至于那场大火之后，江水是否真的染红了三天三夜，民间流传着许多版本。你听过最离谱的一个说，那晚的东风不是风，是周瑜向天借来的——代价是他的十年寿命。

如今你站在江边，手里捏着那枚被火烧过一半的令牌。

江风还是一样的冷。`,

    options: [
      { id: 1, text: '将此事原委记下，留与后人评说' },
      { id: 2, text: '烧掉令牌，从此不提这段往事' },
      { id: 3, text: '追查徐先生的下落，弄清背后的真相' },
    ],
    keywordResonance: { 1: 3, 2: 2, 3: 4 },
  },
}

const MANUSCRIPT_TEMPLATE = `赤壁记事

建安十三年，冬。

我随主公参与赤壁一战，前后凡三月。今将所见所闻录于此卷，以待后人评点。

是役也，孙刘联军以寡敌众，大破曹军于赤壁。火攻之策出自黄盖，彼时江面东风骤起，风助火势，樯橹灰飞烟灭。

然我在军中，所见不止于此。

是日深夜，江边忽有一文士求见，自称曹营旧识。主公纳之，我曾有异议，未被采纳。此人来历不明，言语闪烁，所献"北上一鼓定天下"之策，后被证明不过是纸上谈兵。

及此人失踪，我始知其意不在我军，亦不在曹营，而在搅动天下大势。惜乎彼时已晚。

赤壁之火，烧掉的是曹操一统天下的野心，也烧掉了主公问鼎中原的最佳时机。东风吹了一夜，吹散了八十万大军，也吹乱了天下格局。

事后思之，颇为感慨：英雄造时势，抑或时势造英雄？我不得而知。

唯记江风猎猎，火光冲天，有人在笑，有人在哭，而更多的人，就那样无声无息地沉入了江底。

这便是赤壁。

这便是历史。`

class StoryTellerService {
  private context: StoryContext | null = null

  initStory(ctx: StoryContext): void {
    this.context = ctx
  }

  getChapter(chapterNo: number): Chapter {
    const data = CHAPTERS[chapterNo] || CHAPTERS[1]
    return {
      chapterNo,
      sceneText: data.sceneText,
      options: data.options,
      keywordResonance: data.keywordResonance,
      ripples: [],
    }
  }

  submitChoice(chapterNo: number, optionId: number): { chapter: Chapter } {
    const nextChapter = chapterNo + 1
    if (nextChapter > 3) {
      // 故事结束，返回空章节作为标记
      return { chapter: { chapterNo, sceneText: '', options: [], ripples: [] } }
    }
    return { chapter: this.getChapter(nextChapter) }
  }

  generateManuscript(_chapterCount: number, _totalDeviation: number): Omit<Manuscript, 'baiguanComment'> {
    return {
      fullText: MANUSCRIPT_TEMPLATE,
      wordCount: MANUSCRIPT_TEMPLATE.length,
      annotations: [
        { chapterNo: 1, x: 0, y: 0, text: '此节与正史略有出入，曹操败走华容道而非主动撤离。', color: 'red' },
        { chapterNo: 2, x: 0, y: 0, text: '徐先生其人，正史无载，或为虚构。', color: 'red' },
        { chapterNo: 3, x: 0, y: 0, text: '此段所记，多为稗官之说，姑妄听之。', color: 'red' },
      ],
      choiceMarks: [
        { chapterNo: 1, optionId: 2, text: '持重' },
        { chapterNo: 2, optionId: 2, text: '观望' },
        { chapterNo: 3, optionId: 3, text: '追查' },
      ],
      epilogue: '尾声',
    }
  }
}

export const storyTeller = new StoryTellerService()
