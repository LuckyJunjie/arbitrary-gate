# arbitrary-gate 项目记忆

_最后更新: 2026-04-12 21:39_

## 项目概述
时光笺 - AI说书人游戏，Android/iOS H5应用

## 技术栈
- 前端: Vue3 + TypeScript + Vite + Pinia
- 后端: Spring Boot (Java) + MyBatis-Plus
- 数据库: MySQL + Redis
- AI: 通义千问 (DashScope)
- 部署: Docker + Docker Compose + Nginx

## 事件卡池状态 (D-02) ✅ 完成
- 已有数据: EV001-EV600 (600个事件)
- **完成时间: 2026-04-12 04:40**
- 提交: 10ad6953 — feat(D-02): 完成事件卡池600条 — EV394+EV469-EV600全部写入
- 包含: 历史事件(EV001-EV500) + 虚构传说(EV501-EV560) + 神话武侠(EV561-EV600)
- SQL文件: `docker/mysql/init/02-event_cards.sql`

## 已完成功能清单（近期）
| 任务 | 状态 | 提交/说明 |
|------|------|----------|
| D-02 历史事件卡池(600条) | ✅ | 10ad6953 |
| I-12 Docker部署 | ✅ | 92421d69 (docker-compose.yml + Dockerfile + nginx.conf) |
| UI-06 窗格光影效果 | ✅ | useWindowLight.ts composable + HomeView.vue |
| M-10 批注彩蛋 | ✅ | BaiguanAgent Easter Egg prompt + 黛青色CSS |
| A-01~A-07 音效系统 | ✅ | useSound.ts Web Audio API合成音 |
| I-06 XSS注入防护 | ✅ | bf569099 |
| I-10 图片懒加载 | ✅ | 9ec088d8 |
| SH-05 微信JSSDK分享 | ✅ | ca5c99d2 |
| 题记修色(黛青#4A6B6B) | ✅ | 4cd7f8b0 |
| K-07 卡片墨迹晕染 | ✅ | 0312a9bc |
| UI-07 卷轴天杆/地杆木质视觉 | ✅ | 90a68466 |
| C-07 手牌上限弹窗 | ✅ | 3e78068f |
| C-08 墨迹占卜/今日运势 | ✅ | 3e78068f |
| P-01 入局判词 | ✅ | ff5a91da |
| AI-07 Prompt热更新(4个Agent) | ✅ | Judge/Baiguan/Zhangyan/EncounterAgent |
| AI-04 掌眼Agent | ✅ | AiPhraseFilter已集成 |

## 需求文档
- `docs/产品设计-核心.md` - 核心玩法
- `docs/产品设计-UI美工.md` - UI设计
- `docs/产品设计-补充1-玩法深化.md` - 补充设计
- `docs/opus检查结果1.md` - 需求进度追踪

## 关键常量/字段
- 关键词卡上限: 9张 (KEYWORD_CARD_LIMIT)
- 历史事件卡上限: 3张 (EVENT_CARD_LIMIT)
- 墨香衰减: 每小时 -1 (decayInkFragranceHourly)
- AI显灵阈值: 5次共鸣

## 测试状态
- 320/320 tests ✅ (vitest)
- Build ✅
- E2E 测试: playwright (需配置)

## 剩余高优先级任务
| 任务 | 优先级 | 状态 |
|------|--------|------|
| I-13 CI/CD流水线 | P2 | ✅ 完成 — `.github/workflows/ci.yml` (2026-04-12 05:40) |
| D-04 卡池数据分包扩展机制 | P3 | ✅ 已完成 — commit e6aea473 |
| I-07 分享码不可枚举 | P2 | ✅ 完成 — RateLimitInterceptor (IP限流60s/10次) + Timing Attack防护 (50-200ms延迟) |
| M-10 批注彩蛋 | P3 | ✅ 完成 — useAchievementToast.ts (commit 51e7098a) |
| SH-01~03 分享模块完整链路 | P2 | 🔄 进行中 — sub-agent bc6cf6bd |
| U-05 墨晶充值/购买 | P2 | ❌ 未做（需微信商户号凭证） |
| U-03 游客模式正式方案 | P1 | ⚠️ 需设计（现有guest-login为调试用） |
| I-11 微信环境适配 | P2 | ⚠️ 部分（useWeChatShare已有基础） |

## Git
- 分支: master，与 origin/master 同步
- 无 pending commits

## 已知问题
- apiClient.test.ts 有1个测试失败(预存问题)
- 游客模式 U-03 仅devLogin调试用，需正式方案

## Docker 部署
- `docker-compose.yml`: MySQL + Redis + 后端 + 前端 + Nginx
- `Dockerfile`: 后端构建
- `docker/nginx.conf`: 前端静态资源服务
- 初始化SQL: `docker/mysql/init/02-event_cards.sql` (600条事件)

## Sub-agent 任务记录
- 2026-04-12 05:39: I-13 CI/CD pipeline — ✅ 已完成
- 2026-04-12 07:39: U-07 设置页面 — ✅ 已完成 (commit 74487e54)
- 2026-04-12 10:39: U-02 手机号登录 — ✅ 已完成 (commit 2f51000a)
- 2026-04-12 17:39: D-04 卡池分包扩展 — ✅ 已完成 (commit e6aea473)
- 2026-04-12 21:39: I-07 分享码不可枚举 — ✅ 完成 (commit: feat(I-07), RateLimitInterceptor + TimingAttack防护)
- 2026-04-13 00:39: 成就解锁通知 — ✅ 已完成 (commit 51e7098a, useAchievementToast composable)
- 2026-04-13 00:39: SH-01~03 分享模块完整链路 — 🔄 进行中 (session: bc6cf6bd)
