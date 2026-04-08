# 时光笺 · AGENTS.md

> 本文件供 OpenClaw Agent 学习使用，描述时光笺（任意门）项目的所有功能、开发流程、测试标准和组织架构。

---

## 一、项目概述

**时光笺（TimeNote / 任意门）** 是一款 AI 驱动的互动叙事游戏，让用户以"穿越者"身份进入历史现场，通过抽卡、选择、扮演，最终生成一部属于自己的短篇小说。

- **GitHub**: https://github.com/LuckyJunjie/arbitrary-gate
- **技术栈**: H5 (Vue3 + TypeScript) + 云开发 / Spring Boot
- **核心玩法**: 抽卡 → 入局 → 叙事选择 → 生成短篇小说
- **设计文档**: `docs/` 目录（产品设计/UI/技术架构）

---

## 二、设计文档索引

| 文档 | 内容 |
|------|------|
| `docs/产品设计-核心.md` | 产品核心理念、玩法、AI Agent设计 |
| `docs/产品设计-补充1-玩法深化.md` | 场景式乐趣深化（抽卡仪式感、入局三问、手势交互） |
| `docs/产品设计-UI美工.md` | 完整UI规范（书房主界面、墨池、卷轴、手稿、缺角船票） |
| `docs/技术架构.md` | H5优先架构（Vue3+云开发，完整数据库/API/AI Agent方案） |

---

## 三、技术架构速查

### 3.1 技术选型

**前端**: Vue3 + TypeScript + Vite + Pinia + Vue Router + GSAP  
**后端**: Spring Boot 3.x / 云开发 CloudBase  
**数据库**: MySQL / 云数据库  
**缓存**: Redis / 云缓存  
**AI**: 通义千问 / 文心一言 / GPT-4o-mini（文本生成）+ 通义万相 / SD（图片生成）  
**部署**: Vercel（前端）+ 云函数（后端）

### 3.2 核心模块

```
时光笺
├── 前端模块
│   ├── 墨池抽卡（CSS动画 + Canvas）
│   ├── 卷轴阅读（竖向排版 + 手势交互）
│   ├── 卡匣管理（多宝格卡片墙）
│   ├── 书架管理（时光轴 + 山河图）
│   └── 故事卡分享（H5 Canvas 生成缺角图）
└── 后端模块
    ├── 用户模块（微信登录、资产）
    ├── 卡牌模块（抽卡算法、保底机制）
    ├── 故事模块（状态机、AI生成、流式推送）
    └── 分享模块（缺角码、合券机制）
```

### 3.3 AI Agent 职责

| Agent | 职责 | Prompt要点 |
|-------|------|----------|
| **说书人** | 场景描写、叙事推进、小说成文 | 温和白描，禁止"宛如""无法言说" |
| **判官** | 生成选项、评估选择后果、计算偏离度 | 道德两难，无完美解 |
| **稗官** | 后日谈、配角命运判词 | 野史口吻 |
| **掌眼** | 文学质感检查 | 剔除AI腔 |
| **画师** | 关键词卡图、场景图生成 | 水墨淡彩+浮世绘版画感 |

---

## 四、开发流程（遵循 Smart Factory v3.1）

### 4.1 核心原则

1. **DB 为单一事实来源**：需求、任务、状态存于 Smart Factory 数据库
2. **一团队一需求**：每个团队同一时间只处理 1 个需求
3. **测试先行**：先写测试，再写功能，功能完成以测试通过为准
4. **DoD 验收**：所有功能必须通过 DoD 检查清单方可交付

### 4.2 阶段流程

```
Phase 1: 需求分析
  → 分析设计文档，拆解为具体需求
  → 写入 Smart Factory 数据库

Phase 2: 任务分发
  → 从 DB 读取待办需求
  → 分析任务依赖
  → 分配给各开发 Team

Phase 3: 开发执行
  → 从 Redis Stream 读取任务
  → TDD 方式开发（先写测试）
  → 本地验证测试通过

Phase 4: Code Review + 合并
  → 提交 PR
  → 人工/自动 Code Review
  → 合并到主分支

Phase 5: 验收
  → 运行完整测试套件
  → DoD 检查清单逐项确认
  → 更新任务状态为 done
```

---

## 五、Definition of Done（DoD）

**每个任务/需求完成前，必须逐项确认以下清单：**

### 5.1 功能完成标准

- [ ] 代码实现完整，逻辑正确
- [ ] 所有子任务状态为 `done`
- [ ] 功能已集成到主分支，无未合并的 feature branch

### 5.2 测试标准

