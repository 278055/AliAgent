# P9-B 指标聚合与问题雷达上下文

## 任务卡

- 工作目录：`D:\Java\code\AliAgent-worktrees\p9-insight-radar`
- 工作分支：`codex/p9-insight-radar`
- 允许修改：`services/insight-service/src/main/java/com/bn/aliagent/insight/{aggregate,metric,radar}/` 和对应测试。
- 禁止修改：公共契约、Flyway、POM、YAML、根包控制器/装配、B 以外业务包、其他服务、Gateway、前端。

## 核心交付

实现指标定义版本、退款/转人工/客服响应/满意度/知识缺口/物流问题口径、小时增量与日终固化、修订版本、7/28 天同周期趋势、绝对阈值、样本不足、雷达去重合并和处置状态机。

B 不依赖 A 的具体事实类型；通过 B 包内 `MetricFactReader`/`AggregationStore` 等端口和自己的测试夹具开发。JDBC、调度器、Flyway、控制器及 A 到 B 的适配由集成会话完成。

## 固定口径

- 退款率和退款金额率归属订单创建且已支付的周期，退款成功才计入。
- 同一会话多次转人工在会话级指标只计一次。
- 客服响应从首次进入人工队列到首次人工公开回复，AI/内部备注不计。
- 满意度只计算显式评价；未评价单独进入评价覆盖率。
- 异常同时使用 7/28 天趋势和绝对阈值，不使用跨租户基准。
- 雷达状态只允许 `OPEN -> ACKNOWLEDGED -> RESOLVED` 或 `OPEN -> IGNORED`。

## 验证命令

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/insight-service -Dtest='*Metric*,*Aggregation*,*Radar*,*Anomaly*' test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/insight-service test
git diff --check
```

## 必须使用的 Skill

`superpowers:executing-plans`、`superpowers:test-driven-development`；失败时使用 `superpowers:systematic-debugging`；提交前使用 `superpowers:verification-before-completion`。工作区已经隔离，不要创建嵌套 Worktree。修改前使用主仓库 CodeGraph 索引盘点影响面。

## 新会话提示词

```text
你负责 AliAgent P9-B：指标聚合与问题雷达。只在 D:\Java\code\AliAgent-worktrees\p9-insight-radar 工作，分支必须为 codex/p9-insight-radar。开始时确认路径、分支、HEAD 和工作区干净；完整阅读 AGENTS.md、docs/tasks/p9/P9-阶段任务边界.md、docs/handoffs/P9-B-指标聚合与问题雷达上下文.md、docs/superpowers/specs/2026-07-31-p9-operational-insight-design.md、docs/superpowers/plans/2026-07-31-p9-operational-insight-plan.md。

使用 superpowers:executing-plans 执行计划中 P9-B 的 Task 4-6，并严格使用 superpowers:test-driven-development。修改前优先用 CodeGraph 定位 insight-service 和可复用的领域状态机/版本治理模式；Worktree 若无索引，查询 D:\Java\code\AliAgent 主仓库索引。

只允许修改 insight-service 的 aggregate、metric、radar 包和对应测试。禁止修改 contracts、Flyway、pom.xml、application*.yml、根包控制器和装配、intake/fact/retention/topic/gap/query、其他服务、Gateway 和 frontend。不得 import A/C 尚未合并的类型；用 B 包内端口与测试夹具表达输入和持久化需求。

必须交付：不可变指标定义版本；退款率、退款金额率、转人工率、客服响应、满意度与评价覆盖率、知识缺口率、物流问题率；小时增量、日终固化与修订关系；7/28 天同周期趋势和绝对安全阈值；样本不足；雷达固定指标/阈值/聚合版本；同租户问题去重合并；乐观版本与合法状态迁移。

按计划逐步写失败测试、确认失败、最小实现、确认通过并小步提交。完成后运行定向测试、insight-service 全模块测试和 git diff --check，清理 test- 数据。提交最终代码，但不要推送、不要创建 PR、不要合并其他分支、不要清理 Worktree。报告 SHA、文件、测试数量、清理结果及集成方需要完成的 JDBC/Flyway/调度/API 适配缺口。
```
