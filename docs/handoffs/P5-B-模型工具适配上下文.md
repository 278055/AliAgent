# P5-B 模型工具适配上下文

工作目录 `D:\Java\code\AliAgent-worktrees\p5-orchestration-adapters`，分支 `codex/p5-orchestration-adapters`。只改 adapter/tool 和测试；必须先读任务边界。实现 Mock/DashScope、知识、mall 订单/物流、conversation 流适配，透传可信头和服务 JWT，校验、超时、脱敏、引用。查询 Spring AI Alibaba/WebClient/OpenFeign API 必须用 context7。无密钥绝不真调；mall/knowledge 故障不得产生事实；v1 不能猜 generationId。

## 新会话提示词

```text
负责 P5-B。进入 D:\Java\code\AliAgent-worktrees\p5-orchestration-adapters，确认 codex/p5-orchestration-adapters 且干净。读任务边界和本文件。只改 adapter/tool 和测试。实现 Mock/DashScope、knowledge、mall 只读、conversation 流回写；查询 Spring AI Alibaba 或 WebClient/OpenFeign 前用 context7。默认 mock；仅 provider=dashscope 且 DASHSCOPE_API_KEY 存在时真实调用，禁止密钥提交。透传可信头/JWT、保留 citation、校验超时脱敏。v1 无 generationId 不得猜测；故障不虚构事实。测试、diff、提交并报告。
```