- [ ] **单元测试**：核心逻辑（抽卡算法、AI Prompt 解析、状态机）有单元测试覆盖
- [ ] **集成测试**：关键流程（抽卡 → 故事开始 → 选择 → 生成手稿）端到端测试通过
- [ ] **UI 测试**：核心页面（墨池、卷轴、书架）手动测试通过，无 console error
- [ ] **回归测试**：所有已有功能不受本次变更影响

### 5.3 代码质量

- [ ] 代码符合项目编码规范（见下节）
- [ ] 无硬编码配置（配置外置）
- [ ] 无明显的性能问题（同步操作 < 200ms，AI生成流式响应）
- [ ] 敏感信息（API Key等）通过环境变量注入，不提交到代码库

### 5.4 文档更新

- [ ] 核心接口有文档（接口描述、参数说明、返回值）
- [ ] 复杂度 > 30 行的函数有注释说明
- [ ] API 变更已更新到接口文档
- [ ] `docs/CHANGELOG.md` 已记录本次变更

### 5.5 Git 规范

- [ ] 提交信息符合规范（feat/fix/test/docs/refactor/chore）
- [ ] 每个功能独立的 commit（便于回滚）
- [ ] 已推送到远程仓库

### 5.6 安全

- [ ] 用户输入已做校验（防注入、XSS）
- [ ] AI 生成内容已做审核（防止违规输出）
- [ ] 分享码等敏感信息已做随机化

---

## 六、测试标准

### 6.1 测试金字塔

```
        /\
       /  \      E2E 测试（少量关键路径）
      /----\
     /      \    集成测试（核心流程）
    /--------\
   /          \  单元测试（核心逻辑）
  /____________\
```

### 6.2 测试覆盖要求

| 测试类型 | 覆盖目标 | 工具 |
|---------|---------|------|
| **单元测试** | 抽卡算法、保底机制、AI Prompt 解析、状态机 | Vitest / Jest |
| **集成测试** | 用户登录流程、故事完整生命周期、分享合券 | Playwright / Cypress |
| **UI 组件测试** | 墨池动画、卷轴手势、卡片展示 | Vue Test Utils + @vue/testing-library |
| **E2E 测试** | 完整故事流程（抽卡→入局→叙事→生成→分享） | Playwright |
| **AI 生成质量测试** | Prompt 输出合规性、关键词融入率 | 自动化 + 人工抽检 |

### 6.3 关键测试用例

**抽卡模块：**
- [ ] 每日免费次数正确扣减
- [ ] 保底机制：连续9次未出奇品，第10次必出
- [ ] 保底机制：连续30次未出绝品，第31次必出
- [ ] 墨晶消耗正确
- [ ] 重复抽卡不会获得重复 user_card 记录

**故事模块：**
- [ ] 入局三问答案正确保存
- [ ] 关键词共鸣值正确累加
- [ ] 历史偏离度正确计算
- [ ] 章节选项生成后用户选择能正确推进
- [ ] 故事完结后手稿正确生成
- [ ] 流式生成断线重连后内容不丢失

**分享模块：**
- [ ] 分享码唯一且不可枚举
- [ ] 合券验证逻辑正确
- [ ] 缺角图片生成正确

**UI 动画：**
- [ ] 墨池涟漪动画流畅（60fps）
- [ ] 卡片浮出动画无卡顿
- [ ] 卷轴手势滑动流畅
- [ ] 涟漪波纹扩散动画正常

### 6.4 测试执行命令

```bash
# 前端测试
cd frontend
npm run test          # 单元测试 + 组件测试
npm run test:e2e     # E2E 测试（需启动 dev server）
npm run test:coverage # 覆盖率报告

# 后端测试
cd backend
./gradlew test        # 单元测试
./gradlew integrationTest  # 集成测试

# 完整测试（CI 模式）
npm run test:ci
```

### 6.5 AI 生成质量标准

| 指标 | 目标值 | 检测方法 |
|------|--------|---------|
| 关键词融入率 | ≥ 3个关键词在正文中出现 | NLP 关键词匹配 |
| AI 腔词检出率 | 0%（禁止"宛如""仿佛""无法言说"） | 关键词黑名单过滤 |
| 故事完整性 | 有头有尾，结局明确 | 人工抽检 |
| 字数达标 | 3000-8000字 | 自动字数统计 |

---

## 七、编码规范

### 7.1 Git 提交规范

```
<type>(<scope>): <subject>

feat(card): 实现墨池抽卡动画
fix(story): 修复卷轴滑动丢失进度问题
test(card): 新增抽卡保底机制单元测试
docs(api): 更新AI Agent接口文档
refactor(share): 重构分享码生成逻辑
chore(deps): 升级Vue3到3.4
```

