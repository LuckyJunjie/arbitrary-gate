# 时光笺 · 任意门

> 一场可以走入的历史故事

一款基于 AI Agent 的互动历史叙事游戏，以秦末乱世为背景，让用户以"穿越者"身份亲历历史的关键抉择，最终生成一部属于自己的短篇小说。

**核心差异**：不是让你"观看"历史，而是让你在历史的裂缝里，拥有一具身体，一双眼睛，和几次选择的机会。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue3 + TypeScript + Vite + Pinia + Vitest + Playwright |
| 后端 | Spring Boot 3.3 + MyBatis-Plus + Spring AI Alibaba（通义千问）|
| 数据库 | MySQL + Redis + H2（开发） |
| AI | 通义千问 Qwen-Turbo（阿里云百炼） |
| 构建 | Vite + Maven |

## 快速开始

### 前端

```bash
cd ~/Projects/arbitrary-gate
npm install
npm run dev      # 开发服务器 http://localhost:5173
npm run build    # 构建生产版本
npm run test     # 单元测试（71个）
npm run test:e2e # E2E 测试（Playwright）
```

### 后端

```bash
cd ~/Projects/arbitrary-gate/src/backend
mvn spring-boot:run  # 需要 JDK 21+
# 访问 http://localhost:8080
```

---

## 核心功能

- [x] **墨池抽卡** — 关键词 + 历史事件，随机生成命运卡牌
- [x] **故事场景流式生成（SSE）** — AI 逐段输出，实时呈现历史叙事
- [x] **AI 判官选项评估** — 每次抉择由 AI 判断走向与代价
- [x] **AI 稗官后日谈** — 故事结束后生成"后人记述"，增添史官视角
- [x] **71 个单元测试通过** — 关键词共鸣、抽卡算法、历史偏差、Prompt 解析全覆盖

---

## 项目结构

```
arbitrary-gate/
├── src/
│   ├── frontend/          # Vue3 前端源码
│   │   ├── views/        # 页面视图
│   │   ├── components/   # 公共组件
│   │   ├── stores/       # Pinia 状态管理
│   │   ├── services/     # API 服务层
│   │   └── composables/  # 组合式函数
│   └── backend/          # Spring Boot 后端源码
│       └── com/arbitrarygate/
│           ├── controller/
│           ├── service/
│           ├── mapper/
│           └── model/
├── tests/
│   └── unit/             # Vitest 单元测试
├── docs/                  # 产品设计 & 技术架构文档
│   ├── 产品设计-核心.md
│   ├── 产品设计-UI美工.md
│   └── 技术架构.md
├── docs/                  # 产品设计 & 技术架构文档
├── index.html             # 前端入口
├── vite.config.ts
├── vitest.config.ts
├── playwright.config.ts
└── package.json
```

---

## 设计理念

**"历史的裂缝里，有什么在等着你？"**

每个人在历史书上读到那些著名战役或宫廷政变时，都曾想象过："如果当时我在场，我会怎么选？"

时光笺不给你一个已经写好的故事。它给你一个身份，一个乱世，和几个不得不做的决定。然后，由你来书写那段被遗忘的历史。

---

## 相关文档

- [产品设计 - 核心](./docs/产品设计-核心.md)
- [产品设计 - 玩法深化](./docs/产品设计-补充1-玩法深化.md)
- [UI 美工设计](./docs/产品设计-UI美工.md)
- [技术架构设计](./docs/技术架构.md)
- [卡池数据](./docs/卡池数据.md)
