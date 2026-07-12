# P1-B 服务骨架上下文

## 任务目标

创建六个独立 Spring Boot 服务的可编译骨架：启动类、独立配置、Actuator、Flyway、数据库归属声明和最小健康接口。评测与洞察仅建立事件接收占位能力。

## 唯一目录所有权

- 仅允许修改或新建 `services/`。
- 不允许修改根 `pom.xml`、`contracts/`、`.github/`、`deploy/`、`frontend/`、旧单体 `src/`。
- 假定 P1-A 已合入根父 POM；若公共依赖缺失，将所需坐标写入交付说明，由 P1-A/集成会话处理，禁止直接改根 POM。

## 已冻结服务与数据归属

| 服务 | 数据库 | P1 骨架职责 |
|---|---|---|
| `gateway-service` | 无业务库 | 健康接口、未来路由/上下文过滤器占位 |
| `conversation-service` | `conversation_db` | Flyway、健康接口、会话域占位 |
| `ai-orchestration-service` | `orchestration_db` | Flyway、健康接口、编排域占位 |
| `knowledge-service` | `knowledge_db` | Flyway、健康接口、知识域占位 |
| `evaluation-service` | `evaluation_db` | Flyway、健康接口、事件接收占位 |
| `insight-service` | `insight_db` | Flyway、健康接口、事件接收占位 |

## 实施约束

- 每个服务是独立 Maven 子模块，父 POM 为根工程；服务之间禁止编译期相互依赖。
- 每个服务使用独立 profile/环境变量配置数据源；禁止硬编码端口、口令或连接 URL。端口和基础设施地址由 P1-C Compose 提供。
- 为有数据库归属的五个服务分别提供最小 Flyway `V1__baseline.sql`，只创建服务自己的迁移历史和健康验证所需最小对象；不得创建跨服务表。
- Actuator 使用 `/actuator/health`；附加应用健康端点必须遵守 P1-A OpenAPI，且不暴露机密。
- Nacos 注册仅以配置占位实现，不能在尚未确定兼容 BOM 前强行添加客户端依赖。
- 只建立事件接收端口/接口占位和日志，不连接 RabbitMQ，也不实现领域消费者。
- 所有日志为中文，包含可从请求上下文获得的 `traceId`、`requestId`、`tenantId` 字段的扩展点。

## 验收与合并

- 根聚合工程能编译测试；每个模块可单独执行测试。
- 每个服务至少有一个不连接外部资源的健康契约测试。
- Flyway 迁移文件命名和位置正确，SQL 不含跨库引用。
- 分支命名：`codex/p1-service-skeletons`。
- 合并顺序第二：在 P1-A 后合入；P1-C 的 Compose 配置随后完成运行时集成。

## 必读文件

- `AGENTS.md`
- `docs/baselines/P1-冻结清单.md`
- `docs/baselines/P0-兼容性矩阵与切换回退.md`
- `contracts/standards/identity-and-context.md`
- `contracts/standards/api-and-event.md`
- `contracts/standards/error-codes.md`
