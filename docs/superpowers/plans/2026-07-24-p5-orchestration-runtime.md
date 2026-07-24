# P5 编排运行时实施计划

> **供智能代理执行：** 必须使用 `superpowers:executing-plans` 逐项实施。步骤使用复选框记录状态。

**目标：** 接通 v2 消费、执行存储和五类只读工作流，使 Mock 模式可离线验证。

**架构：** 新增运行期配置创建已有领域对象和适配器。新增工作流执行器依赖冻结端口，基于 `Intent` 分派，始终向 conversation 端口写入同一 `replyMessageId`、`generationId` 的最终块；异常与无事实情形转人工。

**技术栈：** Java 17、Spring Boot 3.4、Spring AMQP、JDBC、JUnit 5。

---

### 任务 1：工作流执行器测试

**文件：**
- 新建：`services/ai-orchestration-service/src/test/java/com/bn/aliagent/orchestration/runtime/ReadOnlyWorkflowRunnerTest.java`
- 新建：`services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/runtime/ReadOnlyWorkflowRunner.java`

- [ ] 编写 GENERAL、RAG、订单、物流、转人工与依赖故障的失败测试，使用端口内存替身记录模型、工具和流块调用。
- [ ] 运行 `mvn -pl services/ai-orchestration-service -Dtest=ReadOnlyWorkflowRunnerTest test`，预期因类不存在失败。
- [ ] 实现最小 `ReadOnlyWorkflowRunner`：仅在 GENERAL 调模型、RAG 调检索、订单/物流调相应只读工具；其他情况写转人工块。
- [ ] 重运行上述命令，预期全部通过。

### 任务 2：运行期装配测试

**文件：**
- 新建：`services/ai-orchestration-service/src/test/java/com/bn/aliagent/orchestration/OrchestrationWiringTest.java`
- 新建：`services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/config/OrchestrationRuntimeConfiguration.java`
- 修改：`services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/adapter/AdapterConfiguration.java`

- [ ] 编写 Spring 上下文测试，断言 v2 消费端、映射器、执行存储、编排服务和工作流执行器均可注入。
- [ ] 运行 `mvn -pl services/ai-orchestration-service -Dtest=OrchestrationWiringTest test`，预期因 Bean 缺失失败。
- [ ] 注册默认内存存储、数据库 profile JDBC 存储、端口适配器、路由器、服务和消费端；启动时调用恢复方法。
- [ ] 重运行上述命令，预期通过。

### 任务 3：模块及回归验证

**文件：**
- 修改：`docs/superpowers/specs/2026-07-24-p5-ai-orchestration-design.md`（仅在实现与设计不一致时）

- [ ] 运行 `mvn -pl services/ai-orchestration-service test`，预期全部通过。
- [ ] 运行 `mvn test`、`mvn -pl contracts/contract-validator test` 和 `docker compose --env-file deploy/.env.example -f deploy/docker-compose.yml config --quiet`。
- [ ] 执行 `git diff --check`、`git status --short`，提交运行期实现。
