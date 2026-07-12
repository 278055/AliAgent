# 迁移功能开关规范

迁移开关统一由 Nacos 管理，键名采用 `feature.{domain}.{capability}`。默认关闭，按租户白名单启用，不使用随机比例灰度。

| 开关 | 默认 | 用途 | 回退 |
|---|---|---|---|
| `feature.knowledge.remote-read` | `false` | 旧单体读取远程知识服务 | 关闭后回退本地 RAG |
| `feature.conversation.remote-write` | `false` | 前端向会话服务写消息 | 关闭后回退旧会话接口 |
| `feature.orchestration.remote-reply` | `false` | 会话服务发布 AI 编排任务 | 关闭后使用旧 Agent |
| `feature.mall.internal-read` | `false` | AI 工具调用 mall 只读接口 | 关闭后不生成电商事实 |
| `feature.aftersale.write` | `false` | 开放售后写操作 | 关闭后拒绝执行，仅解释规则 |

每次变更必须记录操作者、租户、原因、时间和关联发布版本。紧急关闭不得删除历史配置。
