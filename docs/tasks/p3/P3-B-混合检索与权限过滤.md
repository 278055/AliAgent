# P3-B：混合检索、RRF 与权限过滤任务上下文

## 任务身份

- 工作目录：`D:\Java\code\AliAgent-worktrees\p3-knowledge-retrieval`
- 工作分支：`codex/p3-knowledge-retrieval`
- 阶段集成分支：`codex/integration-p3`
- 必须依赖：P3-A 已合并至集成分支，且其 OpenAPI、Flyway 模型和 Maven 依赖均已可用。

## 目标

实现只检索 `PUBLISHED` 知识版本的在线知识检索：固定采用 PostgreSQL 全文关键词召回和 pgvector 语义召回，使用 RRF 融合，并保留可插拔 Reranker。所有候选和结果均受可信租户、知识域、调用权限和版本过滤约束。

## 唯一目录所有权

本任务只可修改：

- `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/retrieval/**`
- `services/knowledge-service/src/test/java/com/bn/aliagent/knowledge/retrieval/**`

禁止修改：根 `pom.xml`、`contracts/**`、`deploy/**`、`services/knowledge-service/pom.xml`、`services/knowledge-service/src/main/resources/**`、P3-A 所有 `catalog`、`ingestion`、`storage` 包、旧单体 `src/**`、`mall/**`、`frontend/**`。发现 API、迁移或依赖缺口时，记录给 P3-A/集成会话，不得越权修复。

## 实施边界

1. 实现关键词检索端口，使用 PostgreSQL 全文检索；实现语义检索端口，使用 `embedding vector(1024)` 的余弦查询。两路查询在 SQL 层即带 `tenant_id`、知识域、权限和 `PUBLISHED` 版本条件。
2. 候选由固定 RRF 公式融合，按稳定的 chunk ID 作为并列排序兜底；避免先召回后在 Java 内存过滤导致跨租户泄露。
3. 定义 `Reranker` SPI 和默认 No-Op 实现。当前不得接入真实模型；后续 DashScope Reranker 只能以新实现替换该 SPI。
4. 实现检索服务与 P3-A 已发布的检索 OpenAPI 对应的 HTTP 层，返回来源文档、知识版本、切片 ID、分数和必要的引用元数据。无候选或低置信时返回可区分的无依据结果，不编造依据。
5. 仅消费可信请求上下文；请求中出现的 `tenantId` 不可覆盖上下文。权限过滤由服务端可验证的权限快照/知识域授权执行。

## 强制测试

- 每路召回、RRF 分数与稳定排序的单测。
- `PUBLISHED`、`DRAFT`、`RETIRED` 版本可见性测试。
- 跨租户、跨知识域、无权限和伪造请求 tenantId 的拒绝测试。
- 1024 维向量参数验证，以及默认 Reranker 与替换实现的契约测试。
- 完成后报告精确 Maven 测试命令和结果；不创建或修改测试数据库数据，除非 P3-A 提供的受控集成测试环境可用。

## 交付与集成顺序

提交前检查修改范围仅在本任务目录。交付提交 SHA、接口实现说明、测试结果及对 P3-C 的检索响应样例。集成会话应在 P3-A 后合并本分支；P3-C 仅在此后接入远程适配器。
