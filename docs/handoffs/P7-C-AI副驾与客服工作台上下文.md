# P7-C AI 副驾与客服工作台上下文

## 任务目标

实现私有 AI 副驾建议领域核心、建议操作审计、受控只读上下文端口，以及最小客服工作台与专属 API client。

## 必读文件

- `contracts/standards/human-agent-p7.md`
- `docs/tasks/p7/P7-阶段任务边界.md`
- `docs/superpowers/specs/2026-07-24-p7-human-agent-copilot-design.md`
- `docs/superpowers/plans/2026-07-24-p7-human-agent-copilot-plan.md`

## 实现提示词

```text
你负责 P7-C：AI 副驾建议和最小客服工作台。

只能在当前 P7-C Worktree 和 `codex/p7-copilot-workbench` 分支工作。修改前优先使用 CodeGraph。

严格只修改：
- services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/copilot/
- 对应测试目录
- frontend/apps/aliagent-admin/
- frontend/packages/api-client/

使用 TDD 实现后端：
1. 消费 suggestion requested 命令的领域入口和 Inbox 幂等。
2. 仅在 HUMAN_ACTIVE 且 assignedAgentId 与当前客服一致时生成私有建议。
3. 通过 copilot 目录内端口读取对话、知识引用、订单/物流和 P6 售后状态。
4. 依赖失败时不虚构业务事实，建议进入 FAILED_RETRYABLE。
5. 保存模型、Prompt、工作流版本、引用和 refreshNo。
6. 采纳、修改发送、忽略和刷新幂等；保存原始建议、最终内容和差异摘要，不保存思维链。
7. 只有 accept/modify-and-send 才调用人工消息端口生成 STAFF/PUBLIC 消息。

实现最小 Vue 3 + TypeScript 客服工作台：我的邀请/队列、已接管会话、实时聊天、订单物流售后摘要、转派、结束/关闭、副驾刷新/采纳/编辑发送/忽略、在线状态和容量。前端处理重复点击、邀请过期和 WebSocket 断线恢复。

不要修改 AI 服务现有 core/runtime/config/迁移，不修改 frontend 根 package.json、pnpm-workspace 或公共 deploy。需要适配时定义 copilot 端口并列入集成请求。

验证后端单元测试和前端 `pnpm --filter @aliagent/admin build`。若根工作区缺少公共依赖导致前端无法构建，记录精确集成请求，不得越权修改公共文件。

提交信息：feat(p7-c): add copilot agent workbench

最终报告：分支、提交 SHA、后端/前端测试结果、目录边界检查、集成变更请求。不要推送、不要 PR、不要合入集成分支。
```

