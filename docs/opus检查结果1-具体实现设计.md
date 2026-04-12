下面是所有 **未实现（❌）和部分实现（⚠️）** 的需求功能点的具体实现细节：

---

## 一、用户模块

### U-02 手机号一键登录 ❌ P1
- **后端**：`UserController` 新增 `POST /api/user/phone-login`，接收手机号 + 验证码，调用阿里云 SMS 发验证码，校验后调用 `StpUtil.login(userId)` 签发 token
- **前端**：新增 `PhoneLoginView.vue`，输入手机号 → 获取验证码 → 提交登录
- **数据库**：`user` 表新增 `phone VARCHAR(20)` 字段

### U-03 游客模式正式方案 ⚠️ P1
- **后端**：`POST /api/user/guest-login`，生成 UUID 作为 guest_open_id，创建临时用户（标记 `is_guest=1`），签发 token
- **前端**：首次打开 App 时自动调用 guest-login，无需用户操作；后续微信登录时合并游客数据到正式账号
- **限制**：游客每日免费抽 1 次（正式用户 3 次），不能分享

### U-05 墨晶充值/购买 ❌ P2
- **后端**：`POST /api/pay/create-order`（创建订单）、`POST /api/pay/wx-callback`（微信支付回调），订单表 `pay_order`（order_no, user_id, amount, ink_stone_count, status, wx_trade_no）
- **前端**：新增 `ShopView.vue`，展示墨晶套餐（10晶/50晶/200晶），调用微信 JSAPI 支付
- **安全**：回调验签 + 幂等防重

### U-07 设置页面 ❌ P2
- **前端**：实现 `/settings` 路由对应的 `SettingsView.vue`，包含：清除缓存、关于我们、用户协议、退出登录、音效开关

---

## 二、抽卡模块

### C-02 事件卡抽取完善 ⚠️ P0
- **数据**：`event_card` 表导入 600+ 历史事件数据，字段：card_no, title, dynasty, year, location, description, rarity, image_url
- **后端**：`CardService.drawEventCard()` 复用 `DrawAlgorithm`，事件卡独立保底计数器（GuaranteeState key 加 `event:` 前缀）
- **前端**：`PoolView.vue` 新增"事件卡池"tab 切换，复用抽卡动画

### C-07 手牌上限检查 ✅ P1
- **后端**：`CardService.drawKeywordCard()` 开头增加检查 `SELECT COUNT(*) FROM user_card WHERE user_id=? AND card_type=1`，超 9 张返回错误码 `CARD_LIMIT_REACHED`
- **前端**：提示"卡匣已满，请先使用或回炉卡牌"

### C-08 墨迹占卜（今日运势） ❌ P1
- **后端**：`GET /api/card/fortune`，根据当天日期 seed + 用户 ID 哈希，从预置的 30 条运势文案中选取一条（如"今日墨色偏青，似有旧物来寻"），附带暗示的 category（器物/职人/风物/情绪/称谓）
- **前端**：`PoolView.vue` 墨池上方用烟灰色 `方正清刻本悦宋` 字体展示，opacity 0.5，3 秒 fadeIn 动画
- **数据**：新建 `fortune_text` 配置表，或 JSON 配置文件存 30+ 条文案

### C-09 残片拼接交互 ✅ P1
- **前端**：`InkPool.vue` 改造抽卡流程：
  1. 点击后卡片 1/5 露出（clip-path 遮罩）
  2. 监听 touch move 事件，使用 Canvas `destination-out` 混合模式做涂抹遮罩
  3. 擦除面积 > 80% 时触发卡片完整显现动画
  4. 墨汁滴落回池的 CSS drop 动画（translateY + opacity）
- **交互**：提示文字"墨中有物，拂去墨迹"，`方正清刻本悦宋` 字体

### C-11 墨香渐淡（时间衰减） ✅ P1
- **后端**：定时任务（`@Scheduled(cron="0 0 0 * * ?")` 每日零点），扫描所有 `user_card`，`ink_fragrance = MAX(0, ink_fragrance - 1)`
- **前端**：`inkValueStore` 显示时根据 `ink_fragrance` 值控制卡片边缘 blur 滤镜强度（7=浓墨 → 0=无墨迹）

