# P5-C 版本治理与降级上下文

工作目录 `D:\Java\code\AliAgent-worktrees\p5-orchestration-governance`，分支 `codex/p5-orchestration-governance`。只改 governance/quota/resilience/audit 和测试。冻结表已含版本、assignment、工具/模型审计。实现默认发布、租户灰度、执行版本固定、服务 JWT 保护的发布/回滚内部 API、配额并发优先级、Sentinel 开关、脱敏审计、模型失败安全转人工。无运营页面、无写业务。

## 新会话提示词

```text
负责 P5-C。进入 D:\Java\code\AliAgent-worktrees\p5-orchestration-governance，确认 codex/p5-orchestration-governance 且干净。读任务边界和本文件。只改 governance/quota/resilience/audit 和测试。复用冻结 Schema 实现默认发布、灰度、版本固定、JWT 内部发布/回滚、配额/并发、Sentinel、审计。模型失败安全转人工；不记录 JWT、身份快照、支付/地址；不改 Flyway/pom/yaml/contracts/其他服务。测试、diff、提交并报告。
```
