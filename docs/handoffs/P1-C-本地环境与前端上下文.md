# P1-C 本地环境与前端工作区上下文

## 任务目标

建立 P1 本地 Docker Compose 基础设施，并将前端升级为 pnpm 工作区骨架。不得迁移现有聊天/RAG 页面或引入任何业务服务逻辑。

## 唯一目录所有权

- 仅允许修改或新建 `deploy/` 与 `frontend/`。
- 不允许修改根 `pom.xml`、`services/`、`contracts/`、`.github/`、旧后端 `src/`。
- 现有 `frontend/src/`、Vite 配置和可用脚本是兼容基线；不得删除页面、静态资源或现有功能。

## 基础设施要求

Compose 必须提供 MySQL、PostgreSQL（含 pgvector）、Redis、RabbitMQ、Nacos、MinIO，并通过环境变量或 `.env.example` 注入口令；不得提交真实密钥。

PostgreSQL 初始化必须创建：`conversation_db`、`orchestration_db`、`knowledge_db`、`evaluation_db`、`insight_db`，并为每个库创建独立登录账号与仅所属数据库权限。`knowledge_db` 启用 `vector` 扩展。MySQL 预留 `mall_db`，不导入 `mall` 源码或业务表。

端口必须集中记录在 `deploy/README.md`；选择不与当前 8080、3000、5432、6600 冲突的宿主机端口，服务代码不得依赖这些端口。

## 前端工作区要求

- 使用 `pnpm-workspace.yaml` 建立 `apps/*` 与 `packages/*` 工作区。
- 创建空骨架：`apps/aliagent-admin`、`apps/widget-playground`、`packages/chat-widget`、`packages/api-client`、`packages/ui`、`packages/shared`。
- 根 `frontend/package.json` 继续保留现有 `dev`、`build` 与类型检查兼容脚本；新增 pnpm workspace 脚本时不可破坏既有 npm 使用方式。
- 不迁移、不重写现有 `frontend/src/` 页面；只写 README 说明未来迁移位置和 P4 后的切换策略。

## 必须使用的技能

使用 `aliagent-database` 技能验证实际 PostgreSQL 状态：

1. 先通过 MCP 的只读 `SELECT` 检查五个数据库、角色和 `knowledge_db` 的 `vector` 扩展。
2. 若需要修正已启动环境中的 DDL，按该技能使用 Java JDBC，不使用 Python。
3. 不写入或清理现有 `postgres` 库中的用户数据；测试资源仅可使用 `test-` 前缀并在结束时清理。

## 验收与合并

- `docker compose -f deploy/docker-compose.yml config` 成功。
- 启动基础设施后，容器健康检查通过；五个数据库、独立账号和 `knowledge_db.vector` 经只读查询验证。
- `pnpm` 工作区可识别全部包；现有 `npm run build` 仍可工作。
- 分支命名：`codex/p1-infra-frontend`。
- 合并顺序第三：P1-A、P1-B 合并后，使用本任务 Compose 配置完成所有服务注册、健康检查和 Flyway 集成验收。

## 必读文件

- `AGENTS.md`
- `docs/baselines/P1-冻结清单.md`
- `docs/baselines/P0-兼容性矩阵与切换回退.md`
- `contracts/standards/identity-and-context.md`
- `contracts/standards/api-and-event.md`
- `contracts/standards/feature-flags.md`
- `.agents/skills/aliagent-database/SKILL.md`
