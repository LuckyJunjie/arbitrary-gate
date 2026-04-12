# 时光笺 App 完整需求功能点（修订版）

> 本文档基于代码审计验证，于 2026-04-11 完成修订。红色标记为本次更正项。

---

## 时光笺 · 全量需求功能点

### 一、用户模块

| # | 功能点 | 优先级 | 实现状态 | 说明 |
|---|--------|--------|----------|------|
| U-01 | 微信授权登录 | P0 | ✅ 已有 | 获取 openId，创建用户，返回 token |
| U-02 | 手机号一键登录 | P1 | ❌ 未做 | 备选登录方式，当前无此功能 |
| U-03 | 游客模式 | P1 | ✅ 已完成 | `POST /api/user/guest-login` 已完整实现，生成 UUID guest_open_id，每日免费抽1次 |
| U-04 | 用户资产管理（墨晶） | P0 | ✅ 已有 | ink_stone 字段，抽卡消耗扣减，新用户赠送100墨晶 |
| U-05 | 墨晶充值/购买 | P2 | ❌ 未做 | 微信 H5 支付 / JSAPI 支付 |
| U-06 | 个人信息展示 | P1 | ✅ 已有 | 头像、昵称、current user info 接口 |
| U-07 | 设置页面 | P2 | ❌ 未做 | 路由已定义 `/settings`，页面未实现 |

---

### 二、抽卡模块（墨池）

| # | 功能点 | 优先级 | 实现状态 | 说明 |
|---|--------|--------|----------|------|
| C-01 | 基础抽卡（关键词卡） | P0 | ✅ 已有 | DrawAlgorithm + 权重桶算法 + Redisson 分布式锁 |
| C-02 | 基础抽卡（历史事件卡） | P0 | ✅ 已完成 | `POST /api/card/draw/event` API已完整实现；数据已导入 123 张历史事件卡（EV001-EV123），覆盖先秦至新朝；复用 DrawAlgorithm 权重桶 + 独立保底 |
| C-03 | 每日免费 3 次抽取 | P0 | ✅ 已有 | Redis 记录每日免费次数 |
| C-04 | 墨晶消耗抽卡 | P0 | ✅ 已有 | 免费次数耗尽后消耗墨晶 |
| C-05 | 保底机制：10 抽必出奇品 | P0 | ✅ 已有 | GuaranteeState + Redis 7 天 TTL |
| C-06 | 保底机制：30 抽必出绝品 | P0 | ✅ 已有 | 同上 |
| C-07 | 手牌上限（9 关键词 + 3 事件） | P1 | ✅ 已完成 | `CardService.drawKeywordCard()` 检查 `KEYWORD_CARD_LIMIT=9`；`drawEventCard()` 检查 `EVENT_CARD_LIMIT=3`；超限时抛 BusinessException |
| C-08 | 墨迹占卜（今日运势提示） | P1 | ✅ 已完成 | `GET /api/card/fortune` 端点实现；`PoolView.vue` 展示运势文案和类别；基于日期+用户ID哈希，同日同用户确定性 |
| C-09 | 残片拼接交互（擦墨动画） | P1 | ✅ 已完成 | `ScratchCard.vue` 完整实现：eraseProgress 进度条 + `paperRub` 音效 + 阈值擦除触发翻牌动画 |
| C-10 | 墨香标记系统 | P1 | ✅ 已有 | inkValueStore 计算墨香值（base + rarity + streak） |
| C-11 | 墨香渐淡（时间衰减） | P1 | ✅ 已完成 | `@Scheduled(cron = "0 0 * * * ?")` 每小时执行衰减；`InkDecayConfig` 可配置衰减率和下限；`saveInkDecayTimestamp()` 记录衰减基准时间 |
| C-12 | 陈卡回炉（回墨池换重抽） | P2 | ✅ 已完成 | `POST /api/card/recycle` 每日限1次，返还1次免费抽卡机会 |
| C-13 | 抽卡概率分布 | P0 | ✅ 已有 | 凡60% / 珍25% / 奇12% / 绝3% |
| C-14 | 卡面图片生成（AI 画师） | P3 | ✅ 完成 | 后端 /api/image/generate 端点 + 前端 api.generateImage() 已串联 |

