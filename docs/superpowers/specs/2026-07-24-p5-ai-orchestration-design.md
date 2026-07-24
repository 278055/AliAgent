# P5 AI Orchestration Design

P5 把 AI 回复请求编排为五条只读工作流。Inbox 后创建可恢复执行；规则优先，再模型分类；工具只能读取 knowledge-service 或 mall；经 conversation-service 回写。每次执行固定版本并记录状态/脱敏审计。v1 保持不变；v2 增 generationId。默认 Mock，DashScope 显式密钥才启用；依赖失败不编造事实。
