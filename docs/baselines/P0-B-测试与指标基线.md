# P0-B 测试与指标基线

## 离线冒烟测试

当前基线只覆盖无需外部基础设施的健康探针契约：

- `GET /api/health` 返回 HTTP 200、`text/plain` 和 `ok`。
- `GET /api/health/stream` 返回 HTTP 200、`text/event-stream` 和单个 `ok`。
- 测试通过 `MockMvc` 直接装配 `HealthController`，不会启动 Spring 应用上下文，也不会连接 PostgreSQL、Redis 或 DashScope。

执行命令：

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd test
```

## 当前限制

现有聊天、会话、知识上传和 RAG 链路依赖 PostgreSQL、Redis、DashScope 与向量存储。P0 阶段不使用真实模型密钥或真实业务数据运行这些测试；待 P1 本地 Compose 环境和可替换模型适配层落地后，再补充端到端自动化测试。

## 指标采样方法

| 指标 | 采样位置 | 计算方式 | 基线记录 |
| --- | --- | --- | --- |
| 健康探针延迟 | 客户端请求 | 每次请求耗时，记录 p50/p95/p99 | 本地运行 100 次后记录到阶段验收报告 |
| 聊天端到端延迟 | 网关或前端请求日志 | 请求开始至 SSE 首 token、完整响应的耗时 | 区分普通问答、RAG、订单查询 |
| RAG Top-K 命中率 | 检索评测集 | 命中正确文档的问题数 / 总问题数 | 固定 `rag-test-` 文档和问题集 |
| 模型调用耗时与成本 | 模型适配层审计日志 | 记录模型、输入/输出 token、耗时；按模型单价离线汇总 | 不记录原始用户内容 |

## 数据约束与清理

- 所有端到端测试使用 `test-` 或 `rag-test-` 前缀的用户、会话和文档。
- 测试结束后先删除对应 `message`、`document_chunk`，再删除 `conversation`、`document` 和测试用户。
- 指标报告只保留聚合数据、请求标识和脱敏样例，不保留订单、个人信息或完整对话正文。
