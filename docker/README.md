# Docker 部署指南

本项目使用 Docker Compose 一键启动完整开发环境，包含 MySQL、Redis、Spring Boot 后端和 Vue3 前端。

## 快速开始

### 1. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，填入真实的密码和 API Key
```

### 2. 一键启动

```bash
docker-compose up -d
```

等待所有服务健康检查通过后即可访问。

## 服务地址

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 | 80 | Vue3 + Nginx |
| 后端 | 8080 | Spring Boot API |
| MySQL | 3306 | MySQL 8.0 |
| Redis | 6379 | Redis 7 |

## 环境变量说明

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `MYSQL_ROOT_PASSWORD` | (必填) | MySQL root 密码 |
| `MYSQL_USER` | appuser | MySQL 应用用户名 |
| `MYSQL_PASSWORD` | (必填) | MySQL 应用密码 |
| `MYSQL_PORT` | 3306 | MySQL 端口 |
| `REDIS_PASSWORD` | (可选) | Redis 密码（不填则无密码） |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `BACKEND_PORT` | 8080 | 后端暴露端口 |
| `FRONTEND_PORT` | 80 | 前端暴露端口 |
| `DASHSCOPE_API_KEY` | (必填) | 阿里云 DashScope API Key |
| `CONTENT_SAFETY_ENABLED` | true | 内容安全检查开关 |
| `CONTENT_SAFETY_API_KEY` | (可选) | 内容安全 API Key |
| `WECHAT_APP_ID` | (可选) | 微信 App ID |
| `WECHAT_APP_SECRET` | (可选) | 微信 App Secret |

## 数据库初始化

MySQL 容器首次启动时会自动执行 `docker/mysql/init/` 目录下的初始化脚本：

- `01-schema.sql` — 建表语句
- `02-event_cards.sql` — **467 条历史事件卡牌数据**
- `03-keyword_cards.sql` — 关键词卡牌数据
- `04-pay_order.sql` — 支付订单表结构

> 💡 容器首次启动时自动初始化，再次启动不会重复执行。如需重新初始化，请先删除 MySQL 数据卷：
> ```bash
> docker-compose down -v   # 删除数据卷
> docker-compose up -d    # 重新启动
> ```

## 常用命令

### 停止服务

```bash
docker-compose down
```

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看指定服务日志
docker-compose logs -f backend
docker-compose logs -f mysql
docker-compose logs -f redis
docker-compose logs -f frontend
```

### 重启服务

```bash
docker-compose restart backend
```

### 进入容器

```bash
docker exec -it arbitrary-gate-backend /bin/sh
docker exec -it arbitrary-gate-mysql mysql -u root -p
docker exec -it arbitrary-gate-redis redis-cli
```

## 健康检查

所有服务均配置了健康检查，可通过以下方式验证：

```bash
# 查看容器健康状态
docker-compose ps
```

- `backend`: `http://localhost:8080/actuator/health`
- `mysql`: `mysqladmin ping`
- `redis`: `redis-cli ping`

## 数据持久化

- MySQL 数据保存在 `mysql_data` 数据卷中
- Redis 数据保存在 `redis_data` 数据卷中

删除容器不会丢失数据，只有执行 `docker-compose down -v` 才会清除数据卷。
