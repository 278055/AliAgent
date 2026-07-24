# P7-B 人工接管、转派与消息上下文

## 任务目标

在 conversation-service 的独占新目录中实现接管互斥、转派责任切换、结束/关闭、人工消息授权幂等和主管异常处理领域核心。

## 必读文件

- `contracts/standards/human-agent-p7.md`
- `docs/tasks/p7/P7-阶段任务边界.md`
- `docs/superpowers/specs/2026-07-24-p7-human-agent-copilot-design.md`
- `docs/superpowers/plans/2026-07-24-p7-human-agent-copilot-plan.md`

## 实现提示词

```text
你负责 P7-B：人工接管、转派、结束/关闭和人工消息领域核心。

只能在当前 P7-B Worktree 和 `codex/p7-human-collaboration` 分支工作。修改前优先使用 CodeGraph。

严格只修改：
- services/conversation-service/src/main/java/com/bn/aliagent/conversation/handoff/
- services/conversation-service/src/main/java/com/bn/aliagent/conversation/takeover/
- services/conversation-service/src/main/java/com/bn/aliagent/conversation/transfer/
- services/conversation-service/src/main/java/com/bn/aliagent/conversation/staffmessage/
- 对应测试目录

使用 TDD 实现：
1. 一个会话最多一个 ACTIVE 接管；接受邀请或领取后才能建立接管。
2. 只有当前客服能发送公开人工消息，clientMessageId 重复提交返回同一消息。
3. 转技能组时结束当前占用并生成重新排队命令，但必须保证责任不会在事务失败时丢失。
4. 转指定客服时校验同租户、技能、ONLINE 和容量；目标接受前原客服保持责任。
5. 普通结束释放容量并请求恢复 AI_ACTIVE；显式关闭进入 CLOSED。
6. 主管强制转派和异常释放必须带原因并审计。
7. 所有命令幂等，跨租户和非当前客服操作拒绝。

不要修改 P7-A 目录、迁移、配置、POM 或现有 core/api/realtime。定义任务内端口表达所需的会话状态、分配和消息持久化能力，交由集成会话适配。

验证至少包括：双接管竞争、非当前客服发送拒绝、重复人工消息、转派接受前责任保留、转派失败回滚、结束恢复 AI、关闭不可恢复、主管跨租户拒绝。

提交信息：feat(p7-b): add human collaboration core

最终报告：分支、提交 SHA、测试命令与数量、目录边界检查、集成变更请求。不要推送、不要 PR、不要合入集成分支。
```

