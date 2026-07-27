# P8-A 评测摄入与数据集上下文

## 任务卡

- 工作目录：`D:\Java\code\AliAgent-worktrees\p8-evaluation-dataset`
- 工作分支：`codex/p8-evaluation-dataset`
- 允许修改：`services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/{intake,anonymization,candidate,dataset}/` 和对应测试。
- 禁止修改：`contracts/`、Flyway、POM、YAML、现有占位控制器、B/C 目录、Gateway、P5、knowledge、frontend。

## 核心交付

实现事件信封校验、Inbox 幂等、两阶段匿名化、候选审核、30/7 天保留策略、共享授权和不可变评测集版本。原始 payload 不得持久化或写日志；跨租户操作必须拒绝。A 对外提供 `EvaluationDatasetReader`，但不 import B/C 类型。

## 验证

按 TDD 完成 `*Intake*`、`*Anonym*`、`*Candidate*`、`*Dataset*` 测试，运行 evaluation-service 模块测试和 `git diff --check`。提交建议：`feat(p8-a): add evaluation datasets`。

## 必须使用的 Skill

`superpowers:using-git-worktrees`、`superpowers:executing-plans`、`superpowers:test-driven-development`；失败时 `superpowers:systematic-debugging`；提交前 `superpowers:verification-before-completion`。修改代码前先使用 CodeGraph。

## 新会话提示词

```text
你负责 P8-A。只在 D:\Java\code\AliAgent-worktrees\p8-evaluation-dataset 工作，分支 codex/p8-evaluation-dataset。先确认当前路径、分支、HEAD 与干净状态，完整阅读 AGENTS.md、docs/tasks/p8/P8-阶段任务边界.md、docs/handoffs/P8-A-评测摄入与数据集上下文.md、docs/superpowers/specs/2026-07-28-p8-continuous-evaluation-design.md、docs/superpowers/plans/2026-07-28-p8-continuous-evaluation-plan.md。

使用 superpowers:executing-plans 按计划 Task 1-4 实施，并严格使用 TDD。修改前先用 CodeGraph 定位影响面。只允许修改 evaluation-service 的 intake、anonymization、candidate、dataset 包和对应测试；禁止修改 contracts、Flyway、POM、YAML、现有 EventReceiverController、B/C 目录、Gateway、P5、knowledge 和 frontend。

交付事件信封验证、Inbox 幂等、持久化前匿名化、隔离状态、候选审核、30/7 天保留、公共共享二次匿名化与显式授权、不可变评测集版本。原始 payload 不得落库或写日志，tenantId 只取可信上下文。跨任务依赖通过 A 自有端口表达，不 import B/C 类型；公共迁移和装配缺口只记录给集成方。

完成后运行定向测试、evaluation-service 模块测试和 git diff --check；确认测试资源清理。提交并报告 SHA、文件、测试结果、公共接口缺口和集成注意事项，不推送、不创建 PR、不合并其他分支。
```
