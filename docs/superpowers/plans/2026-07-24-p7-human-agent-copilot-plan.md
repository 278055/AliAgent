# P7 Human Agent and Copilot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立技能组自动分配、人工接管与转派、实时人工消息、私有 AI 副驾和最小客服工作台，使人工接管后 AI 不再公开发言。

**Architecture:** `conversation-service` 保存人工客服协作事实，A/B 通过独立领域端口并行实现；`ai-orchestration-service/copilot` 生成并审计私有建议；`frontend/apps/aliagent-admin` 提供最小工作台。公共迁移、契约、现有控制器/实时通道、Bean 装配和 Gateway 由集成会话连接。

**Tech Stack:** Java 17、Spring Boot 3.4.5、Spring JDBC、PostgreSQL、RabbitMQ、Redis、Vue 3、TypeScript、Vite、pnpm、JUnit 5、Mockito。

---

## 1. 冻结文件结构

### P7-A 新增

```text
services/conversation-service/src/main/java/com/bn/aliagent/conversation/
  agent/AgentModels.java
  agent/AgentCapacityService.java
  agent/AgentDirectoryPort.java
  skill/SkillGroupModels.java
  skill/SkillRoutingService.java
  skill/SkillGroupRepository.java
  queue/HumanQueueModels.java
  queue/HumanQueueService.java
  queue/HumanQueueRepository.java
  assignment/AssignmentModels.java
  assignment/AssignmentService.java
  assignment/AssignmentRepository.java
  assignment/AssignmentPorts.java
```

对应测试目录保持同包结构。

### P7-B 新增

```text
services/conversation-service/src/main/java/com/bn/aliagent/conversation/
  handoff/HandoffModels.java
  handoff/HandoffService.java
  takeover/TakeoverModels.java
  takeover/TakeoverService.java
  takeover/TakeoverRepository.java
  transfer/TransferModels.java
  transfer/TransferService.java
  staffmessage/StaffMessageModels.java
  staffmessage/StaffMessageService.java
  staffmessage/StaffMessageRepository.java
```

对应测试目录保持同包结构。

### P7-C 新增

```text
services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/copilot/
  CopilotModels.java
  CopilotPorts.java
  CopilotRepository.java
  CopilotPromptFactory.java
  CopilotService.java
  CopilotSuggestionConsumer.java

frontend/apps/aliagent-admin/
  index.html
  package.json
  tsconfig.json
  vite.config.ts
  src/main.ts
  src/App.vue
  src/types.ts
  src/stores/agent-workbench.ts
  src/components/QueuePanel.vue
  src/components/ConversationPanel.vue
  src/components/CopilotPanel.vue
  src/components/TransferDialog.vue
  src/assets/main.css

frontend/packages/api-client/
  package.json
  src/index.ts
  src/agent-client.ts
  src/agent-socket.ts
```

### 集成会话新增或修改

```text
contracts/openapi/human-agent-v1.yaml
contracts/asyncapi/human-agent-events-v1.yaml
contracts/schemas/human-agent/skill-group-v1.schema.json
contracts/schemas/human-agent/human-queue-item-v1.schema.json
contracts/schemas/human-agent/assignment-offer-v1.schema.json
contracts/schemas/human-agent/copilot-suggestion-requested-v1.schema.json
contracts/schemas/human-agent/copilot-suggestion-generated-v1.schema.json
contracts/schemas/human-agent/copilot-suggestion-actioned-v1.schema.json
services/conversation-service/src/main/resources/db/migration/V6__p7_human_agent.sql
services/ai-orchestration-service/src/main/resources/db/migration/V3__p7_copilot.sql
services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/AgentAdministrationController.java
services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/AgentQueueController.java
services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/HumanCollaborationController.java
services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/SupervisorQueueController.java
services/conversation-service/src/main/java/com/bn/aliagent/conversation/config/HumanAgentConfiguration.java
services/conversation-service/src/main/java/com/bn/aliagent/conversation/persistence/JdbcHumanAgentAdapters.java
services/conversation-service/src/main/java/com/bn/aliagent/conversation/realtime/HumanAgentRealtimePublisher.java
services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/config/CopilotConfiguration.java
services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/adapter/CopilotContextAdapters.java
services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/messaging/CopilotSuggestionRequestedConsumer.java
services/gateway-service/src/main/resources/application.yml
frontend/package.json
frontend/pnpm-workspace.yaml
```