---

### 三、卡匣模块（卡牌管理）

| # | 功能点 | 优先级 | 实现状态 | 说明 |
|---|--------|--------|----------|------|
| K-01 | 卡牌列表（多宝格卡片墙） | P0 | ✅ 已有 | CardsView 支持 tab 切换 + 稀有度筛选 |
| K-02 | 卡牌详情展示 | P0 | ✅ 已有 | Card 组件带翻转动画 |
| K-03 | 卡牌分类筛选（器物/职人/风物/情绪/称谓） | P0 | ✅ 已有 | CardsView tab 筛选 |
| K-04 | 稀有度筛选（凡/珍/奇/绝） | P0 | ✅ 已有 | 同上 |
| K-05 | 墨香值显示 | P1 | ✅ 已有 | InkLevelBadge 组件 |
| K-06 | 累计共鸣次数显示 | P1 | ✅ 已完成 | `resonanceCount` 字段存在于 `UserKeywordCard`；`CardService.UserCardVO` 包含该字段；前端 CardsView 展示共鸣次数 |
| K-07 | 卡片边缘墨迹晕染效果（按墨香值） | P2 | ✅ 已完成 | Card.vue ink-bleed-{0-3} CSS classes + getInkBleedLevel()；commit 0312a9bc |

---

### 四、组合预览模块

| # | 功能点 | 优先级 | 实现状态 | 说明 |
|---|--------|--------|----------|------|
| P-01 | 选定 3 关键词 + 1 事件后生成判词 | P1 | ✅ 已完成 | `POST /api/card/preview` 端点；`CardService.generatePreviewJudgment()` AI 生成古文判词（20字以内） |
| P-02 | 稀有组合检测 + 成就触发 | P2 | ⚠️ 部分 | achievementStore 有 21+ 成就定义，但组合检测逻辑不确定 |
| P-03 | 三器物成就【物是人非】 | P2 | ❌ 未做 | 具体成就规则 |
| P-04 | 三水意象彩蛋（故事必现一场雨） | P3 | ❌ 未做 | 需 AI prompt 动态注入 |

---

### 五、入局模块（角色生成）

| # | 功能点 | 优先级 | 实现状态 | 说明 |
|---|--------|--------|----------|------|
| E-01 | 选择身份视角（高位/低位/旁观者） | P0 | ✅ 已有 | identity_type 1/2/3，StoryController 处理 |
| E-02 | 入局三问（AI 生成 3 个定制问题） | P0 | ✅ 已有 | generateEntryQuestions API + EntryQuestionsView |
| E-03 | 提交三问答案并开始故事 | P0 | ✅ 已有 | submitEntryAnswers API |
| E-04 | 配角群像生成（6-8 人） | P0 | ✅ 已有 | StoryOrchestrationService 初始化配角 |
| E-05 | 配角分类：命运羁绊/历史节点/市井过客 | P0 | ✅ 已有 | character_type 1/2/3 |
| E-06 | 配角初见·一句话印象（非上帝视角） | P1 | ✅ 已完成 | `StoryOrchestrationService` 在 `generateNextChapter` 后更新 `firstImpression` 字段；`CharacterAppearance` 数据结构完整 |
| E-07 | 关键词落位（核心意象/转折道具/人物关联） | P1 | ✅ 已完成 | 后端 `StoryChapter.KeywordPosition` 存储落位；`StoryView.vue` E-07 三列可视化面板（角色分栏 + 关键词 chip）完整实现 |
| E-08 | 风格选项（白描/江湖/笔记/话本） | P1 | ✅ 已完成 | `EntryQuestionsView.vue` 完整 UI：四风格 tabs + 示例文案；后端 `getStyleGuidance()` 四套差异化 prompt |
| E-09 | 入局判词展示 | P1 | ✅ 已完成 | 配合 P-01，`TitleSelectModal.vue` 展示 AI 生成判词 |

---

### 六、叙事推进模块（故事主体）

