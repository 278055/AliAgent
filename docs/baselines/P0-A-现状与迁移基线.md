# P0-A：现状与迁移基线

> 基线日期：2026-07-12  
> 盘点范围：当前 `AliAgent` 单体代码、配置和前端调用。本文是后续服务拆分的事实基线，不代表目标架构已落地。

## 1. 当前系统概览

当前仓库是单 Maven 模块的 Spring Boot 单体应用，前端是独立的 Vue 3 + Vite 项目。后端以 PostgreSQL + pgvector 保存会话、消息和知识库向量，以 Redis 保存登录 Token；模型与嵌入调用 DashScope。

| 项目 | 当前实现 |
|---|---|
| 后端入口 | `com.bn.aliagent.AliAgentApplication`，HTTP 端口 `8080` |
| 前端入口 | `frontend/`，Vite 开发端口 `3000`，`/api` 代理至 `8080` |
| 身份 | 本地 `users` 表 + Redis UUID Token，非 JWT、无角色和租户 |
| 会话 | `conversation`、`message` 两张 PostgreSQL 表，服务端 SSE 输出 |
| 知识库 | `document`、`document_chunk`，Tika 解析、DashScope Embedding、pgvector HNSW 检索 |
| 模型 | DashScope `qwen-plus`，Embedding 为 `text-embedding-v3`、1024 维 |
| 现有边界 | 单体内 Controller / Service / RAG Pipeline 分层；尚无 Gateway、服务注册、消息队列、对象存储、Flyway、契约目录或 `mall` 模块 |

## 2. API 基线

除登录、注册与健康检查外，`/api/**` 均由 `TokenInterceptor` 校验 `Authorization: Bearer {token}`。Token 在 Redis 中以 `token:{userId}` 和 `token_index:{token}` 双向索引保存，TTL 为 24 小时。

| 方法 | 路径 | 当前行为 | 认证 |
|---|---|---|---|
| POST | `/api/auth/register` | 注册本地用户 | 否 |
| POST | `/api/auth/login` | 登录并返回 Redis Token | 否 |
| PUT | `/api/auth/password` | 修改密码 | 是 |
| DELETE | `/api/auth/logout` | 删除当前用户 Token | 是 |
| GET | `/api/health` | 返回纯文本 `ok` | 否 |
| GET | `/api/health/stream` | 返回单条 SSE `ok` | 否 |
| GET | `/api/chat/stream?message=&conversationId=` | 流式聊天，返回 SSE 文本片段 | 是 |
| GET | `/api/chat?message=&conversationId=` | 同步聚合聊天结果，兼容接口 | 是 |
| GET | `/api/chat/conversations` | 查询未删除会话，置顶优先 | 是 |
| DELETE | `/api/chat/conversations/{id}` | 软删除会话 | 是 |
| PUT | `/api/chat/conversations/{id}/title` | 修改标题 | 是 |
| GET | `/api/chat/conversations/{id}/messages` | 查询会话消息及 metadata | 是 |
| PUT | `/api/chat/conversations/{id}/pin` | 切换置顶 | 是 |
| POST | `/api/rag/documents/upload` | 上传并摄入 pdf/doc/docx/md/txt | 是 |
| GET | `/api/rag/documents` | 查询文档列表 | 是 |
| GET | `/api/rag/documents/{id}` | 查询文档详情 | 是 |
| GET | `/api/rag/documents/{id}/chunks` | 分页预览文档分块 | 是 |
| DELETE | `/api/rag/documents/{id}` | 删除文档及其分块 | 是 |
| GET | `/api/rag/search?q=&topK=` | pgvector 相似度检索 | 是 |

### 当前 API 风险

- 资源仅按请求是否登录控制，不校验会话、文档的归属；当前数据模型没有 `tenant_id`、`user_id` 或角色字段。
- `GET /api/chat` 的同步响应是字符串，前端类型按 `Message` 处理；迁移时应以契约为准统一响应模型。
- SSE 通过 GET 查询参数提交用户输入，且未定义事件类型、`eventId`、断线恢复游标或幂等键。
- 登录 Token 是不透明 Redis Token；目标 `mall` JWT 与服务 JWT 不能直接复用该实现。

## 3. 数据库基线

数据源配置为 `jdbc:postgresql://localhost:5432/postgres`，默认用户 `postgres`。`schema.sql` 定义 pgvector 扩展和以下五张业务表；当前未配置 Flyway，且 `schema.sql` 是否自动执行取决于 Spring SQL 初始化配置与现有数据库状态，应在 P1 使用空库验证替代。