## 2. P7-A 实施任务

### Task A1：技能组、标签和成员容量模型

**Files:**
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/skill/SkillGroupModels.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/skill/SkillGroupRepository.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/agent/AgentModels.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/agent/AgentDirectoryPort.java`
- Test: `services/conversation-service/src/test/java/com/bn/aliagent/conversation/skill/SkillGroupModelsTest.java`

- [ ] **Step 1: 编写模型校验失败测试**

```java
@Test
void 成员容量必须为正且租户不能为空() {
    assertThrows(IllegalArgumentException.class,
            () -> new AgentModels.Membership("", UUID.randomUUID(), "staff-1", true, 0));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
& "D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd" -s "D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml" -pl services/conversation-service -Dtest=SkillGroupModelsTest test
```

Expected: FAIL，模型类尚不存在。

- [ ] **Step 3: 实现最小不可变模型**

```java
public record Membership(String tenantId, UUID skillGroupId, String staffId,
                         boolean enabled, int maxConcurrent) {
    public Membership {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (skillGroupId == null) throw new IllegalArgumentException("skillGroupId is required");
        if (staffId == null || staffId.isBlank()) throw new IllegalArgumentException("staffId is required");
        if (maxConcurrent < 1) throw new IllegalArgumentException("maxConcurrent must be positive");
    }
}
```

同时定义 `SkillGroup`、`SkillTag`、`Presence`、`AgentSnapshot` 以及仅含领域需要的方法的 Repository/Port。

- [ ] **Step 4: 运行测试确认通过**

Expected: `SkillGroupModelsTest` PASS。

- [ ] **Step 5: 提交**

```powershell
git add services/conversation-service/src/main/java/com/bn/aliagent/conversation/skill services/conversation-service/src/main/java/com/bn/aliagent/conversation/agent services/conversation-service/src/test/java/com/bn/aliagent/conversation/skill
git commit -m "feat(p7-a): add skill group and agent models"
```

### Task A2：版本化路由与优先级

**Files:**
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/skill/SkillRoutingService.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/queue/HumanQueueModels.java`
- Test: `services/conversation-service/src/test/java/com/bn/aliagent/conversation/skill/SkillRoutingServiceTest.java`
- Test: `services/conversation-service/src/test/java/com/bn/aliagent/conversation/queue/PriorityPolicyTest.java`

- [ ] **Step 1: 编写规则版本和优先级测试**

```java
@Test
void 高风险售后必须高于普通vip且固定规则版本() {
    var risky = policy.score(new PriorityInput(true, false, "NORMAL", Duration.ZERO));
    var vip = policy.score(new PriorityInput(false, false, "VIP", Duration.ofMinutes(30)));
    assertTrue(risky.score() > vip.score());
    assertEquals("p7-priority-v1", risky.ruleVersion());
}
```

- [ ] **Step 2: 运行测试确认失败**

Expected: FAIL，策略尚不存在。

- [ ] **Step 3: 实现确定性策略**

```java
int risk = input.highRisk() ? 1000 : 0;
int complaint = input.complaintRisk() ? 800 : 0;
int member = switch (input.membershipLevel()) {
    case "HIGH_VALUE" -> 200;
    case "VIP" -> 100;
    default -> 0;
};
int waiting = (int) Math.min(300, input.waiting().toMinutes());
return new PriorityScore(risk + complaint + member + waiting, "p7-priority-v1");
```

路由服务仅接受已验证标签并返回 `skillGroupId`、matchedTags 和版本。

- [ ] **Step 4: 运行两个测试类确认通过**

- [ ] **Step 5: 提交**

```powershell
git add services/conversation-service/src/main/java/com/bn/aliagent/conversation/skill services/conversation-service/src/main/java/com/bn/aliagent/conversation/queue services/conversation-service/src/test/java/com/bn/aliagent/conversation/skill services/conversation-service/src/test/java/com/bn/aliagent/conversation/queue
git commit -m "feat(p7-a): add deterministic skill routing"
```

### Task A3：队列入队与幂等

**Files:**
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/queue/HumanQueueRepository.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/queue/HumanQueueService.java`
- Test: `services/conversation-service/src/test/java/com/bn/aliagent/conversation/queue/HumanQueueServiceTest.java`

- [ ] **Step 1: 编写重复入队测试**

```java
@Test
void 同一请求和会话重复入队返回同一个队列项() {
    QueueItem first = service.enqueue(command);
    QueueItem second = service.enqueue(command);
    assertEquals(first.id(), second.id());
    assertEquals(1, repository.activeCount(command.tenantId(), command.conversationId()));
}
```

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: 实现入队服务**

服务先按 `requestId` 查找，再按 tenant/conversation 查找活动项；新建时保存冻结的路由和优先级版本。Repository 需暴露原子 `createIfAbsent` 语义，不能只做先查后写。

- [ ] **Step 4: 增加跨租户和 CLOSED 会话拒绝测试并通过**

- [ ] **Step 5: 提交**

```powershell
git add services/conversation-service/src/main/java/com/bn/aliagent/conversation/queue services/conversation-service/src/test/java/com/bn/aliagent/conversation/queue
git commit -m "feat(p7-a): add idempotent human queue"
```

### Task A4：候选筛选、限时邀请与超时重分配

**Files:**
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/assignment/AssignmentModels.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/assignment/AssignmentPorts.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/assignment/AssignmentRepository.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/assignment/AssignmentService.java`
- Test: `services/conversation-service/src/test/java/com/bn/aliagent/conversation/assignment/AssignmentServiceTest.java`

- [ ] **Step 1: 编写候选排序和过滤测试**

```java
@Test
void 只选择在线有技能且未满容量的最小负载客服() {
    Offer offer = service.offer(queueItem.id(), clock.instant());
    assertEquals("staff-low-load", offer.staffId());
}
```

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: 实现候选排序**

```java
candidates.stream()
    .filter(AgentSnapshot::online)
    .filter(AgentSnapshot::hasCapacity)
    .sorted(Comparator.comparingInt(AgentSnapshot::activeCount)
        .thenComparing(AgentSnapshot::lastAssignedAt,
                Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(AgentSnapshot::staffId))
    .findFirst();
```

- [ ] **Step 4: 实现邀请接受/拒绝/过期**

限时邀请接受时调用端口原子预留容量和建立接管；重复接受返回原结果；过期扫描按 `offerId` 幂等，达到最大尝试次数后标记 `CLAIMABLE`。

- [ ] **Step 5: 增加并发接受、超时可重入和主动领取测试**

Expected: 两个并发接受者只有一个成功；重复过期扫描不增加重复邀请；领取后只有一个有效接管。

- [ ] **Step 6: 运行 P7-A 全部测试并提交**

```powershell
& "D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd" -s "D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml" -pl services/conversation-service -Dtest="com.bn.aliagent.conversation.agent.*,com.bn.aliagent.conversation.skill.*,com.bn.aliagent.conversation.queue.*,com.bn.aliagent.conversation.assignment.*" test
git add services/conversation-service/src/main/java/com/bn/aliagent/conversation/assignment services/conversation-service/src/test/java/com/bn/aliagent/conversation/assignment
git commit -m "feat(p7-a): add agent queue assignment core"
```

## 3. P7-B 实施任务

### Task B1：接管事实与互斥

**Files:**
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/takeover/TakeoverModels.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/takeover/TakeoverRepository.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/takeover/TakeoverService.java`
- Test: `services/conversation-service/src/test/java/com/bn/aliagent/conversation/takeover/TakeoverServiceTest.java`

- [ ] **Step 1: 编写双接管竞争测试**

```java
@Test
void 同一会话并发接管只能建立一个active事实() {
    assertEquals(1, runConcurrentTakeovers().stream().filter(Result::accepted).count());
    assertEquals(1, repository.activeCount(TENANT, CONVERSATION_ID));
}
```

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: 实现接管端口和服务**

接管命令必须引用已接受邀请或成功领取事实；Repository 提供 `createActiveIfAbsent`，冲突时返回现有接管而非覆盖。

- [ ] **Step 4: 增加重复 requestId、跨租户和无邀请拒绝测试并通过**

- [ ] **Step 5: 提交**

```powershell
git add services/conversation-service/src/main/java/com/bn/aliagent/conversation/takeover services/conversation-service/src/test/java/com/bn/aliagent/conversation/takeover
git commit -m "feat(p7-b): add exclusive human takeover"
```

### Task B2：人工消息授权与幂等

**Files:**
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/staffmessage/StaffMessageModels.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/staffmessage/StaffMessageRepository.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/staffmessage/StaffMessageService.java`
- Test: `services/conversation-service/src/test/java/com/bn/aliagent/conversation/staffmessage/StaffMessageServiceTest.java`

- [ ] **Step 1: 编写非当前客服拒绝和重复消息测试**

```java
@Test
void 只有当前客服可以发送且clientMessageId幂等() {
    assertThrows(StaffMessageException.class, () -> service.send(otherAgentCommand));
    Message first = service.send(ownerCommand);
    Message second = service.send(ownerCommand);
    assertEquals(first.id(), second.id());
}
```

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: 实现 `STAFF/PUBLIC` 消息命令**

服务只组装领域消息并调用端口持久化；发布实时通知必须发生在持久化确认后，由集成适配器实现。

- [ ] **Step 4: 增加空内容、CLOSED、跨租户测试并通过**

- [ ] **Step 5: 提交**

```powershell
git add services/conversation-service/src/main/java/com/bn/aliagent/conversation/staffmessage services/conversation-service/src/test/java/com/bn/aliagent/conversation/staffmessage
git commit -m "feat(p7-b): add idempotent staff messages"
```

### Task B3：转技能组和指定客服

**Files:**
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/transfer/TransferModels.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/transfer/TransferService.java`
- Test: `services/conversation-service/src/test/java/com/bn/aliagent/conversation/transfer/TransferServiceTest.java`

- [ ] **Step 1: 编写责任保留测试**

```java
@Test
void 指定客服接受前原客服仍保持active责任() {
    Transfer transfer = service.requestAgentTransfer(command);
    assertEquals(CURRENT_AGENT, takeoverPort.currentAgent(TENANT, CONVERSATION_ID));
    service.accept(transfer.id(), TARGET_AGENT, REQUEST_ID);
    assertEquals(TARGET_AGENT, takeoverPort.currentAgent(TENANT, CONVERSATION_ID));
}
```

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: 实现两类转派**

转技能组通过端口执行原子“创建新队列事实 + 将旧接管标记 TRANSFERRED”；指定客服先创建邀请，目标接受时再原子替换接管。校验同租户、技能、ONLINE、容量。

- [ ] **Step 4: 增加转派失败回滚、目标离线、容量满、重复接受测试**

- [ ] **Step 5: 提交**

```powershell
git add services/conversation-service/src/main/java/com/bn/aliagent/conversation/transfer services/conversation-service/src/test/java/com/bn/aliagent/conversation/transfer
git commit -m "feat(p7-b): add safe conversation transfers"
```

### Task B4：结束、关闭和主管异常处理

**Files:**
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/handoff/HandoffModels.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/handoff/HandoffService.java`
- Test: `services/conversation-service/src/test/java/com/bn/aliagent/conversation/handoff/HandoffServiceTest.java`

- [ ] **Step 1: 编写状态结果测试**

```java
@Test
void 普通结束恢复ai而显式关闭进入closed() {
    assertEquals("AI_ACTIVE", service.release(releaseCommand).conversationStatus());
    assertEquals("CLOSED", service.close(closeCommand).conversationStatus());
}
```

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: 实现 release/close/forceRelease**

主管强制释放必须包含非空原因并记录 `FORCE_RELEASED`；所有操作释放容量，重复提交返回相同终态。

- [ ] **Step 4: 增加关闭不可恢复、非主管强制操作、跨租户测试**

- [ ] **Step 5: 运行 P7-B 全部测试并提交**

```powershell
& "D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd" -s "D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml" -pl services/conversation-service -Dtest="com.bn.aliagent.conversation.takeover.*,com.bn.aliagent.conversation.staffmessage.*,com.bn.aliagent.conversation.transfer.*,com.bn.aliagent.conversation.handoff.*" test
git add services/conversation-service/src/main/java/com/bn/aliagent/conversation/handoff services/conversation-service/src/test/java/com/bn/aliagent/conversation/handoff
git commit -m "feat(p7-b): add human collaboration core"
```

## 4. P7-C 实施任务

### Task C1：副驾模型、端口和 Prompt

**Files:**
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/copilot/CopilotModels.java`
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/copilot/CopilotPorts.java`
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/copilot/CopilotPromptFactory.java`
- Test: `services/ai-orchestration-service/src/test/java/com/bn/aliagent/orchestration/copilot/CopilotPromptFactoryTest.java`

- [ ] **Step 1: 编写禁止思维链和事实引用测试**

```java
@Test
void prompt要求只给建议并禁止虚构事实和输出思维链() {
    String prompt = factory.create(context);
    assertTrue(prompt.contains("不得虚构订单、物流、退款或审批事实"));
    assertTrue(prompt.contains("不要输出思维过程"));
}
```

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: 实现模型与细粒度端口**

端口包括：`ConversationContextPort`、`KnowledgeContextPort`、`CommerceFactPort`、`AfterSaleFactPort`、`CopilotModelPort`、`StaffMessagePort`、`ConversationControlPort`。所有返回类型区分已验证事实、引用和不可用状态。

- [ ] **Step 4: 运行测试确认通过并提交**

```powershell
git add services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/copilot services/ai-orchestration-service/src/test/java/com/bn/aliagent/orchestration/copilot
git commit -m "feat(p7-c): add copilot domain contracts"
```

### Task C2：私有建议生成与 Inbox 幂等

**Files:**
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/copilot/CopilotRepository.java`
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/copilot/CopilotService.java`
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/copilot/CopilotSuggestionConsumer.java`
- Test: `services/ai-orchestration-service/src/test/java/com/bn/aliagent/orchestration/copilot/CopilotServiceTest.java`

- [ ] **Step 1: 编写 HUMAN_ACTIVE 和当前客服校验测试**

```java
@Test
void 非人工接管或非当前客服不得生成建议() {
    assertThrows(CopilotException.class, () -> service.generate(nonHumanCommand));
    assertThrows(CopilotException.class, () -> service.generate(foreignAgentCommand));
}
```

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: 实现生成流程**

流程：claim Inbox → 校验会话控制 → 加载上下文和只读事实 → 生成 Prompt → 调用模型 → 保存 `PRIVATE/GENERATED` 建议及版本和引用 → complete Inbox。依赖失败保存 `FAILED_RETRYABLE`，不调用模型拼接猜测事实。

- [ ] **Step 4: 增加重复事件、refreshNo、依赖失败和不保存思维链测试**

- [ ] **Step 5: 提交**

```powershell
git add services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/copilot services/ai-orchestration-service/src/test/java/com/bn/aliagent/orchestration/copilot
git commit -m "feat(p7-c): generate private copilot suggestions"
```

### Task C3：采纳、修改发送、忽略和差异审计

**Files:**
- Modify: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/copilot/CopilotService.java`
- Test: `services/ai-orchestration-service/src/test/java/com/bn/aliagent/orchestration/copilot/CopilotActionTest.java`

- [ ] **Step 1: 编写动作测试**

```java
@Test
void 修改发送保存原文最终内容和差异且只发送一次() {
    var result = service.modifyAndSend(command);
    assertEquals(original, result.originalContent());
    assertEquals(edited, result.finalContent());
    assertFalse(result.diffSummary().isBlank());
    service.modifyAndSend(command);
    assertEquals(1, staffMessagePort.sendCount());
}
```

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: 实现动作状态机**

`GENERATED/FAILED_RETRYABLE` 允许刷新；`GENERATED` 允许 accept、modify、ignore；终态重复命令返回已有动作。accept/modify 才调用 `StaffMessagePort`，使用动作 requestId 作为人工消息幂等依据；修改差异只保存可审计摘要，不保存模型思维链。

- [ ] **Step 4: 增加跨客服、跨租户、过期建议和重复动作测试**

- [ ] **Step 5: 提交**

```powershell
git add services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/copilot services/ai-orchestration-service/src/test/java/com/bn/aliagent/orchestration/copilot
git commit -m "feat(p7-c): audit copilot suggestion actions"
```

### Task C4：API client 和 WebSocket 客户端

**Files:**
- Modify: `frontend/packages/api-client/package.json`
- Create: `frontend/packages/api-client/src/index.ts`
- Create: `frontend/packages/api-client/src/agent-client.ts`
- Create: `frontend/packages/api-client/src/agent-socket.ts`

- [ ] **Step 1: 定义严格响应类型**

```ts
export interface AssignmentOffer {
  offerId: string
  conversationId: string
  skillGroupName: string
  expiresAt: string
}

export interface CopilotSuggestion {
  suggestionId: string
  content: string
  status: 'GENERATED' | 'FAILED_RETRYABLE' | 'ACCEPTED' | 'MODIFIED' | 'IGNORED'
  citations: Array<{ title: string; source: string }>
}
```

- [ ] **Step 2: 实现 REST client**

每个写请求生成 `requestId` 并设置 `Idempotency-Key`；401/403/409 使用结构化错误；不能从页面传入 tenantId 内部头。

- [ ] **Step 3: 实现 WebSocket 断线恢复**

客户端保存最后消息序列，重连后先调用 REST 补拉再恢复实时订阅。指数退避上限 10 秒，组件销毁时停止重连。

- [ ] **Step 4: TypeScript 检查并提交**

```powershell
pnpm --dir frontend --filter @aliagent/api-client exec tsc --noEmit
git add frontend/packages/api-client
git commit -m "feat(p7-c): add agent workbench api client"
```

如果公共 TypeScript 配置尚未提供导致命令失败，保留源码并在集成请求中写明缺失文件和所需配置，不得修改前端根目录。

### Task C5：最小客服工作台

**Files:**
- Create: `frontend/apps/aliagent-admin/index.html`
- Modify: `frontend/apps/aliagent-admin/package.json`
- Create: `frontend/apps/aliagent-admin/tsconfig.json`
- Create: `frontend/apps/aliagent-admin/vite.config.ts`
- Create: `frontend/apps/aliagent-admin/src/main.ts`
- Create: `frontend/apps/aliagent-admin/src/App.vue`
- Create: `frontend/apps/aliagent-admin/src/types.ts`
- Create: `frontend/apps/aliagent-admin/src/stores/agent-workbench.ts`
- Create: `frontend/apps/aliagent-admin/src/components/QueuePanel.vue`
- Create: `frontend/apps/aliagent-admin/src/components/ConversationPanel.vue`
- Create: `frontend/apps/aliagent-admin/src/components/CopilotPanel.vue`
- Create: `frontend/apps/aliagent-admin/src/components/TransferDialog.vue`
- Create: `frontend/apps/aliagent-admin/src/assets/main.css`

- [ ] **Step 1: 建立三栏工作台状态模型**

```ts
export const state = reactive({
  presence: 'OFFLINE' as 'OFFLINE' | 'ONLINE' | 'BUSY',
  offers: [] as AssignmentOffer[],
  queue: [] as QueueItem[],
  activeConversations: [] as ConversationSummary[],
  selectedConversationId: null as string | null,
  suggestion: null as CopilotSuggestion | null,
  pendingAction: null as string | null,
})
```

- [ ] **Step 2: 实现邀请/队列面板**

显示邀请倒计时、接受/拒绝和主动领取；过期或 409 后刷新服务端状态；pendingAction 防重复点击。

- [ ] **Step 3: 实现会话与实时消息面板**

选择会话后加载历史、建立 WebSocket、按 sequence 去重；发送消息使用稳定 `clientMessageId`；提供转派、结束和关闭入口。

- [ ] **Step 4: 实现副驾面板**

显示建议、引用和失败重试；支持刷新、直接采纳、编辑后发送和忽略。编辑框不得把建议自动发送。

- [ ] **Step 5: 实现在线状态、容量和响应式样式**

桌面为队列/会话/副驾三栏；窄屏切换面板，不隐藏关键邀请过期提示。

- [ ] **Step 6: 构建并提交**

```powershell
pnpm --dir frontend --filter @aliagent/admin build
git add frontend/apps/aliagent-admin frontend/packages/api-client
git commit -m "feat(p7-c): add copilot agent workbench"
```

## 5. 集成实施任务

### Task I1：合并和边界审查

- [ ] **Step 1:** 确认集成 Worktree 在 `codex/integration-p7` 且干净。
- [ ] **Step 2:** 按 P7-A → P7-B → P7-C 顺序使用 `--no-ff` 合并。
- [ ] **Step 3:** 每次合并后运行该任务定向测试并检查 `git diff --check`。
- [ ] **Step 4:** 如果任务修改越过冻结目录，停止并要求任务分支修正，不在集成时静默接受。

### Task I2：公共契约与 Flyway

**Files:**
- Create: `contracts/openapi/human-agent-v1.yaml`
- Create: `contracts/asyncapi/human-agent-events-v1.yaml`
- Create: `contracts/schemas/human-agent/skill-group-v1.schema.json`
- Create: `contracts/schemas/human-agent/human-queue-item-v1.schema.json`
- Create: `contracts/schemas/human-agent/assignment-offer-v1.schema.json`
- Create: `contracts/schemas/human-agent/copilot-suggestion-requested-v1.schema.json`
- Create: `contracts/schemas/human-agent/copilot-suggestion-generated-v1.schema.json`
- Create: `contracts/schemas/human-agent/copilot-suggestion-actioned-v1.schema.json`
- Create: `services/conversation-service/src/main/resources/db/migration/V6__p7_human_agent.sql`
- Create: `services/ai-orchestration-service/src/main/resources/db/migration/V3__p7_copilot.sql`

- [ ] **Step 1:** 先写契约校验测试并确认新契约缺失时失败。
- [ ] **Step 2:** 按 `contracts/standards/human-agent-p7.md` 实现 REST 和事件契约。
- [ ] **Step 3:** 创建 conversation_db 表、唯一约束、租户索引和状态 CHECK。
- [ ] **Step 4:** 创建 copilot 表、Inbox/Outbox 和建议动作唯一约束。
- [ ] **Step 5:** 在两个 PostgreSQL 空库执行 Flyway，从 V1 到最新版本成功。

### Task I3：后端适配与 Bean 装配

**Files:**
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/AgentAdministrationController.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/AgentQueueController.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/HumanCollaborationController.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/SupervisorQueueController.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/config/HumanAgentConfiguration.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/persistence/JdbcHumanAgentAdapters.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/realtime/HumanAgentRealtimePublisher.java`
- Modify: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/core/ConversationService.java`
- Modify: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/RealtimeWebSocketController.java`
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/config/CopilotConfiguration.java`
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/adapter/CopilotContextAdapters.java`
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/messaging/CopilotSuggestionRequestedConsumer.java`
- Modify: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/runtime/ReadOnlyWorkflowRunner.java`

- [ ] **Step 1:** 为 A/B Repository 和 Port 实现 JDBC 适配器，使用唯一约束和事务完成原子操作。
- [ ] **Step 2:** 增加 REST 控制器和可信身份/权限校验。
- [ ] **Step 3:** 把人工状态接入 P4 WebSocket，发布队列、邀请、接管、转派和人工消息事件。
- [ ] **Step 4:** 在用户消息进入 `HUMAN_ACTIVE` 会话时写 `copilot.suggestion.requested.v1` Outbox；不得写 `AIReplyRequested`。
- [ ] **Step 5:** 在 AI 公开流式回写前查询会话状态，非 `AI_ACTIVE` 返回明确抑制结果并完成事件消费，不能写公开内容。
- [ ] **Step 6:** 为 C 的端口连接现有模型、RAG、mall 只读能力和 P6 售后只读 API。
- [ ] **Step 7:** 将建议生成和动作事件接入 Outbox/Inbox，不使用 Noop 默认实现。

### Task I4：Gateway、前端工作区和环境

- [ ] **Step 1:** Gateway 转发 agent、supervisor、copilot REST 和 WebSocket 路径，继续清除客户端内部头。
- [ ] **Step 2:** 配置 STAFF/SUPERVISOR 权限映射和服务 JWT。
- [ ] **Step 3:** 补齐 `@aliagent/admin` 和 `@aliagent/api-client` 的公共 workspace 依赖和构建脚本。
- [ ] **Step 4:** Compose 添加 P7 所需队列、环境变量和测试配置，不提交真实密钥。

### Task I5：完整验收

- [ ] **Step 1:** 运行根 Maven 聚合测试，10 个模块全部 SUCCESS。
- [ ] **Step 2:** 运行前端 `pnpm --dir frontend install --frozen-lockfile` 和 `pnpm --dir frontend --filter @aliagent/admin build`。
- [ ] **Step 3:** 验证技能路由、优先级、邀请、拒绝、超时、主动领取和容量。
- [ ] **Step 4:** 验证并发接受/领取/转派只有一个有效接管。
- [ ] **Step 5:** 验证人工状态下 AI 不再公开发言，普通结束后恢复 AI。
- [ ] **Step 6:** 验证人工消息 WebSocket、断线补拉和重复 clientMessageId。
- [ ] **Step 7:** 验证副驾自动生成、刷新、采纳、修改、忽略和差异审计。
- [ ] **Step 8:** 验证模型、RAG、mall、Redis、MQ 故障安全降级。
- [ ] **Step 9:** 验证 MEMBER/STAFF/SUPERVISOR 权限和跨租户拒绝。
- [ ] **Step 10:** 清理所有 `test-`/`rag-test-` PostgreSQL、Redis 和 MQ 数据。
- [ ] **Step 11:** 运行 `git diff --check` 和 `git status --short`，集成工作区必须干净。

## 6. 完成条件

- A/B/C 各自在独立 Worktree 完成并提交，目录边界无交叉。
- 集成分支按 A → B → C 合并并解决公共连接。
- 所有契约、Flyway、Maven、前端构建和端到端验收通过。
- 人工接管期间 AI 没有任何消费者可见公开回复。
- 不推送、不创建 PR、不合入 `master`，直到用户确认阶段验收报告。