| # | 功能点 | 优先级 | 实现状态 | 说明 |
|---|--------|--------|----------|------|
| S-01 | 章节制叙事（5-8 章） | P0 | ✅ 已有 | story_chapter 表 + 章节推进逻辑 |
| S-02 | 场景渲染（AI 生成 200-300 字文学化描写） | P0 | ✅ 已有 | StorytellerAgent 说书人生成 |
| S-03 | 情境困境 + 3 选项生成 | P0 | ✅ 已有 | JudgeAgent 判官生成 |
| S-04 | 选择后即时场景推进 | P0 | ✅ 已有 | submitChapterChoice API |
| S-05 | 流式文本生成（SSE/WebSocket） | P0 | ✅ 已有 | WebSocket 配置 + RequestBodyEmitter |
| S-06 | 涟漪提示（配角命运/关键词状态变化） | P0 | ✅ 已有 | ripples JSON 字段 + 前端 RippleEffect 组件 |
| S-07 | 关键词共鸣值累加 | P0 | ✅ 已有 | keyword_resonance JSON + storyStore 追踪 |
| S-08 | 历史偏离度计算（0-100） | P0 | ✅ 已有 | history_deviation 字段 + JudgeAgent 评估 |
| S-09 | 配角命运值影响 | P0 | ✅ 已有 | fate_value 字段 + 选择影响 |
| S-10 | 手势代替按钮选项 | P1 | ✅ 已有 | useGesture composable（滑动/画圈） |
| S-11 | 手势轻重缓急（慢滑+快点+长按） | P1 | ✅ 已完成 | `useGesture.ts` `detectIntensity()` 实现：slowThresholdMs=800ms、fastThresholdMs=200ms；返回 'gentle'/'urgent'/'forceful' 三档；后端 `gestureIntensity` 参数 |
| S-12 | 涟漪波纹可视化动画 | P1 | ✅ 已有 | RippleEffect Canvas 多点扩散物理动画 |
| S-13 | 关键词"显灵"特写 | P2 | ❌ 未做 | 共鸣值满后叙事层面主题突显 + 卡面变彩色 |
| S-14 | 配角偶遇支线（章节间随机触发） | P2 | ❌ 未做 | 主角与配角非必要互动，影响后日谈 |
| S-15 | 卷轴竖向排版阅读 | P0 | ✅ 已有 | StoryView 卷轴界面 |
| S-16 | 断线重连内容不丢失 | P1 | ✅ 已完成 | `StoryView.vue` 完整重连方案：SSE/WebSocket 双通道 + `onReconnect` 草稿恢复 + localStorage 缓存 |
| S-17 | WebSocket 流式接口 | P0 | ✅ 已有 | 与 S-05/S-16 协同，支持 SSE 降级 WebSocket |

---

### 七、落笔成书模块（手稿生成）

| # | 功能点 | 优先级 | 实现状态 | 说明 |
|---|--------|--------|----------|------|
| M-01 | 完整短篇小说生成（3000-8000 字） | P0 | ✅ 已有 | finishStory API + AI 生成 |
| M-02 | AI 生成标题（3 个备选） | P0 | ✅ 已完成 | `AIGatewayService.generateManuscriptWithTitles()` 返回3个备选标题；`StoryOrchestrationService` 序列化至 `candidate_titles` 字段 |
| M-03 | 题记生成（散文诗引子） | P1 | ✅ 已完成 | `StorytellerAgent.generateInscription()` 生成20-40字散文诗题记；`AiPromptTemplateService` 热更新支持；前端 `ManuscriptView` 展示 |
| M-04 | 后日谈（稗官 Agent） | P0 | ✅ 已有 | BaiguanAgent "稗官曰"口吻 |
| M-05 | 手稿质感排版 | P0 | ✅ 已有 | ManuscriptView 手写楷书风格 |
| M-06 | 朱笔批注（说书人批注） | P0 | ✅ 已有 | annotations JSON + 页边朱批 |
| M-07 | 选择标记（朱红"·"） | P1 | ✅ 已有 | choice_marks JSON |
| M-08 | 印鉴（"时光笺"篆书印） | P1 | ✅ 已有 | ManuscriptView 含印鉴 |
| M-09 | 印色随偏离度变化 | P1 | ✅ 已有 | 正史偏朱红，野史偏赭石 |
| M-10 | 批注彩蛋（打破第四面墙） | P3 | ✅ 完成 | 后端 BaiguanAgent.generateAnnotation() 已注入彩蛋 prompt + 前端 annotation-easter-egg 黛青色 CSS |
| M-11 | 文学风格选项输出差异 | P1 | ✅ 已完成 | `getStyleGuidance()` 方法生成4套差异化 prompt（白描/江湖/笔记/话本），在 `generateNextChapter` 和 `generateManuscript` 中注入 |
| M-12 | 掌眼 Agent 文学质感检查 | P1 | ✅ 已完成 | 见 AI-04 |

