# AliAgent 电商 AI 客服与运营协同平台

AliAgent 是一个面向电商场景的 AI 客服与运营协同平台。项目以 `macrozheng/mall` 作为商品、订单、库存、物流、售后和退款的业务事实来源；AI 平台负责会话、知识检索、受控工具调用、人工协同、评测和运营洞察。

当前已完成工程基础设施（P1）与 `mall` 接入基线（P2），正在进入知识服务拆分阶段。

## 架构原则

- `mall` 是电商业务事实的唯一来源，AI 服务禁止直连其数据库。
- 订单、库存、物流、价格和政策必须由受控 API 或知识引用提供依据，模型不得推测。
- 跨服务异步事实采用 Outbox/Inbox、幂等消费和最终一致性。
- 身份、HTTP、数据库、缓存、消息、文件和向量检索全链路携带可信 `tenantId`。
- 高风险售后操作必须经过规则判断、用户确认和人工审批。

## 当前能力

- 单仓库 Maven 多模块工程：Gateway、会话、AI 编排、知识、评测和洞察六个服务骨架。
- Docker Compose 本地依赖：MySQL、PostgreSQL + pgvector、Redis、RabbitMQ、Nacos、MinIO。
- 五个独立 PostgreSQL 服务数据库及独立账号；`knowledge_db` 启用 pgvector。
- `mall` 已通过 Git Subtree 引入，固定上游 `macrozheng/mall` v1.0.3。
- MEMBER/STAFF 统一短期 JWT，包含主体类型、租户、角色和权限声明。
- 面向 AI 平台的商品、订单、物流、库存、售后资格内部只读 API，包含服务身份与用户身份双重校验、资源归属校验和默认脱敏。
- `mall` Outbox、基础领域事件和 Mock 物流适配器。

## 仓库结构

```text
.
├── contracts/                 # OpenAPI、AsyncAPI、JSON Schema 与跨服务规范
├── deploy/                    # Docker Compose、本地环境模板和验证脚本
├── docs/                      # 架构、阶段计划、ADR、基线和协作指南
├── frontend/                  # Vue 前端与 pnpm 工作区骨架
├── mall/                      # macrozheng/mall Git Subtree（业务中台）
├── platform/                  # 跨服务公共安全组件
├── services/                  # Gateway 与五个 AI 领域服务
└── src/                       # 迁移期间保留的原 AliAgent 单体实现
```

## 技术栈

| 领域 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.4.5、Maven |
| AI | Spring AI Alibaba、DashScope |
| 数据 | PostgreSQL、pgvector、MySQL、MyBatis-Plus |
| 基础设施 | Redis、RabbitMQ、Nacos、MinIO、Docker Compose |
| 前端 | Vue 3、TypeScript、Vite、pnpm Workspace |
| 电商中台 | `macrozheng/mall` v1.0.3 |

## 快速开始

### 1. 启动本地基础设施

复制环境变量模板并替换示例口令。不要提交 `.env` 或任何真实密钥。

```powershell
Copy-Item deploy/.env.example deploy/.env
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d
docker compose -f deploy/docker-compose.yml --env-file deploy/.env ps
```

本地端口、数据库归属和停止命令见 [deploy/README.md](deploy/README.md)。

### 2. 运行平台模块测试

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd `
  -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml test
```

### 3. 构建前端

```powershell
Set-Location frontend
npm run build
```

### 4. 启动 mall

`mall` 需要可用的 MySQL 与 Redis。本地配置只能放在未跟踪的文件或环境变量中。

```powershell
Set-Location mall
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd `
  -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml `
  -pl mall-portal -am test
```

`mall` 的上游版本、同步方式和启动基线见 [docs/P2-0-mall-Subtree与最小启动基线.md](docs/P2-0-mall-Subtree与最小启动基线.md)。

## 开发约定

- HTTP、事件、身份上下文和错误码遵守 [contracts/standards](contracts/standards/README.md) 的规范。
- 新服务必须拥有独立数据库、独立账号和 Flyway 迁移；禁止跨库查询。
- 测试资源统一使用 `test-` 或 `rag-test-` 前缀，并在测试完成后清理。
- 多会话开发必须使用独立分支和 Worktree，集成测试通过后才合入主分支。详见 [多会话 Git 工作树协作指南](docs/多会话Git工作树协作指南.md)。
- `mall` 上游更新通过 Git Subtree 进行，不直接覆盖上游模块。

## 文档

- [总体架构设计](docs/电商AI客服平台-总体架构设计.md)
- [分阶段实施计划](docs/电商AI客服平台-分阶段实施计划.md)
- [项目现状](docs/项目现状.md)
- [P1 冻结清单](docs/baselines/P1-冻结清单.md)
- [P2-0 mall Subtree 与最小启动基线](docs/P2-0-mall-Subtree与最小启动基线.md)
- [多会话 Git 工作树协作指南](docs/多会话Git工作树协作指南.md)

## 安全说明

仓库中不得提交 DashScope API Key、数据库口令、JWT 密钥、MinIO 密钥或生产数据。提交前请检查 `.env`、本地配置文件和测试日志是否包含敏感信息。

## 许可证

本项目使用 [Apache License 2.0](LICENSE)。`mall/` 保留其上游许可证与版权声明。
