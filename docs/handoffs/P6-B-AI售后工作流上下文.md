# P6-B AI 售后工作流上下文

## 任务卡

- 工作目录：`D:\Java\code\AliAgent-worktrees\p6-ai-aftersale-workflow`
- 工作分支：`codex/p6-ai-aftersale-workflow`
- 依赖提交：冻结提交由 `codex/integration-p6` 的 `docs(p6)` 提供。
- 允许修改：`services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/{aftersale,confirmation}/` 与对应测试。
- 禁止修改：`contracts/`、POM、YAML、迁移、mall、A/C 目录及 P5 已完成工作流。

## 真实接口与边界

P5 的 `RuleFirstIntentRouter` 对写意图只返回 `HUMAN_HANDOFF`，工作流仅有 GENERAL/RAG/ORDER/LOGISTICS/HANDOFF；P6 在新目录扩展售后意图、材料收集、确认卡和受控命令提交。conversation 的 `AIReplyRequested v2` 及 P5 orchestration execution 提供会话、消息、`generationId`、请求/追踪与授权快照关联基础。AI 只展示 mall API/事件返回的申请、审批、退款和 Saga 事实；不得直连 mall 数据库或生成成功事实。

## 状态、幂等与安全

确认卡状态映射到 `WAITING_USER_CONFIRMATION`；用户确认才发送 `AfterSaleConfirmed`，受控命令必须携带冻结字段。AI 不自行决定审批或退款，金额大于等于 `500.00`、高风险、重复退款疑似、订单主体/租户不匹配必须如实提示并交 mall 规则处理。相同 `requestId`/确认 `actionId` 重放只提交同一 `idempotencyKey`；调用超时只展示未知/处理中，绝不重发退款。

透传 `X-Request-Id`、`X-Trace-Id`、`X-Tenant-Id`、主体/角色/权限、授权快照和短期服务 JWT。mall 不可用时降级为不能确认业务事实的安全说明或人工转接，不能臆造订单、退款或审批结果；日志必须脱敏。

## 测试与交付

测试覆盖售后意图和多轮信息收集、确认前无写、重复确认、可信头透传、mall 故障无事实、权限/租户不匹配、状态查询展示与人工转接。运行 Maven、`git diff --check`、Compose 校验；提交 `feat(p6-b): add aftersale workflow`，报告 SHA、文件、验证、清理和集成请求。

## 必须使用的 Skill

`superpowers:using-git-worktrees`、`superpowers:brainstorming`、`superpowers:test-driven-development`、失败时 `superpowers:systematic-debugging`、提交前 `superpowers:verification-before-completion`；修改前 CodeGraph；查询 Spring/RabbitMQ/Sentinel 用 `context7`。

## 新会话提示词

```text
你负责 P6-B。进入 D:\Java\code\AliAgent-worktrees\p6-ai-aftersale-workflow，确认 codex/p6-ai-aftersale-workflow 和干净状态。阅读 AGENTS.md、docs/tasks/p6/P6-阶段任务边界.md、docs/handoffs/P6-B-AI售后工作流上下文.md、contracts/standards/aftersale-p6.md。只改 services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/{aftersale,confirmation}/ 与对应测试；禁止改 contracts、POM、YAML、迁移、mall、A/C 目录和 P5 已完成工作流。

先用 CodeGraph 查 P5 HUMAN_HANDOFF、AIReplyRequested v2、消息/执行模型和 mall 适配边界。实现售后意图、多轮材料收集、确认卡、用户确认、受控命令提交、状态查询和事实展示。AI 只能从 mall API 或事件取得事实，禁止直连 mall DB 或输出退款/取消/审批成功事实。确认前不得写；重放复用同一 idempotencyKey；超时显示未知并先查询。可信身份、租户、授权快照和服务 JWT 必须透传，mall 故障安全降级或转人工。TDD，失败先 systematic-debugging；运行 Maven、diff、Compose 校验，清理 test- 资源，提交 feat(p6-b): add aftersale workflow，并报告 SHA、文件、验证、清理和集成请求。
```
