# arbitrary-gate 项目记忆

_最后更新: 2026-04-14 04:39_

## 项目概述
时光笺 - AI说书人游戏，Android/iOS H5应用

## 技术栈
- 前端: Vue3 + TypeScript + Vite + Pinia
- 后端: Spring Boot (Java) + MyBatis-Plus
- 数据库: MySQL + Redis
- AI: 通义千问 (DashScope wanx2.1)
- 部署: Docker + Docker Compose + Nginx + GitHub Actions CI/CD

## 事件卡池状态 (D-02) ✅ 完成
- 已有数据: EV001-EV600 (600个事件)
- **完成时间: 2026-04-12 04:40**
- 提交: 10ad6953 — feat(D-02): 完成事件卡池600条 — EV394+EV469-EV600全部写入
- 包含: 历史事件(EV001-EV500) + 虚构传说(EV501-EV560) + 神话武侠(EV561-EV600)
- SQL文件: `docker/mysql/init/02-event_cards.sql`

## 已完成功能清单（完整）
| 任务 | 状态 | 提交/说明 |
|------|------|----------|
| D-02 历史事件卡池(600条) | ✅ | 10ad6953 |
| D-04 卡池数据分包扩展机制 | ✅ | e6aea473 (事件卡) + 2cafbb7f (关键词卡) |
| I-12 Docker部署 | ✅ | 92421d69 (docker-compose.yml + Dockerfile + nginx.conf) |
| I-13 CI/CD流水线 | ✅ | `.github/workflows/ci.yml` |
| UI-06 窗格光影效果 | ✅ | useWindowLight.ts composable + HomeView.vue |
| UI-07 卷轴天杆/地杆木质视觉 | ✅ | commit 90a68466 |
| UI-09 缓动曲线统一 | ✅ | commit 9c86273d — 所有硬编码cubic-bezier替换为CSS变量，100%收敛 |
| A-01~A-07 音效系统 | ✅ | useSound.ts Web Audio API合成音 |
| M-03 题记生成 | ✅ | commit 1498f4b5 |
| M-10 批注彩蛋 | ✅ | useAchievementToast.ts (commit 51e7098a) |
| K-06/K-07 卡片墨迹效果 | ✅ | commit 0312a9bc |
| C-07 手牌上限弹窗 | ✅ | commit 3e78068f |
| C-08 墨迹占卜/今日运势 | ✅ | commit 3e78068f |
| C-09 称号系统 | ✅ | 已完成 |
| C-11 墨晶商城 | ✅ | 已完成 |
| P-01 入局判词 | ✅ | commit ff5a91da |
| P-03 稀有组合成就 | ✅ | commit eae1800c (补充单元测试) |
| P-04 其他成就 | ✅ | commit a065284f |
| AI-04 掌眼Agent | ✅ | AiPhraseFilter已集成 |
| AI-05 AI画师后端对接 | ✅ | commit 7eda7b3f — 关键词卡图/场景图/分享卡 |
| AI-06 AI腔词黑名单 | ✅ | commit cee92bbf |
| AI-07 Prompt热更新(4个Agent) | ✅ | Judge/Baiguan/Zhangyan/EncounterAgent |
| I-06 XSS注入防护 | ✅ | commit bf569099 |
| I-07 分享码不可枚举 | ✅ | RateLimitInterceptor (IP限流60s/10次) + Timing Attack防护(50-200ms) |
| I-10 图片懒加载 | ✅ | commit 9ec088d8 |
| I-11 微信环境适配 | ✅ | viewport禁用缩放 + @vitejs/plugin-legacy + webkit-overflow-scrolling |
| 批量文档同步 | ✅ | scripts/batch-doc-sync/sync.js (commit 1a78590c) |
| SH-05 微信JSSDK分享 | ✅ | commit ca5c99d2 |
| 题记修色(黛青#4A6B6B) | ✅ | commit 4cd7f8b0 |
| SH-01~03 分享模块完整链路 | ✅ | commit 6ff6c220（缺角卡/合券/分享码，2026-04-13推送） |
| U-02 手机号登录 | ✅ | commit 2f51000a |
| U-03 游客模式正式方案 | ✅ | guestLogin + phoneLogin数据合并，游客每日1次免费抽 |
| U-07 设置页面 | ✅ | commit 74487e54 |
| 成就解锁通知 | ✅ | useAchievementToast.ts (commit 51e7098a) |

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
- 357/357 tests ✅ (vitest)
- Build ✅
- E2E: playwright (需配置)

## 剩余阻塞任务
| 任务 | 优先级 | 状态 | 说明 |
|------|--------|------|------|
| U-05 墨晶充值/购买 | P2 | 🔄 待配置 | PayService+ShopView ✅；仅需在 .env 配置 WECHAT_MCH_ID + WECHAT_API_KEY 即可上线 |

## Git
- 分支: master，与 origin/master 同步
- 待提交: MEMORY.md, CHANGELOG.md, .gitignore, storyStore.ts, main.ts

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
- 2026-04-12 21:39: I-07 分享码不可枚举 — ✅ 完成 (commit a2e358c5)
- 2026-04-13 00:39: 成就解锁通知 — ✅ 已完成 (commit 51e7098a)
- 2026-04-13 00:39: SH-01~03 分享模块完整链路 — ✅ 已完成 (commit 6ff6c220)
- 2026-04-13 01:44: P-02 稀有组合检测优先级修复 — ✅ 已完成 (commit 46f2a9d2)
- 2026-04-13 02:48: UI-09 缓动曲线标准化 — ✅ 已完成 (commit ab0814cd)；统一使用 CSS 变量：--ease-smooth / --ease-spring / --ease-out / --ease-loop；全部 .vue/.ts 文件 non-standard easing 已收敛，仅 ScratchCard.vue linear 进度条属例外
- 2026-04-13 05:42: P-03 稀有组合成就单元测试 — ✅ 已完成 (commit eae1800c)
- 2026-04-13 06:39: MEMORY.md 完整同步 — ✅ 完成
- 2026-04-13 10:40: 批量文档同步工具 — ✅ 已完成 (commit 1a78590c)
  - scripts/batch-doc-sync/sync.js: Node.js 批量同步脚本
  - data/: 示例数据文件 (keyword_cards_sample.json, event_cards_sample.json, achievements_sample.json)
- 2026-04-13 12:12: 单元测试修复 — ✅ 已完成 (commit feb9619b)
  - apiClient.test.ts: fetchStoryList 调用参数修正
  - storyStore.test.ts: fetchStoryList mock 返回值分页结构修正
  - 357/357 tests passing
- 2026-04-13 12:21: UI-09 缓动曲线全面收敛 — ✅ 已完成 (commit 9c86273d)
  - 所有硬编码 cubic-bezier 替换为 CSS 变量
  - cubic-bezier(0.22,1,0.36,1) → var(--ease-spring) (4处)
  - cubic-bezier(0.34,1.56,0.64,1) → var(--ease-bounce) (3处)
  - cubic-bezier(0.4,0,0.2,1) → var(--ease-smooth) (1处)
  - ease → var(--ease-smooth) (2处)
- 2026-04-13 12:25: D-04 关键词卡扩展包支持 — ✅ 已完成 (commit 2cafbb7f)
  - Backend: KeywordCard 新增 expansionCode 字段
  - CardService.getKeywordCardsByExpansion() + GET /api/card/keyword-cards
  - SQL迁移: docker/mysql/init/04-keyword_card_expansion.sql
  - Frontend: fetchKeywordCards API + KeywordCard.expansionCode
- 2026-04-13 12:36: U-06 个人信息页完善 — ✅ 已完成 (commit 02ff40be)
  - api.ts: updateNickname() 新增
  - SettingsView.vue: 昵称点击编辑 (仅非游客), 保存/取消, 输入校验
  - A-01~A-07 音效系统全部确认完成

---

_更新于 2026-04-13 18:40_

## 最新提交 (2026-04-13 下午)
- `4aed1de7` feat(S-16): 完成断线重连继续阅读功能
- `8b4f57ee` fix(backend): 修复 getKeywordCards 和 getEventCardName 硬编码问题
- `caa7b7a8` docs: 确认A-01~A-07音效全部完成

## 项目状态总结
- **测试**: 403/403 tests ✅ (新增 PayServiceTest 17项 + ShopViewTest 46项)
- **构建**: ✅
- **唯一阻塞**: U-05 墨晶充值 — 主体代码✅单元测试✅，待微信商户号(mchId/apiKey)配置后即可上线
- **P-02 状态修正**: 稀有组合检测已完整实现，⚠️标记系旧文档残留(已更正)

## Sub-agent 任务记录
- 2026-04-13 18:40: P-02 文档状态修正 ✅ (主 session 完成)
- 2026-04-14 03:44: 最终收尾 — 更新opus统计+提交dist — ✅ 已完成
- 2026-04-14 04:39: 清理调试日志 + 更新CHANGELOG + 修复.gitignore — ✅ 已完成 (commit 270140b5)
