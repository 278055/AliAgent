# P5-A 编排核心上下文

工作目录 `D:\Java\code\AliAgent-worktrees\p5-orchestration-core`，分支 `codex/p5-orchestration-core`，依赖 `codex/integration-p5` 冻结提交。只改 core/routing/workflow/messaging 和测试；必须阅读 `docs/tasks/p5/P5-阶段任务边界.md`。实现 v1/v2 消费、Inbox 幂等、执行状态机、规则优先/模型兜底、五条固定工作流和恢复。v1 无 generationId，只可等待/降级，绝不伪造回写 ID；已完成工具/模型绝不重放。使用 TDD、失败时 systematic-debugging、提交前 verification-before-completion。

## 新会话提示词

```text
负责 P5-A。进入 D:\Java\code\AliAgent-worktrees\p5-orchestration-core，确认 codex/p5-orchestration-core 和干净状态。阅读 docs/tasks/p5/P5-阶段任务边界.md、本文件、contracts 与 conversation-service 实码。只改 orchestration/core、routing、workflow、messaging 及测试。实现 v1/v2 消费、Inbox、状态机、规则优先路由、普通/RAG/订单/物流/转人工、重启恢复。v1 没有 generationId：记录不兼容/等待，禁止猜测；禁止重复工具/模型和任何写工具。运行 Maven、diff 检查，提交并报告 SHA、文件、结果、缺口。
```
