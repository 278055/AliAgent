# P8-B 回放评分与版本对比上下文

## 任务卡

- 工作目录：`D:\Java\code\AliAgent-worktrees\p8-replay-scoring`
- 工作分支：`codex/p8-replay-scoring`
- 允许修改：`services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/{replay,runner,scoring,judge,comparison}/` 和对应测试。
- 禁止修改：`contracts/`、Flyway、POM、YAML、A/C 目录、Gateway、P5、knowledge、frontend。

## 核心交付

实现不可变版本清单、确定性 Mock 回放、受审批和预算限制的 DashScope 回归、确定性评分、Judge 辅助、结果聚合和版本对比。B 定义自己的数据集/编排/知识端口，不 import A/C 类型。Judge 不得覆盖安全红线；外部故障不得默认通过。

## 验证

按 TDD 完成 `*Replay*`、`*Runner*`、`*DashScope*`、`*Scorer*`、`*Judge*`、`*Comparison*` 测试，运行 evaluation-service 模块测试和 `git diff --check`。提交建议：`feat(p8-b): add evaluation replay and scoring`。

## 必须使用的 Skill

`superpowers:using-git-worktrees`、`superpowers:executing-plans`、`superpowers:test-driven-development`；失败时 `superpowers:systematic-debugging`；提交前 `superpowers:verification-before-completion`。修改代码前先使用 CodeGraph；查询外部库文档时使用 `context7`。

## 新会话提示词

```text
你负责 P8-B。只在 D:\Java\code\AliAgent-worktrees\p8-replay-scoring 工作，分支 codex/p8-replay-scoring。先确认当前路径、分支、HEAD 与干净状态，完整阅读 AGENTS.md、docs/tasks/p8/P8-阶段任务边界.md、docs/handoffs/P8-B-回放评分与版本对比上下文.md、docs/superpowers/specs/2026-07-28-p8-continuous-evaluation-design.md、docs/superpowers/plans/2026-07-28-p8-continuous-evaluation-plan.md。

使用 superpowers:executing-plans 按计划 Task 5-8 实施，并严格使用 TDD。修改前先用 CodeGraph 定位影响面。只允许修改 evaluation-service 的 replay、runner、scoring、judge、comparison 包和对应测试；禁止修改 contracts、Flyway、POM、YAML、A/C 目录、Gateway、P5、knowledge 和 frontend。

交付固定 Prompt/工作流/模型/知识/工具/规则/数据集/评分版本的 EvaluationManifest；确定性 Mock 回放；管理员审批且受样本、Token、费用限制的 DashScope 回归；意图、工具、参数、RAG、事实、安全、转人工、采纳、时延和成本评分；Judge 只辅助开放回答；同条件版本对比和失败样本证据。跨任务依赖通过 B 自有端口表达，不 import A/C 类型；API Key 不进入领域对象、日志或结果。

完成后运行定向测试、evaluation-service 模块测试和 git diff --check；确认测试资源清理。提交并报告 SHA、文件、测试结果、公共适配器缺口和集成注意事项，不推送、不创建 PR、不合并其他分支。
```