### C-12 陈卡回炉 ❌ P2
- **后端**：`POST /api/card/recycle`，参数 `cardId`。校验：每日限 1 次（Redis key `recycle:{userId}:{date}`），删除 user_card 记录，返还 1 次免费抽取机会（Redis incr `free_draw:{userId}:{date}`）
- **前端**：卡匣页长按卡片弹出"投入墨池回炉"确认框

### C-14 卡面图片生成（AI 画师） ⚠️ P3
- **后端**：`POST /api/ai/generate-card-image`，调用通义万相 API，prompt 由 `aiPainter.ts` 中已有的 `buildCardPrompt()` 逻辑迁移到后端
- **风格**：水墨淡彩 + 浮世绘版画感，分辨率 512×768
- **存储**：生成图片上传 OSS，URL 写入 `keyword_card.image_url`
- **缓存**：已生成的卡面不重复生成，检查 image_url 非空则跳过

---

## 三、卡匣模块

### K-06 累计共鸣次数显示 ✅ P1
- **前端**：`CardsView.vue` 卡片详情弹层中展示 `resonanceCount`，用赭石色小字 "共鸣 ×{n}"
- **后端**：确保 `GET /api/card/owned` 返回 `resonance_count` 字段

### K-07 墨迹晕染效果 ❌ P2
- **前端**：`Card.vue` 外层 wrapper 加 CSS `box-shadow` + `filter: blur()`，强度与 `inkFragrance` 绑定：
  - 7: `box-shadow: 0 0 12px rgba(44,44,42,0.6)` 
  - 4: `box-shadow: 0 0 6px rgba(44,44,42,0.3)`
  - 0: 无 shadow
- 动画：墨迹用 `radial-gradient` 模拟晕染，边缘不规则（SVG mask）

---

## 四、组合预览模块

### P-01 组合判词生成 ❌ P1
- **后端**：`POST /api/card/preview`，参数 `{ keywordIds: [1,2,3], eventId: 1 }`。调用 AI（说书人 Agent 轻量 prompt）：
  ```
  三张关键词：{name1}、{name2}、{name3}，历史事件：{eventTitle}。
  请用一句判词（20字以内）暗示这组卡可能产生的故事，用古文口吻。
  ```
  返回 `{ judgment: "江边渡口，有人把半生未说出口的话，折成一张旧船票。" }`
- **前端**：选卡完成后、点击"入局"前，展示判词浮层（竖排，`方正清刻本悦宋`，赭石色）

### P-02/P-03 稀有组合成就 ⚠️ P2
- **前端**：`achievementStore` 新增组合检测逻辑：
  - 三器物（category 全为 1）→ 解锁 `物是人非`
  - 三情绪（category 全为 4）→ 解锁 `百感交集`
  - 旧船票 + 渡口类事件 → 解锁 `离人`
- **后端**：`POST /api/achievement/unlock`，记录到 `user_achievement` 表

### P-04 三水意象彩蛋 ❌ P3
- **后端**：在 `StoryOrchestrationService.startNewStory()` 中检测三张关键词是否都带"水"标签（tag 包含 渡口/船/江/河/雨），若是则在说书人 prompt 尾部注入 `"故事中必须出现一场雨"`

---

## 五、入局模块

### E-06 配角初见·一句话印象 ✅ P1
- **后端**：`StorytellerAgent` 配角生成 prompt 增加约束：
  ```
  每个配角出场时，不要用上帝视角介绍身份。
  用主角的眼睛，描述此人"此刻的样子"，一句话，不超过30字。
  示例："瘦高个子，鞋上有泥，走路时右手一直攥着什么。"
  ```
  返回的 `story_character` 新增 `first_impression VARCHAR(128)` 字段
- **前端**：StoryView 章节开始时，配角名旁用烟灰色斜体展示一句话印象

