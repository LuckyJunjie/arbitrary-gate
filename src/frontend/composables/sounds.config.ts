/**
 * sounds.config.ts — 音效配置文件
 *
 * 定义所有音效资源的元数据：
 *   - file: 相对于 public/sounds/ 的文件名
 *   - volume: 播放音量（0~1），上限由 useSound.ts 的 MAX_VOLUME=0.3 控制
 *
 * 音效清单（A-01~A-07）：
 *   ink-drop       — 抽卡水滴叮（A-01）
 *   paper-rub      — 擦墨沙沙声（A-02）
 *   wind-chime     — 卡片显现风铃（A-03）
 *   brush-tap      — 选择舔笔声（A-04）
 *   bell           — 章节编钟（A-05）
 *   chime          — 显灵磬音（A-06）
 *   jade-click     — 合券玉击（A-07）
 */

export const SOUNDS = {
  inkDrop: {
    id: 'inkDrop' as const,
    file: 'ink-drop.mp3',
    volume: 0.25,
    description: '抽卡水滴叮',
  },
  paperRub: {
    id: 'paperRub' as const,
    file: 'paper-rub.mp3',
    volume: 0.2,
    description: '擦墨沙沙声',
  },
  windChime: {
    id: 'windChime' as const,
    file: 'wind-chime.mp3',
    volume: 0.15,
    description: '卡片显现风铃',
  },
  brushTap: {
    id: 'brushTap' as const,
    file: 'brush-tap.mp3',
    volume: 0.2,
    description: '选择舔笔声',
  },
  bell: {
    id: 'bell' as const,
    file: 'bell.mp3',
    volume: 0.25,
    description: '章节编钟',
  },
  chime: {
    id: 'chime' as const,
    file: 'chime.mp3',
    volume: 0.25,
    description: '显灵磬音',
  },
  jadeClick: {
    id: 'jadeClick' as const,
    file: 'jade-click.mp3',
    volume: 0.2,
    description: '合券玉击',
  },
} as const

export type SoundId = keyof typeof SOUNDS

export const SOUND_IDS = Object.keys(SOUNDS) as SoundId[]
