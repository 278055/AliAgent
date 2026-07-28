# P8-C 发布门禁与评测控制台上下文

## 任务卡

- 工作目录：`D:\Java\code\AliAgent-worktrees\p8-release-gate`
- 工作分支：`codex/p8-release-gate`
- 允许修改：`evaluation/gate/` 及对应测试、`frontend/apps/aliagent-admin/src/evaluation/`、`frontend/packages/api-client/src/evaluation-client.ts`，以及任务边界明确列出的前端入口文件。
- 禁止修改：`contracts/`、Flyway、POM、YAML、A/B 目录、Gateway、P5、knowledge。

## 核心交付

实现相对退化与绝对红线门禁、签名 Gate Decision、验签/过期/撤销/跨租户检查，以及候选、数据集、任务、对比、失败样本和门禁结果的最小控制台。C 定义自己的结果读取端口，不 import B 类型。P5 和 knowledge 的实际发布接口连接由集成会话完成。

## 验证

按 TDD 完成 `*GateEvaluation*`、`*GateDecision*` 测试，运行 evaluation-service 模块测试、api-client 类型检查、admin 构建和 `git diff --check`。提交建议：`feat(p8-c): add release gate and console`。

## 必须使用的 Skill

`superpowers:using-git-worktrees`、`superpowers:executing-plans`、`superpowers:test-driven-development`；失败时 `superpowers:systematic-debugging`；提交前 `superpowers:verification-before-completion`。修改代码前先使用 CodeGraph；Vue/TypeScript API 查询使用 `context7`。

## 新会话提示词

```text
你负责 P8-C。只在 D:\Java\code\AliAgent-worktrees\p8-release-gate 工作，分支 codex/p8-release-gate。先确认当前路径、分支、HEAD 与干净状态，完整阅读 AGENTS.md、docs/tasks/p8/P8-阶段任务边界.md、docs/handoffs/P8-C-发布门禁与评测控制台上下文.md、docs/superpowers/specs/2026-07-28-p8-continuous-evaluation-design.md、docs/superpowers/plans/2026-07-28-p8-continuous-evaluation-plan.md。

使用 superpowers:executing-plans 按计划 Task 9-11 实施，并严格使用 TDD。修改前先用 CodeGraph 定位影响面。只允许修改 evaluation-service 的 gate 包和对应测试、aliagent-admin 的 evaluation 模块、evaluation-client.ts 及边界文档明确列出的前端入口文件；禁止修改 contracts、Flyway、POM、YAML、A/B 目录、Gateway、P5 和 knowledge。

交付相对退化与绝对安全红线并行判定；红线优先且 Judge 不可覆盖；使用 Java 标准加密 API 签发绑定租户、目标版本、清单摘要、策略、任务结果和有效期的 Gate Decision；拒绝伪造、过期、撤销、跨租户和版本不匹配证明。实现候选审核、评测集、任务启动、版本对比、失败样本和门禁结果六块最小控制台，不展示原始敏感 payload 或密钥。跨任务依赖通过 C 自有端口表达，不 import B 类型；P5/knowledge 发布连接只记录给集成方。

完成后运行定向测试、evaluation-service 模块测试、api-client 类型检查、admin 构建和 git diff --check；确认测试资源清理。提交并报告 SHA、文件、验证、公共连接缺口和集成注意事项，不推送、不创建 PR、不合并其他分支。
```