### 7.2 分支命名

```
feature/<feature-name>      # 新功能
bugfix/<bug-name>           # Bug修复
hotfix/<critical-fix>        # 紧急修复
release/v<version>           # 发布分支
docs/<doc-name>             # 文档更新
```

### 7.3 代码风格

**前端 (Vue3 + TypeScript):**
- 使用 Composition API + `<script setup>`
- Props 必须有 TypeScript 类型定义
- 组件文件名：`PascalCase.vue`
- 工具函数：`camelCase.ts`

**后端 (Java/Kotlin):**
- 类名：`PascalCase`
- 方法名/变量名：`camelCase`
- 常量：`UPPER_SNAKE_CASE`

---

## 八、组织架构

### 8.1 团队角色

| Agent | 角色 | 职责 |
|-------|------|------|
| **Apollo** | 产品经理 | 需求分析、PRD 撰写、优先级排序 |
| **Athena** | 架构师 | 技术方案设计、技术选型、代码审查 |
| **Hermes** | 前端开发 | Vue3 页面、动画、交互实现 |
| **Cerberus** | 测试工程 | 单元/集成测试、自动化测试框架 |
| **Jarvis** | 统筹协调 | 任务分发、进度跟踪、汇报 |

### 8.2 工作目录结构

```
arbitrary-gate/
├── docs/                      # 设计文档（不可修改来源）
├── src/                       # 源代码
│   ├── frontend/              # Vue3 前端
│   │   ├── views/             # 页面组件
│   │   ├── components/        # 通用组件
│   │   ├── composables/      # 组合式逻辑
│   │   ├── stores/          # Pinia 状态管理
│   │   ├── services/         # API 调用层
│   │   └── utils/           # 工具函数
│   └── backend/              # Spring Boot 后端（可选）
│       ├── controller/
│       ├── service/
│       ├── repository/
│       └── ai/
├── tests/                    # 测试代码
│   ├── unit/
│   ├── integration/
│   └── e2e/
├── work/                     # 开发工作区
│   ├── shared/               # 共享文档/数据
│   │   ├── requirements/     # 需求分析文档
│   │   └── docs/           # 团队内部文档
│   ├── athena/               # 架构师输出
│   ├── hermes/               # 前端开发输出
│   └── cerberus/             # 测试工程输出
└── README.md
```

---

## 九、需求优先级

### P0（MVP 必须）
- [ ] 墨池抽卡（基础动画 + 抽卡逻辑）
- [ ] 卡匣展示（卡片墙 + 分类筛选）
- [ ] 故事阅读界面（卷轴竖排 + 基本翻页）
- [ ] AI 接入（说书人 + 判官 Agent）
- [ ] 故事生成（完整生命周期）

### P1（核心体验）
- [ ] 入局三问定制
- [ ] 手势选择交互
- [ ] 关键词共鸣可视化
- [ ] 涟漪波纹动画
- [ ] 手稿生成（含朱批）
- [ ] 书架管理

### P2（社交传播）
- [ ] 分享码 + 缺角故事卡
- [ ] 合券机制
- [ ] 时光轴视图
- [ ] 山河图视图

### P3（体验增强）
- [ ] 墨香标记渐淡
- [ ] 组合预览判词
- [ ] AI 画师（卡面图生成）
- [ ] 稀有组合成就

---

## 十、Smart Factory 集成

### 10.1 任务分发

使用 Smart Factory 的 Redis Stream 进行任务分发：

```bash
# 查看任务队列
redis-cli XRANGE smartfactory:stream:tasks - + COUNT 5

# 查看消费者组状态
redis-cli XINFO GROUPS smartfactory:stream:tasks
```

### 10.2 任务格式

```json
{
  "taskId": "AG-001-hermes",
  "requirementId": "REQ-001",
  "projectName": "arbitrary-gate",
  "title": "实现墨池抽卡动画",
  "assignee": "hermes",
  "priority": "P0",
  "dependsOn": [],
  "callbackStream": "smartfactory:stream:results"
}
```

### 10.3 结果汇报

```python
import redis
from datetime import datetime

REDIS_CLIENT = redis.from_url("redis://localhost:6379", decode_responses=True)

REDIS_CLIENT.xadd("smartfactory:stream:results", {
    "task_id": task_id,
    "agent": "hermes",
    "status": "completed",
    "output_path": f"work/hermes/output/{task_id}_report.md",
    "timestamp": datetime.now().isoformat()
})
```

---

*时光笺 AGENTS.md - 遵循 Smart Factory v3.1 开发规范*