| 表 | 关键字段 | 当前归属 | 迁移目标 |
|---|---|---|---|
| `users` | `id`、`username`、明文 `password`、`email`、`deleted` | 现有本地认证 | 不迁移为业务事实；P2 起由 `mall` 身份、员工、角色与租户替代，历史本地用户仅作开发兼容 |
| `conversation` | `id`、`title`、`pinned`、`deleted`、时间戳 | 单体聊天 | P4 `conversation-service`；新增 `tenant_id`、参与者、会话状态、版本与审计字段 |
| `message` | `id`、`conversation_id`、`role`、`content`、`metadata` JSONB | 单体聊天 | P4 `conversation-service`；新增租户、发送者、消息状态、幂等键、可恢复流式草稿等 |
| `document` | `id`、`name`、`type`、`size`、`metadata` JSONB | 单体知识库 | P3 `knowledge-service`；对象内容迁至 MinIO，仅保留对象键、版本、审核和租户元数据 |
| `document_chunk` | `document_id`、`content`、`embedding vector(1024)`、页码、序号、metadata | 单体知识库 | P3 `knowledge-service`；新增 `tenant_id`、发布状态、知识版本与检索过滤字段 |

索引：`message` 按会话和创建时间索引；`document_chunk` 按文档、创建时间及 `embedding vector_cosine_ops` 的 HNSW 索引；`users` 按用户名和邮箱索引。

### 数据迁移边界

- 商品、订单、库存、物流、售后、退款、审批不在现有库中；P2 以后均由 `mall` 的 MySQL 数据库作为唯一事实来源。
- 会话、消息、文档和向量数据迁移到各服务自己的 PostgreSQL 数据库；禁止新服务跨库读取当前 `postgres` 库。
- 迁移前创建只读备份及行数校验。正式迁移需要保留旧表只读回退窗口，并以功能开关控制读写链路。
- 现有记录缺少租户归属。导入新服务时默认映射到单一开发租户；生产数据迁移必须先提供归属映射，不能猜测租户。

## 4. RAG 链路基线

### 文档摄入

1. 前端 `KnowledgeBase.vue` / `FileUploader.vue` 通过 `utils/api.ts` 的 XHR 上传文件。
2. `POST /api/rag/documents/upload` 调用 `DocumentIngestService`。
3. Tika 读取 pdf、doc、docx、md、txt；`TokenTextSplitter` 以 800 token 分块，最小块为 100 字符。
4. DashScope `EmbeddingModel` 为分块生成 1024 维向量。
5. 同一事务写入 `document` 和 `document_chunk`，分块 metadata 保存文档名、类型、页码、序号。

### 检索与回答

1. `ChatController` 将 SSE 或同步请求交给 `Agent`。
2. `Agent` 创建会话、读取历史、写入用户消息，并调用 `RAGPipeline`。
3. `RuleStrategy` 依据关键词决定是否检索；命中知识类关键词时才进入 RAG。
4. `NoOpQueryRewriter` 原样透传查询；`VectorRetriever` 使用 pgvector 余弦相似度检索，默认 Top-K 5、阈值 0.5；`NoOpReranker` 不重排。
5. `SimpleContextBuilder` 去重、拼接并截断至 2000 字符；`BasicPromptBuilder` 生成增强提示词。
6. DashScope 流式生成回复，片段直接写入 SSE；结束时将完整回复与 sources JSON 写入 `message.metadata`。
7. 前端通过 `fetch` + `ReadableStream` 解析 `data:` 行并渐进显示，历史消息从 metadata 恢复来源卡片。

### RAG 迁移结论

- P3 可复用的核心代码是 Tika 摄入、分块、Embedding、pgvector 检索及来源引用模型；应迁入 `knowledge-service`，而不是复制后长期双写。
- P5 可复用的编排概念是“检索策略 → 改写 → 召回 → 重排 → 上下文 → Prompt”；应由 `ai-orchestration-service` 经远程知识接口调用，不能直接访问知识库数据库。
- 当前没有租户检索过滤、发布审核、对象存储、混合召回、真实重排或知识版本控制；这些都是 P3 的新增能力，不应假定现有实现已经覆盖。

## 5. 前端与配置基线

### 前端调用点

| 区域 | 当前组件/模块 | 当前调用 |
|---|---|---|
| 认证 | `AuthShell.vue`、`utils/api.ts` | 注册、登录、改密、登出；Token 存入 local/sessionStorage |
| 会话 | `stores/index.ts`、`Sidebar.vue`、`ChatArea.vue` | 加载会话、加载消息、重命名、置顶、删除、发送 SSE 消息 |
| 知识库 | `KnowledgeBase.vue`、`FileUploader.vue`、`RAGSourceCard.vue` | 上传、文档列表、分块预览、删除、来源展示 |
| 健康检查 | `stores/index.ts` | 请求 `/api/health` 标记服务在线状态 |

前端无 Vue Router、Pinia 或 BFF；使用 `reactive()` 全局状态。P1 仅建立 pnpm 工作区骨架，不迁移上述页面。P4/P5 再以功能开关将管理端聊天调用切至 Gateway 和新服务。

### 配置与外部依赖

