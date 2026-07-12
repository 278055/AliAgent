# P1 集成加固设计

## 目标

补齐 P1 服务骨架的服务间认证拒绝路径、标准启动方式、数据库 profile 验证和前端构建隔离，不修改聊天、RAG 或售后业务逻辑。

## 服务 JWT

本地环境使用必填的 `SERVICE_JWT_SECRET` 环境变量和 HS256。Gateway 在代理下游请求时签发有效期不超过五分钟的 JWT，并写入 `X-Service-Authorization: Bearer <token>`。令牌包含 `caller`、`aud`、`scopes`、`iat`、`exp`、`jti`。

业务服务通过共享安全组件校验签名、过期时间、目标 audience 和路由 scope。除 `/api/v1/health` 外，内部端点默认需要服务 JWT。失败响应为 HTTP 401 和 `{ "code": "AUTH-401-001", "message": "服务认证无效", "requestId": "..." }`。Gateway 的外部入口不要求服务 JWT。

## 构建与运行

各服务 POM 统一声明 `spring-boot-maven-plugin`，使 `mvn spring-boot:run` 及可执行 jar 成为标准启动方式。服务的健康测试继续使用 MockMvc，并增加安全拒绝路径测试。

## 数据库验证

Compose PostgreSQL 是 P1 的隔离验证环境。使用每个服务自己的 `database` profile 环境变量启动，执行 Flyway 基线迁移。验证服务专属角色可以连接其数据库，且不能连接其他服务数据库。验证资源使用临时 Compose 卷，停止后清理容器和卷；不向项目原有 PostgreSQL 写入数据。

## 前端构建隔离

前端 Vite 构建输出改为 `frontend/dist`，避免 `npm run build` 改写根单体的已跟踪静态资源。前端静态资源同步由后续明确的发布步骤负责，不属于 P1 集成测试。

## 验收

运行 Maven 聚合测试、契约校验、Compose 配置与启动、六服务数据库 profile 健康检查、数据库权限检查、前端构建与 pnpm workspace 识别。安全测试至少覆盖缺失令牌、过期令牌、错误 audience、错误签名、scope 不足和有效令牌。
