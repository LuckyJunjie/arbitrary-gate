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

## Docker 部署（推荐）

一键启动完整开发环境，包含前端、后端、MySQL 和 Redis。

### 前置条件

- Docker 20.10+
- Docker Compose v2.0+

### 快速启动

```bash
# 1. 复制环境变量配置
cp .env.example .env

# 2. 编辑 .env，填入必要的 API Key
vim .env

# 3. 一键启动所有服务
docker-compose up -d

# 4. 查看服务状态
docker-compose ps

# 5. 查看日志
docker-compose logs -f
```

### 服务地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端 | http://localhost:80 | Vue3 + Nginx |
| 后端 | http://localhost:8080 | Spring Boot |
| MySQL | localhost:3306 | MySQL 8.0 |
| Redis | localhost:6379 | Redis 7 |

### 手动构建

```bash
# 仅构建（不启动）
docker-compose build

# 前端单独构建
docker build -t arbitrary-gate-frontend .

# 后端单独构建（需要先在 src/backend 执行 mvn package）
cd src/backend && mvn package -DskipTests
docker build -t arbitrary-gate-backend ./src/backend
```

### 数据初始化

MySQL 容器首次启动时会自动执行 `docker/mysql/init/` 下的初始化脚本：
- `01-schema.sql` — 创建表结构
- `02-event_cards.sql` — 导入 123 张历史事件卡牌
- `03-keyword_cards.sql` — 导入 1000 张关键词卡牌
- `04-pay_order.sql` — 充值订单表

### 环境变量

主要环境变量说明（详见 `.env.example`）：

| 变量 | 说明 | 必填 |
|------|------|------|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 | 是 |
| `MYSQL_USER` | 应用数据库用户 | 是 |
| `MYSQL_PASSWORD` | 应用数据库密码 | 是 |
| `REDIS_PASSWORD` | Redis 密码 | 否 |
| `DASHSCOPE_API_KEY` | 阿里云通义千问 API Key | 是 |
| `CONTENT_SAFETY_API_KEY` | 阿里云内容安全 API Key | 否 |
| `WECHAT_APP_ID` | 微信 App ID | 否 |
| `WECHAT_APP_SECRET` | 微信 App Secret | 否 |

### 停止服务

```bash
# 停止并移除容器
docker-compose down

# 停止并移除容器和数据卷（慎用，会清除数据库数据）
docker-compose down -v
```

### 常用命令

```bash
# 进入后端容器（调试）
docker exec -it arbitrary-gate-backend sh

# 进入 MySQL
docker exec -it arbitrary-gate-mysql mysql -u root -p

# 重启后端
docker-compose restart backend

# 查看后端日志
docker-compose logs -f backend

# 验证 docker-compose 配置
docker-compose config
```

---

## 相关文档

- [产品设计 - 核心](./docs/产品设计-核心.md)
- [产品设计 - 玩法深化](./docs/产品设计-补充1-玩法深化.md)
- [UI 美工设计](./docs/产品设计-UI美工.md)
- [技术架构设计](./docs/技术架构.md)
- [卡池数据](./docs/卡池数据.md)