| 配置/依赖 | 当前值或来源 | 迁移处理 |
|---|---|---|
| `server.port` | `8080` | 保持单体兼容端口；P1 为 Gateway 和各服务分配新端口 |
| PostgreSQL | `localhost:5432/postgres` | P1 拆为独立数据库和独立账号 |
| Redis | `localhost:6600`，密码在 `application.yaml` | P1 改由 Compose 管理；密码移入环境变量或密钥管理 |
| DashScope 密钥 | `api-keys.yaml`，示例支持 `DASHSCOPE_API_KEY` | 不提交真实密钥；P5 通过模型适配器使用 |
| 前端代理 | Vite `/api` → `http://localhost:8080` | P1/P4 切换为 Gateway 地址，旧路径保留到回退窗口结束 |
| 构建产物 | `frontend` 构建到 `src/main/resources/static` | P1 后管理端与 Widget 独立构建，旧静态产物仅作兼容 |

## 6. 可复现启动与功能基线

### 前置条件

- JDK 17、Maven、Node.js、PostgreSQL（含 pgvector）和 Redis 已启动。
- PostgreSQL：`localhost:5432/postgres`；Redis：`localhost:6600`。实际口令以本地安全配置为准，不在命令或文档中回显。
- 从 `src/main/resources/api-keys.example.yaml` 创建本地未跟踪的 `api-keys.yaml`，或设置 `DASHSCOPE_API_KEY`。
- 现有数据库需要已执行 `src/main/resources/schema.sql`；不要用真实业务或个人数据做测试。

### 启动步骤

```powershell
# 仓库根目录：启动后端
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run

# 新终端：启动前端开发服务器
Set-Location frontend
D:\Java_Tools\nodejs\npm.cmd install
D:\Java_Tools\nodejs\npm.cmd run dev
```

访问 `http://localhost:3000` 验证前端开发模式，或在后端启动后访问 `http://localhost:8080` 验证已构建静态页面。健康检查为 `GET http://localhost:8080/api/health`，预期响应为 `ok`。

### 最小功能验收

1. 注册 `test-p0a-<时间戳>` 用户并登录，确认返回 Token。
2. 带 Token 调用健康、会话列表和流式聊天；确认创建会话、用户消息与助手消息。
3. 上传命名为 `rag-test-p0a-<时间戳>.txt` 的非敏感文本，确认文档和分块可查询。
4. 以包含“政策”“流程”等知识关键词的问题调用流式聊天，确认历史助手消息 metadata 含 `sources`。
5. 删除测试文档；删除测试会话关联消息后删除会话；删除测试用户。测试完成后使用项目约定 SQL 检查无遗留 `test-` / `rag-test-` 资源。

## 7. 新旧链路切换与回退清单

| 能力 | 当前链路 | 目标链路 | 切换前置条件 | 回退方式 |
|---|---|---|---|---|
| 身份与租户 | 本地用户 + Redis Token | `mall` JWT，经 Gateway 注入可信上下文 | P2 JWT、角色、租户与资源授权测试通过 | Gateway 功能开关回到单体 Token；不迁移或覆盖本地账号 |
| 知识摄入与检索 | 单体 Controller 直连 PostgreSQL | `knowledge-service` + MinIO + 独立 PostgreSQL | P3 数据校验、跨租户拒绝和远程接口契约通过 | 远程知识适配器开关关闭，恢复单体 RAG |
| 会话与实时输出 | 单体会话表 + SSE | `conversation-service` + SSE/WebSocket + Redis 路由 | P4 双实例、幂等和断线恢复测试通过 | Gateway 路由切回单体聊天；旧表保留只读/兼容写入窗口 |
| AI 编排 | `Agent` 直调 DashScope 与 RAG | `ai-orchestration-service` 调用知识和 mall 工具 | P5 降级、工具授权、来源引用与审计通过 | 编排路由回单体 Agent；禁止自动对业务事实作猜测 |
| 前端 | Vite 直连单体 `/api` | Gateway 路由的新 API | 对应服务稳定、契约兼容、灰度观察完成 | 前端功能开关恢复旧 API 基地址和页面调用 |

## 8. P1 前必须冻结的结论

- 当前单体在迁移期间继续运行，不在 P1 删除或重写现有 Controller、表和前端页面。
- `mall` 是商品、订单、库存、物流、售后、退款和审批事实的唯一所有者；AI 服务不得直接访问其数据库。
- 新服务从创建起必须拥有独立数据库、独立账号、Flyway 迁移和可信 `tenantId`；当前单体的无租户表不可直接视为目标模型。
- RAG 的来源引用是首里程碑必须保留的能力；模型故障或知识服务不可用时必须显式降级，不得虚构政策或业务事实。
- 当前明文密码、本地 Token、缺少资源授权和缺少追踪字段均为遗留兼容范围，不能作为 P1+ 新接口设计的基础。

