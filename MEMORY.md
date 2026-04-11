# arbitrary-gate 项目记忆

_最后更新: 2026-04-11 20:23_

## 项目概述
时光笺 - AI说书人游戏，Android/iOS H5应用

## 技术栈
- 前端: Vue3 + TypeScript + Vite + Pinia
- 后端: Spring Boot (Java) + MyBatis-Plus
- 数据库: MySQL + Redis
- AI: 通义千问 (DashScope)
- 部署: Docker

## 事件卡池状态 (D-02)
- 已有数据: EV031-EV369 (338个事件，来自 import_event_cards.sql + events_batch*.json + gen_extended_events.py + gen_events_ev269.py)
- 目标: 600个事件
- 缺口: EV001-EV030(30) + EV268(1) + EV370-EV600(231) = 262个
- **状态: Hermes sub-agent (session: 06706423) 正在生成 import_event_cards_v2.sql**

## 需求文档
- `docs/产品设计-核心.md` - 核心玩法
- `docs/产品设计-UI美工.md` - UI设计
- `docs/产品设计-补充1-玩法深化.md` - 补充设计

## 关键常量/字段
- 关键词卡上限: 9张 (KEYWORD_CARD_LIMIT)
- 历史事件卡上限: 3张 (EVENT_CARD_LIMIT)
- 墨香衰减: 每小时 -1 (decayInkFragranceHourly)
- AI显灵阈值: 5次共鸣

## 今日完成
| 任务 | 提交 |
|------|------|
| I-06 XSS注入防护 | bf569099 |
| I-10 图片懒加载 | b1c92f71, 9ec088d8 |
| SH-05 微信JSSDK分享 | ca5c99d2 |
| 题记修色(黛青#4A6B6B) | 4cd7f8b0 |
| K-07 卡片墨迹晕染 | 0312a9bc |
| C-07 手牌上限弹窗 | 3e78068f |
| C-08 墨迹占卜/今日运势 | 3e78068f |
| P-01 入局判词 | ff5a91da |
| AI-07 Prompt热更新(4个Agent) | (memory记录) |
| AI-04 掌眼Agent | (已集成) |
| Build修复 + Vue导入 | 0ed8302b |
| gen_extended_events.py truncate fix | fbe6a7fd |

## 测试状态
- 320/320 tests ✅
- Build ✅ (2026-04-11 20:23)

## 剩余高优先级任务
- D-02 历史事件卡池(338→600) - Hermes处理中
- UI-06 窗格光影效果 - Apollo
- M-10 批注彩蛋(打四壁说书人批注) - Apollo
- A-01~A-07 音效系统 - Apollo
- I-12 Docker部署 - Hermes
- I-13 CI/CD流水线 - Hermes

## Git
- 领先 origin/master 2 commits (0ed8302b, fbe6a7fd)
- 包含: Build修复 + truncated line fix

## 已知问题
- apiClient.test.ts 有1个测试失败(预存问题)
- 游客模式 U-03 仅devLogin调试用，需正式方案