### E-07 关键词落位可视化 ✅ P1
- **后端**：AI 生成开局时，说书人返回 JSON 标注三个关键词的角色：`{ "核心意象": "旧船票", "转折道具": "铜锁芯", "人物关联": "摆渡人" }`
- **前端**：入局页面底部用三列布局展示关键词落位，卡片下方标注角色名，泥金色连线

### E-08 风格选项 UI ✅ P1
- **前端**：`EntryQuestionsView.vue` 在提交三问答案前，增加风格选择步骤：四个选项卡"白描 / 江湖 / 笔记 / 话本"，各附一句示例文风
- **后端**：`submitEntryAnswers` 请求体增加 `style: number`（1-4），写入 `story.style`，说书人 prompt 根据 style 切换叙述口吻

---

## 六、叙事推进模块

### S-11 手势轻重缓急 ✅ P1
- **前端**：`useGesture.ts` 增强：
  - **慢速检测**：滑动耗时 > 800ms 判定为"悄悄"动作
  - **快速检测**：滑动耗时 < 200ms 判定为"紧急"动作
  - **长按检测**：按住 > 1500ms 判定为"用力"动作
  - 根据检测结果传递 `gestureIntensity: 'gentle' | 'urgent' | 'forceful'` 给后端，影响 AI 生成的场景描写语气

### S-13 关键词"显灵"特写 ❌ P2
- **后端**：`JudgeAgent` 评估选择时，若某关键词 `resonance >= 5`，返回 `keywordEnlightenment: { cardId, text: "一句显灵描写" }`
- **前端**：触发时全屏展示关键词卡面（灰暗变彩色 transition 1.5s），底部浮现显灵故事文字，配合磬音音效
- **数据**：`user_card.resonance_count` 累加，卡面增加小字记录"显灵于《故事名》第X章"

### S-14 配角偶遇支线 ❌ P2
- **后端**：`StoryOrchestrationService` 在章节间转场时，30% 概率触发偶遇事件。调用说书人 Agent 生成 50-80 字的偶遇场景 + 2 选项（搭话/装没看见）
- **前端**：StoryView 章节过渡动画后，弹出半屏偶遇浮层
- **数据**：影响 `story_character.fate_value`（搭话 +10，忽略 -5），影响后日谈内容

### S-16 断线重连 ⚠️ P1
- **前端**：`storyStore` 在 WebSocket 断开时启动 reconnect 定时器（指数退避 1s/2s/4s/8s，最多 5 次）；每次收到流式文本都 append 到 `localStorage` key `story_draft:{storyId}:{chapterNo}`；重连后对比服务端已生成长度，只请求增量
- **后端**：`GET /api/story/{id}/chapter/{no}/progress` 返回当前已生成文本长度，前端据此决定是否需要补全

---

## 七、落笔成书模块

### M-02 标题三选一 ⚠️ P0
- **后端**：`StoryOrchestrationService.finishStory()` 中，说书人 prompt 增加 `"请为这个故事生成3个备选标题，用JSON数组返回"`，解析后存入 `story.candidate_titles JSON`
- **前端**：`ManuscriptView.vue` 首次查看时弹出标题选择浮层，用户选定后 `POST /api/story/{id}/title` 更新

### M-03 题记生成 ✅ P1
- **后端**：finishStory 流程中增加一步，说书人 prompt：`"为本故事写一句题记，散文诗风格，不超过30字"`
- **数据**：`story_manuscript` 新增 `epigraph VARCHAR(128)` 字段
- **前端**：手稿页正文前，居中竖排展示题记，字号略大，赭石色

### M-11 文学风格输出差异 ⚠️ P1
- **后端**：说书人 Agent prompt 根据 `story.style` 值切换：
  - style=1 白描：`"用白描手法，惜字如金，不用任何形容词"`
  - style=2 江湖：`"用江湖话本口吻，可以夸张，可以热血"`
  - style=3 笔记：`"用笔记体，像纪晓岚《阅微草堂笔记》"`
  - style=4 话本：`"用话本口吻，可以有'看官''且说'等说书套语"`

