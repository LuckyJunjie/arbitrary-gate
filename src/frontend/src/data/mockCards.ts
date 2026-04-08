/**
 * 前端模拟卡牌数据（演示用）
 * 包含 10 个关键词卡 + 3 个历史事件
 */

// =====================================================
// 关键词卡牌（演示用 10 张）
// =====================================================
export interface KeywordCard {
  id: number;
  cardNo: string;
  name: string;
  category: 1 | 2 | 3 | 4 | 5;
  rarity: 1 | 2 | 3 | 4;
  weight: number;
  description?: string;
}

export const MOCK_KEYWORD_CARDS: KeywordCard[] = [
  {
    id: 2,
    cardNo: "KW002",
    name: "旧船票",
    category: 1,
    rarity: 3,
    weight: 12,
    description: "一张看不清终点的旧船票",
  },
  {
    id: 5,
    cardNo: "KW005",
    name: "半块玉佩",
    category: 1,
    rarity: 3,
    weight: 12,
    description: "意外碎成两半的玉佩之一",
  },
  {
    id: 23,
    cardNo: "KW023",
    name: "摆渡人",
    category: 2,
    rarity: 2,
    weight: 25,
    description: "渡人过河的沉默船夫",
  },
  {
    id: 27,
    cardNo: "KW027",
    name: "秦腔伶人",
    category: 2,
    rarity: 3,
    weight: 12,
    description: "唱秦腔的戏曲艺人",
  },
  {
    id: 44,
    cardNo: "KW044",
    name: "咸阳宫阙",
    category: 3,
    rarity: 4,
    weight: 3,
    description: "秦帝国心脏的宏伟宫殿",
  },
  {
    id: 49,
    cardNo: "KW049",
    name: "乌江渡",
    category: 3,
    rarity: 3,
    weight: 12,
    description: "项羽自刎的乌江渡口",
  },
  {
    id: 61,
    cardNo: "KW061",
    name: "意难平",
    category: 4,
    rarity: 3,
    weight: 12,
    description: "心中挥之不去的遗憾",
  },
  {
    id: 71,
    cardNo: "KW071",
    name: "亡国恨",
    category: 4,
    rarity: 4,
    weight: 3,
    description: "亡国之君的深仇大恨",
  },
  {
    id: 85,
    cardNo: "KW085",
    name: "阶下囚",
    category: 5,
    rarity: 3,
    weight: 12,
    description: "失去自由的囚徒",
  },
  {
    id: 98,
    cardNo: "KW098",
    name: "末代王朝",
    category: 5,
    rarity: 4,
    weight: 3,
    description: "即将覆灭的腐朽王朝",
  },
];

// =====================================================
// 历史事件卡牌（演示用 3 张）
// =====================================================
export interface EventCard {
  id: number;
  cardNo: string;
  title: string;
  dynasty: string;
  location: string;
  description: string;
  weight: number;
  era?: string;
}

export const MOCK_EVENT_CARDS: EventCard[] = [
  {
    id: 1,
    cardNo: "EV001",
    title: "巨鹿·破釜沉舟",
    dynasty: "秦",
    location: "巨鹿",
    description: "项羽率楚军渡河，凿沉船只，粉碎秦军主力",
    weight: 100,
    era: "秦末",
  },
  {
    id: 5,
    cardNo: "EV005",
    title: "垓下·四面楚歌",
    dynasty: "楚汉",
    location: "垓下",
    description: "楚歌声起，霸王别姬，乌江自刎，楚汉争雄落幕",
    weight: 100,
    era: "楚汉",
  },
  {
    id: 19,
    cardNo: "EV019",
    title: "马嵬驿·贵妃缢死",
    dynasty: "隋唐",
    location: "马嵬驿",
    description: "安史之乱，贵妃被迫缢死于马嵬坡",
    weight: 100,
    era: "唐中",
  },
];

// =====================================================
// 辅助函数
// =====================================================

/** 根据稀有度获取标签文字 */
export function getRarityLabel(rarity: KeywordCard["rarity"]): string {
  const map = { 1: "凡", 2: "珍", 3: "奇", 4: "绝" };
  return map[rarity];
}

/** 根据分类获取标签文字 */
export function getCategoryLabel(
  category: KeywordCard["category"]
): string {
  const map = { 1: "器物", 2: "职人", 3: "风物", 4: "情绪", 5: "称谓" };
  return map[category];
}

/** 稀有度颜色映射 */
export function getRarityColor(rarity: KeywordCard["rarity"]): string {
  const map: Record<number, string> = {
    1: "#8C8C8C", // 凡 - 灰色
    2: "#4CAF50", // 珍 - 绿色
    3: "#2196F3", // 奇 - 蓝色
    4: "#FF9800", // 绝 - 橙色
  };
  return map[rarity];
}
