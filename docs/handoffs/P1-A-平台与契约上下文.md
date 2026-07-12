# P1-A 平台与契约上下文

## 任务目标

将根工程升级为 Maven 聚合父工程，并建立 P1 的可校验契约与最小公共安全组件。只提供骨架和契约，不迁移任何聊天、RAG、售后或 `mall` 业务。

## 唯一目录所有权

- 允许修改：根 `pom.xml`、`.github/`（如 CI 尚不存在）、`contracts/`。
- 不允许修改：`services/` 内任何文件、`deploy/`、`frontend/`、现有 `src/main/` 业务代码及现有数据库 DDL。
- P1-B 创建子服务模块后，其 POM 将以根 `pom.xml` 为父 POM；P1-A 不创建服务目录。

## 已冻结约束

- Java 17、Spring Boot 3.4.5、Spring AI Alibaba 1.1.2.0、Spring AI 1.1.2、MyBatis-Plus 3.5.5、pgvector 1024 维。
- Spring Cloud / Spring Cloud Alibaba 的准确 BOM 版本必须先验证与 Boot 3.4.5 的兼容性并记录证据；未验证时不要引入 Nacos 客户端依赖。
- 根 POM 是 `packaging=pom` 的聚合父工程；旧单体应作为一个保留的可构建模块或明确的兼容模块，不能在 P1 删除。
- HTTP 从 `/api/v1` 开始，事件使用 `eventVersion: 1`；遵守 `contracts/standards/` 的上下文、错误码、幂等和功能开关规范。

## 交付物

1. 根 Maven 聚合和依赖管理：统一 Java、Boot、测试、Actuator、Flyway、日志等 P1 骨架所需依赖版本；不要把 AI、数据库、Nacos 依赖无差别塞给所有模块。
2. `contracts/openapi/`：至少包含 Gateway 健康路由和跨服务最小健康接口的 OpenAPI 3.1 契约。
3. `contracts/asyncapi/` 与 `contracts/json-schema/`：事件信封与 `AIReplyRequested` 示例契约/Schema，均含版本、租户和追踪字段。
4. 契约校验脚本或 CI 工作流：可在不启动基础设施的情况下校验 YAML/JSON 语法和 Schema 引用；若使用新工具，须锁定版本和说明安装方式。
5. 公共组件设计说明：日志字段、请求上下文、服务 JWT 的接口边界与包坐标；实现代码仅能放在根公共模块（若需要），不得侵入服务目录。

## 验收与合并

- 执行 `mvn -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml test`；使用阿里云镜像。
- 对所有 YAML/JSON 执行契约校验；不允许未解析的 `$ref`。
- 分支命名：`codex/p1-platform-contracts`。
- 合并顺序第一：P1-A 必须先合入阶段集成分支，P1-B 和 P1-C 再基于它处理集成冲突。

## 必读文件

- `AGENTS.md`
- `docs/baselines/P1-冻结清单.md`
- `docs/baselines/P0-兼容性矩阵与切换回退.md`
- `contracts/standards/README.md`
- `contracts/standards/identity-and-context.md`
- `contracts/standards/api-and-event.md`
- `contracts/standards/error-codes.md`
- `contracts/standards/feature-flags.md`