### M-10 批注彩蛋 ❌ P3
- **后端**：稗官 Agent 生成批注时增加 prompt：`"其中1-2条批注可以打破第四面墙，对读者说话，或暗示'如果当时选了别的选项会怎样'"`
- **数据**：annotations JSON 中增加 `type: 'normal' | 'easter_egg'`，彩蛋批注前端用不同颜色（黛青色）

### M-12 掌眼 Agent ❌ P1
- **后端**：新建 `ZhangyanAgent.java`（掌眼），在 finishStory 流程中，说书人生成正文后、存入数据库前调用掌眼：
  ```
  prompt: "你是一位严苛的老编辑。检查以下文本，找出所有AI腔词汇（宛如、仿佛、无法言说、竟然、不禁、缓缓、轻轻），替换为更自然的表达。直接返回修改后的全文。"
  ```
  黑名单词：`["宛如", "仿佛", "无法言说", "不禁", "缓缓说道", "轻声说道", "目光中满是"]`
- **备选**：如果 AI 调用成本高，可用正则后处理替换

---

## 八、书架模块

### B-04 山河图视图完善 ⚠️ P2
- **前端**：`BookshelfView.vue` map 模式，引入一张古风中国地图 SVG（标注主要城市/关隘），根据故事的 `event_card.location` 在地图上放置标记点
- **交互**：点击标记点展示该地故事列表浮层
- **数据**：`event_card` 表需补充 `latitude DECIMAL(9,6), longitude DECIMAL(9,6)` 或预定义 `location_code` 映射坐标

### B-05 书架视觉升级 ❌ P2
- **前端**：grid 模式改造为书架木纹背景（CSS background-image），每本书用竖排 div 模拟书脊（高度按 `total_words / 1000` 缩放），题签用手写楷体，已完成的书脊底部加朱红印鉴 icon

---

## 九、分享模块

### SH-01 缺角故事卡生成 ⚠️ P2
- **后端**：`POST /api/share/create`，生成分享码（`IdGenerator.generateShareCode()`），调用 Canvas/Sharp 库生成图片：
  - 正面：AI 场景图 + 标题 + 判词 + 右下角物理缺角（clip-path）
  - 背面：正文第一段 + 缺角处关键词图标
- **前端**：`ShareView.vue` 使用 html2canvas 在前端生成分享图，缺角用 CSS clip-path polygon 实现
- **存储**：生成图片上传 OSS，返回图片 URL

### SH-02 分享码 ⚠️ P2
- **后端**：`IdGenerator` 生成 8 位随机字母数字码（排除易混淆字符 0OIl），写入 `story_share.share_code`
- **安全**：分享码不可枚举（足够随机 + 速率限制）

### SH-03 合券机制 ⚠️ P2
- **后端**：`POST /api/share/{code}/joint`，参数 `{ cardId: number }`
  1. 查 `story_share` 找到 `missing_corner_card_id`
  2. 校验请求者的 `user_card` 是否拥有该 card_id
  3. 匹配成功：双方各获得一张限定合券纪念卡 → 插入 `user_card`（card_type=3 合券纪念卡）
  4. 授予请求者对该故事的阅读权限 → 插入 `story_reader` 表
- **前端**：扫码/输入分享码 → 显示缺角卡 → 匹配展示动画（两半卡合拢）

### SH-04 合券纪念卡 ❌ P3
- **数据**：`special_card` 表（id, name, image_url, source_story_id, source_share_code）
- **前端**：卡匣新增"纪念卡"tab，展示合券纪念卡

### SH-05 微信 JSSDK 分享 ❌ P2
- **后端**：`GET /api/wx/js-config`，返回签名配置（appId, timestamp, nonceStr, signature）
- **前端**：引入微信 JSSDK，`wx.config()` 后调用 `wx.updateAppMessageShareData()` 和 `wx.updateTimelineShareData()`，分享标题/描述/图片/链接

---

## 十、UI/动效