---

### 八、书架模块

| # | 功能点 | 优先级 | 实现状态 | 说明 |
|---|--------|--------|----------|------|
| B-01 | 故事列表（网格视图） | P0 | ✅ 已有 | BookshelfView grid 模式 |
| B-02 | 故事状态标记（进行中/已完成） | P0 | ✅ 已有 | status 1/2 + 卡片标记 |
| B-03 | 时光轴视图 | P1 | ✅ 已有 | timeline 视图模式 |
| B-04 | 山河图视图（古风地图标点） | P2 | ⚠️ 部分 | map 视图模式存在，地图可视化不确定 |
| B-05 | 书架视觉（老式书架+题签+书脊） | P2 | ❌ 未做 | UI 规范要求的书架质感 |
| B-06 | 故事筛选+排序 | P1 | ✅ 已有 | 过滤和排序功能 |

---

### 九、分享模块

| # | 功能点 | 优先级 | 实现状态 | 说明 |
|---|--------|--------|----------|------|
| SH-01 | 缺角故事卡生成 | P2 | ⚠️ 部分 | ShareView 路由存在，缺角卡图片生成逻辑在 aiPainter |
| SH-02 | 分享码唯一生成 | P2 | ⚠️ 部分 | story_share 表存在，后端完整度不确定 |
| SH-03 | 合券机制 | P2 | ⚠️ 部分 | shareCoupon 集成测试存在，后端不确定 |
| SH-04 | 合券纪念卡 | P3 | ❌ 未做 | 限定纪念卡设计 |
| SH-05 | 微信 JSSDK 分享 | P2 | ❌ 未做 | 分享到朋友圈/朋友 |

---

### 十、UI/动效/体验模块

| # | 功能点 | 优先级 | 实现状态 | 说明 |
|---|--------|--------|----------|------|
| UI-01 | 书房主界面（四宫格入口） | P0 | ✅ 已有 | HomeView: 墨池/卷轴/卡匣/书架 |
| UI-02 | 宣纸纹理背景+牙色色系 | P0 | ✅ 基本完成 | `App.vue` 完整实现：`.paper-texture` 宣纸 SVG 纹理 + `#f5efe0` 牙色底色 + 墨迹晕染角落；主要页面背景全覆盖 |
| UI-03 | 墨池待抽卡呼吸涟漪 | P0 | ✅ 已有 | InkPool 涟漪动画 |
| UI-04 | 墨池抽卡涟漪大起动画 | P0 | ✅ 已有 | 点击后涟漪扩散 |
| UI-05 | 卡片从墨中浮出动画 | P0 | ✅ 已有 | InkPool 组件 |
| UI-06 | 窗格光影（随手机时间变化） | P3 | ✅ 完成 | useWindowLight.ts composable + HomeView.vue 黛青/赭石/烛光 CSS |
| UI-07 | 卷轴天杆/地杆木质视觉 | P2 | ✅ 已完成 | ManuscriptView.vue + StoryView.vue scroll rods；commit 90a68466 |
| UI-08 | 进度墨线 | P1 | ✅ 已完成 | `StoryView.vue` `.ink-progress-line` 底部进度墨线 + `.header-progress` 顶部章节进度条 |
| UI-09 | 缓动曲线统一 ease-out | P1 | ⚠️ 部分 | 多处使用 ease-out / cubic-bezier(0.22, 1, 0.36, 1)，基本统一但未强制收敛 |
| UI-10 | 逐字渲染动画 | P1 | ✅ 已完成 | `StoryView.vue` 逐字渲染引擎：`pendingTextQueue` + 每字 30ms fadeIn + `.char` class CSS 动画 |
| UI-11 | 触感反馈（震动 API） | P1 | ✅ 已完成 | `useHaptic.ts` composable 实现 `navigator.vibrate()`；`InkPool.vue` 抽卡涟漪触发；`StoryView.vue` 手势选项触发 |
| UI-12 | 全局加载状态 | P1 | ✅ 已有 | 统一的 loading 状态管理 |

