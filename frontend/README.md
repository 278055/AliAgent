# 前端工作区骨架

根目录保留现有 Vue + Vite 应用，`npm run dev` 与 `npm run build` 继续直接使用 `src/` 和现有 Vite 配置。P1 不迁移聊天、RAG 页面或静态资源。

`pnpm-workspace.yaml` 为后续前端拆分预留以下位置：

| 位置 | P4 后职责 |
| --- | --- |
| `apps/aliagent-admin` | 管理端应用 |
| `apps/widget-playground` | 聊天组件演示与集成验证 |
| `packages/chat-widget` | 可嵌入聊天组件 |
| `packages/api-client` | 版本化 API 客户端 |
| `packages/ui` | 共用 UI 基元 |
| `packages/shared` | 共用类型、工具与契约适配 |

P4 前所有目录仅为无业务代码的可识别包。P4 在按租户功能开关完成远程会话写入与回退验证后，再把旧页面逐步迁入对应 app/package；切换期间旧 `src/` 始终保留为回退路径。
