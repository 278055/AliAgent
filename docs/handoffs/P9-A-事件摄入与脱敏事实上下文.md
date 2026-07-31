# P9-A 事件摄入与脱敏事实上下文

## 任务卡

- 工作目录：`D:\Java\code\AliAgent-worktrees\p9-insight-intake`
- 工作分支：`codex/p9-insight-intake`
- 允许修改：`services/insight-service/src/main/java/com/bn/aliagent/insight/{intake,fact,retention}/` 和对应测试。
- 禁止修改：公共契约、Flyway、POM、YAML、根包控制器/装配、A 以外业务包、其他服务、Gateway、前端。

## 核心交付

实现版本化事件校验、可信租户校验、Inbox 领域端口、持久化前确定性脱敏、匿名事实投影、迟到事件判定、事实修订关系、超过 7 天的人工重算项，以及 90 天事实保留清理领域逻辑。

原始 payload 只能存在于当前调用栈；Inbox 只保存摘要和处理状态。订单/会话/用户关联必须变为租户级不可反解引用。A 只定义持久化端口和内存测试实现，JDBC、Flyway、消息监听和应用装配由集成会话处理。

## CodeGraph 盘点结论

- 可参考 P8 `EventIntakeService` 的 `reserve -> anonymize -> sink -> complete/fail` 流程。
- 可参考 P8 `DeterministicAnonymizer` 的敏感键删除、文本清理和租户 HMAC，但 P9 必须有自己的包和策略版本。
- 当前 `insight-service/EventReceiverController` 只是日志占位，禁止 A 修改；集成会话会替换。

## 验证命令

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/insight-service -Dtest='*Intake*,*Anonym*,*Fact*,*Retention*' test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/insight-service test
git diff --check
```

## 必须使用的 Skill

`superpowers:executing-plans`、`superpowers:test-driven-development`；出现失败时使用 `superpowers:systematic-debugging`；交付前使用 `superpowers:verification-before-completion`。工作区已经隔离，不要再创建嵌套 Worktree。修改代码前先用 CodeGraph；若当前 Worktree 无索引，用主仓库 `D:\Java\code\AliAgent` 的索引定位同一基线代码。

## 新会话提示词

```text
你负责 AliAgent P9-A：事件摄入与脱敏事实。只在 D:\Java\code\AliAgent-worktrees\p9-insight-intake 工作，分支必须为 codex/p9-insight-intake。开始时确认路径、分支、HEAD 和工作区干净；完整阅读 AGENTS.md、docs/tasks/p9/P9-阶段任务边界.md、docs/handoffs/P9-A-事件摄入与脱敏事实上下文.md、docs/superpowers/specs/2026-07-31-p9-operational-insight-design.md、docs/superpowers/plans/2026-07-31-p9-operational-insight-plan.md。

使用 superpowers:executing-plans 执行计划中 P9-A 的 Task 1-3，并严格使用 superpowers:test-driven-development。修改前优先用 CodeGraph 定位 P8 EventIntakeService、DeterministicAnonymizer 及 insight-service 影响面；若 Worktree 没有索引，查询主仓库 D:\Java\code\AliAgent 的同一基线索引。

只允许修改 insight-service 的 intake、fact、retention 包和对应测试。禁止修改 contracts、Flyway、pom.xml、application*.yml、EventReceiverController、应用装配、aggregate/metric/radar/topic/gap/query、其他服务、Gateway 和 frontend。跨任务依赖只能通过 A 包内端口表达，公共缺口记录给集成方。

必须交付：事件信封与事件策略、可信租户验证、Inbox reserve/complete/fail 幂等语义、原始 payload 落库前匿名化、敏感字段清理、租户级不可反解引用、匿名事实投影、迟到事件与事实修订、7 天自动重算判定、超过 7 天人工重算项、90 天保留清理领域逻辑。禁止在日志、Inbox、异常信息保存原始 payload。

按计划逐步写失败测试、确认失败、最小实现、确认通过并小步提交。完成后运行定向测试、insight-service 全模块测试和 git diff --check，清理 test- 数据。提交最终代码，但不要推送、不要创建 PR、不要合并其他分支、不要清理 Worktree。报告 SHA、文件、测试数量、清理结果及集成方需要完成的 JDBC/Flyway/消息装配缺口。
```
