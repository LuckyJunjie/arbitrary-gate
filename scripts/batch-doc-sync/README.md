# 批量文档同步 (Batch Document Sync)

## 概述

时光笺游戏的配置文件批量同步工具，支持将 JSON/YAML 格式的游戏配置同步到数据库。

## 支持的文档类型

| 类型 | 文件位置 | 说明 |
|------|----------|------|
| 关键词卡 | `data/keyword_cards.json` | 1000个关键词卡配置 |
| 历史事件卡 | `data/event_cards.json` | 600+历史事件配置 |
| 成就配置 | `data/achievements.json` | 成就解锁条件配置 |
| AI提示词 | `data/ai_prompts.json` | 各Agent的Prompt模板 |

## 使用方法

```bash
# 全量同步
node sync.js --all

# 增量同步
node sync.js --incremental

# 指定类型同步
node sync.js --type keyword_cards
node sync.js --type event_cards
node sync.js --type achievements
node sync.js --type ai_prompts
```

## 配置

在 `.env` 中配置数据库连接：

```
DB_HOST=localhost
DB_PORT=3306
DB_USER=appuser
DB_PASSWORD=apppassword
DB_NAME=arbitrary_gate
```

## 数据格式

### 关键词卡 (keyword_cards.json)

```json
[
  {
    "id": "KC001",
    "name": "旧船票",
    "category": "器物",
    "rarity": "凡",
    "description": "一张褪色的船票，泛黄的纸边承载着远行的记忆",
    "weight": 10
  }
]
```

### 历史事件卡 (event_cards.json)

```json
[
  {
    "id": "EV001",
    "name": "巨鹿城·破釜沉舟",
    "dynasty": "秦末",
    "location": "巨鹿",
    "description": "项羽大败秦军的历史事件",
    "characters": ["项羽", "章邯", "王离"]
  }
]
```

### 成就配置 (achievements.json)

```json
[
  {
    "id": "ACH_001",
    "name": "物是人非",
    "description": "集齐三件相同类别的器物关键词",
    "condition": {
      "type": "keyword_combination",
      "category": "器物",
      "count": 3
    },
    "reward": {
      "type": "resonance_boost",
      "value": 10
    }
  }
]
```

## API 接口

后端提供以下同步接口：

```
POST /api/admin/batch-sync
Content-Type: application/json

{
  "type": "keyword_cards" | "event_cards" | "achievements" | "ai_prompts",
  "mode": "full" | "incremental",
  "data": [...]  // 可选，传入数据而非读取文件
}
```

响应：

```json
{
  "success": true,
  "synced": 100,
  "skipped": 5,
  "errors": []
}
```

## 开发说明

此脚本设计用于：
1. 开发环境中快速同步配置
2. 自动化部署时的数据初始化
3. 配置的版本化管理（配置文件纳入 Git）

---
_创建于 2026-04-13_