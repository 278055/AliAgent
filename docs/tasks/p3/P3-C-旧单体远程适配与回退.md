# P3-C：旧单体远程知识适配、双跑与回退任务上下文

## 任务身份

- 工作目录：`D:\Java\code\AliAgent-worktrees\p3-monolith-adapter`
- 工作分支：`codex/p3-monolith-adapter`
- 阶段集成分支：`codex/integration-p3`
- 必须依赖：P3-A 与 P3-B 已按顺序合并到集成分支；不得在缺少远程检索契约的旧基线上猜测接口。

## 目标

保留旧单体 RAG 和现有接口，在 `feature.knowledge.remote-read` 默认关闭的前提下接入远程 `knowledge-service` 检索。支持可观测的双跑比较、按租户启用和立即回退到本地 RAG；不迁移会话或 AI 编排，不修改 mall 业务事实。

## 唯一目录所有权

本任务只可修改：

- `src/main/java/com/bn/aliagent/agent/Agent.java`
- `src/main/java/com/bn/aliagent/config/RAGConfig.java`
- `src/main/java/com/bn/aliagent/rag/**`
- `src/main/java/com/bn/aliagent/Controller/RAGController.java`
- `src/main/resources/application.yaml`
- `src/test/java/com/bn/aliagent/rag/**`
- `src/test/java/com/bn/aliagent/agent/**`

禁止修改：根 `pom.xml`、`contracts/**`、`deploy/**`、`services/**`、`mall/**`、`frontend/**`、单体数据表 `schema.sql`。新增依赖、契约变更和部署配置均为 P3-A/集成会话的集成请求。

## 实施边界

1. 基于 P3-B 的已发布契约实现远程知识客户端和本地 RAG 兼容映射，保留既有 `RagChunk`、引用输出和所有旧 `/api/rag/**` 接口。
2. 功能开关只能采用 `feature.knowledge.remote-read`：默认关闭；按租户白名单启用；关闭、超时、鉴权失败、协议错误或远程无依据时必须明确回退到本地 RAG，并记录原因。
3. 双跑模式必须以本地结果作为用户可见结果，异步/旁路请求远程服务，只记录脱敏后的 Top-K chunk ID、文档/版本标识、延迟、错误类别和重合度。不得双写，不得将完整文档内容或凭据写入日志。
4. 切换到远程读取后，记录本地与远程检索对比指标，支持按租户立即关闭开关恢复本地链路。旧上传、删除、检索和 Agent 接口都不得删除或改为强制远程。
5. 服务间调用传递短期服务 JWT 和可信上下文；客户端提交的 tenantId 不得被信任。没有可信上下文时拒绝远程调用而非猜测租户。

## 强制测试

- 开关关闭、按租户开启和紧急关闭回退测试。
- 远程成功、超时、5xx、未授权、无结果和响应解析失败均回退本地的测试。
- 双跑不改变用户可见本地结果的测试，以及引用兼容性测试。
- 验证旧 `/api/rag/**` 路由仍存在；不得对 `schema.sql`、旧 document/document_chunk 做迁移写入。

## 交付与集成顺序

完成后提交原子变更，报告提交 SHA、改动清单、开关行为、双跑指标字段、回退验证与未解决集成请求。集成会话最后合并 P3-C，并执行端到端迁移/回退验收。
