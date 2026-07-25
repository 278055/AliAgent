# P7-A 技能组、队列与分配上下文

## 任务目标

在 conversation-service 的独占新目录中实现技能组、成员容量、队列优先级、自动分配邀请、超时重分配和主动领取的可测试领域核心。

## 必读文件

- `contracts/standards/human-agent-p7.md`
- `docs/tasks/p7/P7-阶段任务边界.md`
- `docs/superpowers/specs/2026-07-24-p7-human-agent-copilot-design.md`
- `docs/superpowers/plans/2026-07-24-p7-human-agent-copilot-plan.md`

## 实现提示词

```text
你负责 P7-A：技能组、队列、分配邀请和最小主管队列能力。

只能在当前 P7-A Worktree 和 `codex/p7-agent-routing` 分支工作。修改前优先使用 CodeGraph；Worktree 没有索引时使用主仓库同一冻结基线的索引，或执行 `codegraph explore`。

严格只修改：
- services/conversation-service/src/main/java/com/bn/aliagent/conversation/agent/
- services/conversation-service/src/main/java/com/bn/aliagent/conversation/skill/
- services/conversation-service/src/main/java/com/bn/aliagent/conversation/queue/
- services/conversation-service/src/main/java/com/bn/aliagent/conversation/assignment/
- 对应测试目录

使用 TDD 实现：
1. 技能组、标签、成员资格、最大接待量和数据范围模型。
2. 版本化路由与优先级计算；高风险/投诉优先，其次等待时长和会员等级。
3. 同租户、技能匹配、ONLINE、剩余容量候选筛选。
4. 当前接待量、最近分配时间、staffId 的确定性排序。
5. 限时邀请、接受、拒绝、过期、重分配和达到次数后 CLAIMABLE。
6. 主动领取并发保护和幂等。
7. STAFF 只能查看所属技能组队列，SUPERVISOR 可查看本租户全部队列。

不要修改迁移、POM、配置、公共契约、现有 core/api/realtime。需要外部能力时在任务目录定义端口和内存测试实现，并在最终报告列出集成变更请求。

验证至少包括：规则版本固定、同分 FIFO、离线/忙碌/满容量过滤、并发接受只成功一次、重复 claim 幂等、跨租户拒绝、邀请超时可重入。

提交信息：feat(p7-a): add agent queue assignment core

最终报告：分支、提交 SHA、测试命令与数量、目录边界检查、集成变更请求。不要推送、不要 PR、不要合入集成分支。
```