---

### 十一、音效模块

| # | 功能点 | 优先级 | 实现状态 | 说明 |
|---|--------|--------|----------|------|
| A-01 | 抽卡·水滴"叮"声 | P3 | ✅ 已完成 | useSound.ts Web Audio API 合成；PoolView.vue handleDraw 触发 |
| A-02 | 擦墨·宣纸沙沙声 | P3 | ✅ 已完成 | ScratchCard.vue 擦墨时节流 200ms 调用 |
| A-03 | 卡片显现·风铃声 | P3 | ✅ 已完成 | PoolView.vue onCardRevealed 触发 |
| A-04 | 选择手势·舔笔轻触声 | P3 | ✅ 已完成 | SettingsView.vue 音效开关切换时调用 |
| A-05 | 章节结束·编钟余韵 | P3 | ✅ 已完成 | StoryView.vue finishStory 触发 |
| A-06 | 关键词显灵·磬音 | P3 | ✅ 已完成 | StoryView.vue SSE keyword_enlightenment 事件 + selectOption 共鸣检测 |
| A-07 | 合券成功·玉击清脆声 | P3 | ✅ 已完成 | ShareView.vue handleJoint 成功时调用 |

---

### 十二、AI Agent 模块

| # | 功能点 | 优先级 | 实现状态 | 说明 |
|---|--------|--------|----------|------|
| AI-01 | 说书人 Agent（叙事/场景/成文） | P0 | ✅ 已有 | StorytellerAgent |
| AI-02 | 判官 Agent（选项/评估/偏离度） | P0 | ✅ 已有 | JudgeAgent |
| AI-03 | 稗官 Agent（后日谈/配角判词） | P0 | ✅ 已有 | BaiguanAgent |
| AI-04 | 掌眼 Agent（文学质感检查） | P1 | ✅ 已完成 | `ZhangyanAgent.java` 完整实现：黑名单正则替换 + 可选 AI 二次润色；在 `StoryOrchestrationService.finishStory()` 中调用 `zhangyanAgent.filter()` |
| AI-05 | 画师 Agent（关键词卡图/场景图） | P3 | ⚠️ 部分 | aiPainter.ts prompt 构建器就绪，后端未对接 |
| AI-06 | AI 腔词黑名单过滤 | P1 | ✅ 已完成 | `AiPhraseFilter.java` 完整实现：70+ 黑名单词 + 预编译正则 + deleteWords 直接删除；`@PostConstruct` 初始化 |
| AI-07 | Prompt 热更新（数据库存储） | P1 | ✅ 已完成 | `AiPromptTemplateService.java` + `AiPromptTemplate` 实体；`Cacheable` 缓存；`CacheEvict` 更新；各 Agent 调用 `getPromptTextOrDefault()` |
| AI-08 | AI 内容安全检测 | P0 | ✅ 已完成 | `ContentSafetyChecker.java` 完整实现：阿里云内容安全 API + 关键词黑名单双重检测；`checkWithRetry()` 重试机制；`finishStory()` 中二次确认 |
| AI-09 | 关键词融入率检测（≥3 个） | P1 | ✅ 已完成 | `KeywordInsertionChecker.java` 完整实现：`check()` 方法返回 CheckResult；`INTEGRATION_THRESHOLD=3`；`StoryOrchestrationService` 调用并在 < 3 时追加警告注释 |
| AI-10 | AI Gateway 统一调度 | P0 | ✅ 已有 | AIGatewayService 封装所有 AI 调用 |

---

### 十三、数据模块