### UI-06 窗格光影 ❌ P3
- **前端**：`HomeView.vue` 新增光影覆盖层 div，根据 `new Date().getHours()` 切换：
  - 6-11 点：`background: linear-gradient(135deg, rgba(74,107,107,0.1), transparent)`（黛青冷调）
  - 12-16 点：无光影
  - 17-19 点：`background: linear-gradient(135deg, rgba(139,94,60,0.15), transparent)`（赭石暖调）
  - 20-5 点：烛光效果（CSS animation flicker），关闭窗格影

### UI-07 卷轴天杆/地杆 ❌ P2
- **前端**：`StoryView.vue` 顶部和底部各加一个 `div.scroll-bar`，背景为木纹图片（`background-image`），高度 8px，圆角，阴影模拟立体感

### UI-08 进度墨线 ❌ P1
- **前端**：`StoryView.vue` 顶部 fixed 定位一条细线（2px），宽度 = `(currentChapter / totalChapters) * 100%`，颜色墨色 `#2C2C2A`，transition 0.5s

### UI-10 逐字渲染 ⚠️ P1
- **前端**：WebSocket 收到文本片段后，不直接 innerHTML，而是逐字 append 到 DOM，每字间隔 30ms（`setTimeout` 队列），配合 CSS `@keyframes fadeIn { from { opacity: 0 } to { opacity: 1 } }` 每个字 0.3s 淡入

### UI-11 触感反馈 ⚠️ P1
- **前端**：在 `useGesture.ts` 完成手势识别后调用 `navigator.vibrate([10])`（轻震 10ms）；抽卡成功时 `navigator.vibrate([15, 50, 15])`（双震）

---

## 十一、音效模块（全部 P3）

### A-01 ~ A-07 音效系统 ❌
- **前端**：新建 `composables/useSound.ts`：
  ```typescript
  // 预加载 AudioBuffer，用 Web Audio API 播放
  const sounds = {
    inkDrop: '/sounds/ink-drop.mp3',      // 抽卡水滴叮
    paperRub: '/sounds/paper-rub.mp3',    // 擦墨沙沙
    windChime: '/sounds/wind-chime.mp3',  // 卡片显现风铃
    brushTap: '/sounds/brush-tap.mp3',    // 选择舔笔声
    bell: '/sounds/bell.mp3',             // 章节编钟
    chime: '/sounds/chime.mp3',           // 显灵磬音
    jade: '/sounds/jade-click.mp3',       // 合券玉击
  }
  ```
- **音量**：所有音效 volume ≤ 0.3（极轻微）
- **开关**：设置页 `soundEnabled` 存 localStorage，全局控制

---

## 十二、AI Agent 模块

### AI-04 掌眼 Agent ❌ P1
（见 M-12，同一功能）

### AI-06 AI 腔词黑名单过滤 ❌ P1
- **后端**：`common/util/AITextFilter.java`，使用正则匹配黑名单词表：
  ```java
  private static final List<String> BANNED_WORDS = List.of(
    "宛如", "仿佛", "无法言说", "不禁", "缓缓说道", 
    "轻声说道", "目光中满是", "心中一动", "似乎在诉说"
  );
  ```
  替换策略：直接删除或替换为空字符串，由掌眼 Agent 二次润色
- **调用点**：所有 AI 返回文本经过 `AITextFilter.filter(text)` 后再存库

### AI-07 Prompt 热更新 ❌ P1
- **数据库**：新建 `ai_prompt_template` 表（id, agent_name, prompt_key, prompt_text, version, updated_at）
- **后端**：各 Agent 启动时从 DB 加载 prompt，Redis 缓存 10 分钟 TTL；管理接口 `PUT /api/admin/prompt/{id}` 更新后清缓存
- **好处**：调整 prompt 不需要重新编译部署