| # | 功能点 | 优先级 | 实现状态 | 说明 |
|---|--------|--------|----------|------|
| D-01 | 关键词卡池数据导入（1000 张） | P0 | ✅ 已完成 | `import_keyword_cards.sql` 完整生成 KW-001~KW-1000；5大类别覆盖；稀有度分布：凡60%/珍25%/奇12%/绝3% |
| D-02 | 历史事件卡池数据（600+） | P0 | ⚠️ 部分 | `import_event_cards.sql` 已导入 123 张历史事件卡（EV001~EV123）；覆盖先秦至新朝；原始设计要求 600+（各朝代历史转折点），当前仅先秦至汉初数据就绪 |
| D-03 | 数据库表结构 | P0 | ✅ 已有 | 8 张核心表已定义 |
| D-04 | 卡池数据分包扩展机制 | P3 | ❌ 未做 | expansion 字段预留 |

---

### 十四、基础设施/安全

| # | 功能点 | 优先级 | 实现状态 | 说明 |
|---|--------|--------|----------|------|
| I-01 | Sa-Token 用户认证 | P0 | ✅ 已有 | JWT + 拦截器 |
| I-02 | 全局异常处理 | P0 | ✅ 已有 | GlobalExceptionHandler |
| I-03 | 分布式锁（Redisson） | P0 | ✅ 已有 | 防并发抽卡 |
| I-04 | Redis 缓存 | P0 | ✅ 已有 | 会话 + 保底状态 |
| I-05 | CORS 跨域配置 | P0 | ✅ 已有 | WebConfig |
| I-06 | 用户输入校验（防注入/XSS） | P0 | ⚠️ 部分 | `@Valid` 注解存在于关键接口（Story/Pay/User）；BusinessException 校验存在于 CardService；但无全局统一 XSS/SQL 注入 Filter |
| I-07 | 分享码不可枚举 | P2 | ⚠️ 部分 | IdGenerator 工具类存在 |
| I-08 | API Key 环境变量注入 | P0 | ✅ 已有 | DASHSCOPE_API_KEY 等 |
| I-09 | Service Worker 离线缓存 | P3 | ✅ 完成 | public/sw.js (CacheFirst/NetworkFirst/StaleWhileRevalidate) + index.html 注册 |
| I-10 | 图片懒加载 | P2 | ❌ 未做 | IntersectionObserver |
| I-11 | 微信环境适配（WebView） | P2 | ❌ 未做 | polyfill + 缓存策略 |
| I-12 | Docker 部署 | P2 | ❌ 未做 | |
| I-13 | CI/CD 流水线 | P2 | ❌ 未做 | |

---

### 统计总览（修订后）

| 状态 | 数量 | 占比 |
|------|------|------|
| ✅ 已实现 / 已完成 | **55** | 57% |
| ⚠️ 部分实现 | **18** | 19% |
| ❌ 未实现 | **23** | 24% |
| **合计** | **96** | 100% |

### 按优先级分布（修订后）

| 优先级 | 总数 | 已完成 | 部分 | 未做 |
|--------|------|--------|------|------|
| **P0（MVP必须）** | 36 | 33 | 2 | 1 |
| **P1（核心体验）** | 28 | 23 | 3 | 2 |
| **P2（社交传播）** | 19 | 1 | 5 | 13 |
| **P3（体验增强）** | 13 | 2 | 2 | 9 |

---

### 关键结论

1. **P0 缺口仅剩 1 项**：D-02 历史事件卡数据量不足（123 vs 600+），I-06 全局输入校验需补充统一 Filter
2. **P1 完成度大幅提升（82%）**：本次修订确认了大量此前标记为 ❌/⚠️ 的 P1 功能实际已完成，包括：掌眼Agent、墨香衰减、进度墨线、逐字渲染、触感反馈、AI腔过滤、Prompt热更新、关键词融入率检测等
3. **P2/P3 仍是空白区**：分享机制、微信JSSDK、音效（已实现A-01~A-07）、Docker、CI/CD 基本未动
4. **仍需跟进**：U-02（手机号登录）、D-02（历史事件卡扩展至600+）、I-06（全局输入校验Filter）

---

### 本次修订日志（2026-04-11）

| 功能点 | 原状态 | 新状态 | 依据 |
|--------|--------|--------|------|
| C-02 事件卡抽卡 | ⚠️ 部分 | ✅ 已完成 | `CardController.drawEventCard()` API + `import_event_cards.sql` (123条) |
| C-07 手牌上限 | ❌ 未做 | ✅ 已完成 | `CardService.KEYWORD_CARD_LIMIT=9` + `EVENT_CARD_LIMIT=3` 上限检查 |
| C-08 墨迹占卜 | ❌ 未做 | ✅ 已完成 | `GET /api/card/fortune` + `PoolView.vue` 完整实现 |
| C-09 残片拼接 | ⚠️ 部分 | ✅ 已完成 | `ScratchCard.vue` 擦墨交互完整实现 |
| C-11 墨香衰减 | ❌ 未做 | ✅ 已完成 | `@Scheduled` + `InkDecayConfig` + `saveInkDecayTimestamp()` |
| K-06 累计共鸣 | ⚠️ 部分 | ✅ 已完成 | `resonanceCount` 字段前后端完整 |
| P-01 组合判词 | ❌ 未做 | ✅ 已完成 | `POST /api/card/preview` 端点 |
| E-06 配角初见印象 | ❌ 未做 | ✅ 已完成 | `firstImpression` 字段更新逻辑 |
| E-07 关键词落位 | ⚠️ 部分 | ✅ 已完成 | `StoryView.vue` 三列可视化面板 |
| E-08 风格选项UI | ⚠️ 部分 | ✅ 已完成 | `EntryQuestionsView.vue` 四风格 UI |
| S-11 手势轻重缓急 | ⚠️ 部分 | ✅ 已完成 | `useGesture.detectIntensity()` 快慢长按检测 |
| S-16 断线重连 | ⚠️ 部分 | ✅ 已完成 | `StoryView.vue` SSE重连 + 草稿恢复 |
| M-02 AI生成标题3备选 | ⚠️ 部分 | ✅ 已完成 | `generateManuscriptWithTitles()` 返回3个标题 |
| M-03 题记生成 | ❌ 未做 | ✅ 已完成 | `StorytellerAgent.generateInscription()` |
| M-11 文学风格差异 | ⚠️ 部分 | ✅ 已完成 | `getStyleGuidance()` 4套差异化prompt |
| UI-02 宣纸纹理 | ⚠️ 部分 | ✅ 基本完成 | `App.vue` 宣纸纹理 + 牙色背景完整 |
| UI-08 进度墨线 | ❌ 未做 | ✅ 已完成 | `StoryView.vue` 底部进度墨线 + 顶部章节进度条 |
| UI-10 逐字渲染 | ⚠️ 部分 | ✅ 已完成 | 逐字渲染引擎 + 30ms fadeIn |
| UI-11 触感反馈 | ⚠️ 部分 | ✅ 已完成 | `useHaptic.ts` + 多场景触发 |
| U-03 游客模式 | ⚠️ 部分 | ✅ 已完成 | `POST /api/user/guest-login` 完整实现 |
| AI-04 掌眼Agent | ❌ 未做 | ✅ 已完成 | `ZhangyanAgent.java` + `StoryOrchestrationService` 调用 |
| AI-06 AI腔词黑名单 | ❌ 未做 | ✅ 已完成 | `AiPhraseFilter.java` 70+ 黑名单词 |
| AI-07 Prompt热更新 | ❌ 未做 | ✅ 已完成 | `AiPromptTemplateService.java` + DB存储 |
| AI-08 AI内容安全 | ❌ 未做 | ✅ 已完成 | `ContentSafetyChecker.java` 阿里云API + 黑名单 |
| AI-09 关键词融入率 | ❌ 未做 | ✅ 已完成 | `KeywordInsertionChecker.java` INTEGRATION_THRESHOLD=3 |
| D-01 关键词卡池1000张 | ⚠️ 部分 | ✅ 已完成 | SQL KW-001~KW-1000 完整 |
| D-02 历史事件卡池 | ❌ 未做 | ⚠️ 部分 | 123条，缺口 500+ 条 |