### AI-08 AI 内容安全检测 ❌ P0
- **后端**：`common/util/ContentSafetyChecker.java`，调用阿里云内容安全 API（`green.cn-shanghai.aliyuncs.com`），检测 AI 输出文本是否包含色情/暴力/政治敏感内容
- **调用点**：说书人/稗官返回文本 → `ContentSafetyChecker.check(text)` → 不通过则重新生成（最多 3 次）→ 仍不通过则返回兜底文案
- **配置**：`CONTENT_SAFETY_API_KEY` 环境变量

### AI-09 关键词融入率检测 ❌ P1
- **后端**：`common/util/KeywordChecker.java`，故事完结生成手稿后，检查三个关键词在正文中出现次数，要求 ≥ 3 个关键词至少各出现 1 次
- **不通过**：在说书人 prompt 中追加强调 `"正文中必须自然地融入以下关键词：{kw1}、{kw2}、{kw3}"`，重新生成

---

## 十三、数据模块

### D-01 关键词卡池全量导入 ⚠️ P0
- **数据来源**：卡池数据.md 已定义 340+ 条，需补齐到 1000 条
- **实现**：编写 SQL 导入脚本 `scripts/import_keyword_cards.sql`，从 markdown 表格解析为 INSERT 语句
- **格式**：`INSERT INTO keyword_card (card_no, name, category, rarity, weight) VALUES ('KW-001', '旧船票', 1, 1, 100);`

### D-02 历史事件卡池 ❌ P0
- **数据**：整理 600+ 历史事件，按朝代分类（先秦/秦汉/三国/隋唐/宋元/明清/近代/虚构传说）
- **表结构**：`event_card` 表，含 dynasty, year, location, latitude, longitude, description, identity_options JSON（三种身份视角配置）
- **优先导入**：先导入设计文档提到的核心事件（巨鹿/赤壁/马嵬驿/陈桥驿/崖山/玄武门/断桥等）

---

## 十四、基础设施

### I-06 输入校验完善 ⚠️ P0
- **后端**：所有 Controller 请求体加 `@Valid` 注解，DTO 字段加 `@NotNull`, `@Size`, `@Min/@Max`
- **XSS**：添加 `XssFilter`（Spring Filter），对所有 request parameter 做 HTML 转义
- **SQL 注入**：MyBatis-Plus 已使用参数化查询，确认无 `${}` 拼接

### I-10 图片懒加载 ❌ P2
- **前端**：自定义 Vue 指令 `v-lazy-img`，使用 `IntersectionObserver` 监听元素进入视口后才设置 src

### I-11 微信 WebView 适配 ❌ P2
- **前端**：vite.config.ts 配置 `@vitejs/plugin-legacy`（polyfill）；index.html 加 `<meta>` 禁止缩放；CSS 加 `-webkit-overflow-scrolling: touch`

### I-09 Service Worker 离线缓存 ❌ P3
- **前端**：使用 `vite-plugin-pwa`，配置 precache 静态资源 + runtime cache 策略（CacheFirst 用于图片，NetworkFirst 用于 API）

### I-12 Docker 部署 ❌ P2
- 前端：`Dockerfile` multi-stage build（node build → nginx serve）
- 后端：`Dockerfile`（maven package → java -jar）
- `docker-compose.yml`：frontend + backend + mysql + redis 四容器编排

### I-13 CI/CD ❌ P2
- `.github/workflows/ci.yml`：push 触发 → npm test + mvn test → docker build → deploy

---

**总计 54 个待实现/完善功能点**，建议实施路线：

1. **第一批（P0 缺口）**：D-01 卡池导入 → D-02 事件卡池 → C-02 事件卡抽取 → AI-08 内容安全 → I-06 输入校验 → M-02 标题三选一
2. **第二批（P1 核心体验）**：C-08/C-09 抽卡仪式感 → E-06/E-08 入局深化 → M-12/AI-06 文学质感 → UI-08/UI-10 阅读体验 → S-16 断线重连
3. **第三批（P2 社交）**：SH-01~SH-05 分享合券全链路 → B-04/B-05 书架升级 → U-05 支付 → I-12 部署
4. **第四批（P3 增强）**：音效系统 → 窗格光影 → 彩蛋 → Service Worker