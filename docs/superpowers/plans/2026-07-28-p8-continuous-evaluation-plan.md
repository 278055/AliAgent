# P8 持续评测实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立匿名评测样本、版本化评测集、离线回放、版本对比和不可伪造发布门禁，并接入现有版本治理。

**Architecture:** `evaluation-service` 作为评测事实来源，A 负责摄入与数据集，B 负责回放与评分，C 负责门禁与最小控制台。A/B/C 通过各自稳定端口并行开发；公共契约、Flyway、Gateway、跨包装配、P5/知识版本发布连接和 E2E 由集成会话统一完成。

**Tech Stack:** Java 17、Spring Boot 3.4.5、Spring JDBC、PostgreSQL/Flyway、Vue 3、TypeScript、Vite、Maven、pnpm。

---

## 文件与所有权

### P8-A 独占

- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/intake/`
- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/anonymization/`
- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/candidate/`
- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/dataset/`
- 上述包对应的 `src/test/java/` 目录

### P8-B 独占

- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/replay/`
- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/runner/`
- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/scoring/`
- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/judge/`
- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/comparison/`
- 上述包对应的 `src/test/java/` 目录

### P8-C 独占

- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/gate/`
- 对应的 `src/test/java/com/bn/aliagent/evaluation/gate/`
- `frontend/apps/aliagent-admin/src/evaluation/`
- `frontend/packages/api-client/src/evaluation-client.ts`
- `frontend/packages/api-client/src/index.ts`
- `frontend/apps/aliagent-admin/src/App.vue`
- `frontend/apps/aliagent-admin/src/assets/main.css`

### 集成会话独占

- `contracts/`
- `services/evaluation-service/src/main/resources/db/migration/`
- `services/evaluation-service/src/main/resources/application*.yml`
- `services/evaluation-service/pom.xml`
- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/EventReceiverController.java`
- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/EvaluationServiceApplication.java`
- `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/governance/`
- `services/knowledge-service/` 中知识快照发布连接
- `services/gateway-service/`、根 `pom.xml`、`deploy/`、CI、E2E 工具

## 并行接口约束

A/B/C 不得直接 import 其他任务尚未合并的类型。每个任务在自己的包中定义端口：

```java
// A 对外暴露，集成会话负责适配到 B。
public interface EvaluationDatasetReader {
    PublishedDatasetVersion requirePublished(String tenantId, UUID datasetVersionId);
}

// B 对外暴露，集成会话负责适配到 C。
public interface EvaluationResultReader {
    EvaluationRunSummary requireCompleted(String tenantId, UUID runId);
}

// C 对外暴露，P5 和知识服务通过内部 HTTP/服务适配器调用。
public interface GateDecisionVerifier {
    VerifiedGateDecision verify(GateProof proof, GateTarget target);
}
```

任务分支只能提交独占目录。若发现必须修改公共文件，把接口缺口和建议改动写入交付报告，由集成会话完成。

---

### Task 1：P8-A 事件摄入与 Inbox 幂等

**Files:**
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/intake/EvaluationEventEnvelope.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/intake/EventInbox.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/intake/EventIntakeService.java`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/intake/EventIntakeServiceTest.java`

- [ ] **Step 1：写失败测试**

覆盖：有效事件只处理一次、重复 `eventId` 返回既有结果、未知事件版本拒绝、租户为空拒绝、失败重试不生成第二个候选。

```java
@Test
void duplicateEventCreatesOnlyOneCandidate() {
    var inbox = new InMemoryEventInbox();
    var sink = new RecordingCandidateSink();
    var service = new EventIntakeService(inbox, sanitizer, sink, clock);
    var event = Fixtures.feedbackEvent("test-p8-a-tenant", UUID.randomUUID());

    service.accept(event);
    service.accept(event);

    assertEquals(1, sink.accepted().size());
    assertEquals(IntakeStatus.COMPLETED, inbox.require(event.eventId()).status());
}
```

- [ ] **Step 2：确认测试先失败**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest=EventIntakeServiceTest test`

Expected: FAIL，原因是摄入类型尚不存在。

- [ ] **Step 3：实现最小摄入模型**

`EvaluationEventEnvelope` 固定字段：`eventId`、`eventType`、`eventVersion`、`occurredAt`、`tenantId`、`traceId`、`producer`、`payload`。只接受冻结的五类 v1 事件。`EventInbox` 使用 `reserve/complete/fail/require` 语义，`EventIntakeService` 必须先成功预留 Inbox，再匿名化并写候选。

- [ ] **Step 4：运行测试并提交小步提交**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest=EventIntakeServiceTest test`

Expected: PASS。

Commit: `feat(p8-a): add idempotent evaluation intake`

### Task 2：P8-A 两阶段匿名化

**Files:**
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/anonymization/AnonymizationPolicy.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/anonymization/DeterministicAnonymizer.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/anonymization/PublicDatasetAnonymizer.java`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/anonymization/DeterministicAnonymizerTest.java`

- [ ] **Step 1：写失败测试**

测试手机号、邮箱、详细地址、证件号、Token 被删除；用户/客服/订单/会话标识变为租户内 HMAC；无法可靠清理的文本进入 `QUARANTINED`；公共匿名化使用不同盐值且必须携带显式授权。

```java
@Test
void removesSensitiveFieldsBeforeCandidatePersistence() {
    var result = anonymizer.anonymize(Fixtures.payloadWithPii(), "test-p8-a-tenant");
    assertEquals(AnonymizationStatus.SAFE, result.status());
    assertFalse(result.canonicalJson().contains("13800138000"));
    assertFalse(result.canonicalJson().contains("Bearer "));
    assertEquals("anon-v1", result.ruleVersion());
}
```

- [ ] **Step 2：确认测试先失败**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest=*AnonymizerTest test`

Expected: FAIL。

- [ ] **Step 3：实现匿名化**

使用确定性字段白名单、敏感键黑名单、文本模式清理和 HMAC-SHA256。密钥通过构造器端口注入，禁止硬编码。原始 payload 只存在于方法调用内，不进入 Inbox、日志或异常文本。公共匿名化要求 `DatasetShareAuthorization` 为有效状态，并重新生成关联标识。

- [ ] **Step 4：验证并提交**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest=*AnonymizerTest test`

Expected: PASS。

Commit: `feat(p8-a): anonymize evaluation candidates`

### Task 3：P8-A 候选审核、保留策略与共享授权

**Files:**
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/candidate/EvaluationCandidate.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/candidate/CandidateReviewService.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/candidate/CandidateRetentionService.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/candidate/CandidateController.java`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/candidate/CandidateReviewServiceTest.java`

- [ ] **Step 1：写状态和保留期失败测试**

覆盖 `PENDING_REVIEW/QUARANTINED/ACCEPTED/REJECTED/EXPIRED`，接受时必须有期望结果与标签；未审核 30 天过期；拒绝/隔离 7 天删除正文但保留摘要；跨租户审核拒绝。

- [ ] **Step 2：实现审核与清理服务**

```java
public record CandidateReviewCommand(
        UUID candidateId, String tenantId, String reviewerId,
        ReviewAction action, Map<String, Object> expected,
        Set<String> labels, String reason) { }
```

控制器只从可信请求上下文取得租户和主体；请求体中的租户字段不得用于授权。保留期清理使用 `Clock`，保证测试确定性和任务幂等。

- [ ] **Step 3：验证并提交**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest=*Candidate*Test test`

Expected: PASS。

Commit: `feat(p8-a): add candidate review lifecycle`

### Task 4：P8-A 不可变评测集版本

**Files:**
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/dataset/EvaluationDataset.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/dataset/PublishedDatasetVersion.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/dataset/EvaluationDatasetService.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/dataset/EvaluationDatasetReader.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/dataset/DatasetController.java`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/dataset/EvaluationDatasetServiceTest.java`

- [ ] **Step 1：写失败测试**

覆盖草稿可编辑、只允许加入 `ACCEPTED` 候选、发布后快照不可变、摘要稳定、私有样本默认不能公开、显式授权后二次匿名化才能进入公共版本。

- [ ] **Step 2：实现领域模型**

样本快照固定输入、期望意图、允许/禁止工具、参数约束、引用要求、事实断言、安全标签、期望转人工行为、权重与适用指标。内容摘要对规范化 JSON 使用 SHA-256。

- [ ] **Step 3：验证 P8-A 全量测试**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest='*Intake*,*Anonym*,*Candidate*,*Dataset*' test`

Expected: PASS，0 failures/errors。

Run: `git diff --check`

Commit: `feat(p8-a): publish immutable evaluation datasets`

---

### Task 5：P8-B 版本清单与确定性 Mock 回放

**Files:**
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/replay/EvaluationManifest.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/replay/ReplayFixture.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/runner/MockReplayRunner.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/runner/EvaluationRunService.java`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/runner/MockReplayRunnerTest.java`

- [ ] **Step 1：写失败测试**

测试版本清单缺失即拒绝、同一清单与 fixture 生成相同摘要、Mock 不触发真实外部调用、普通问答/RAG/订单查询/工具故障/转人工均可回放。

```java
public record EvaluationManifest(
        UUID promptVersionId, UUID workflowVersionId, UUID modelVersionId,
        UUID knowledgeVersionId, String toolContractVersion,
        String deterministicRuleVersion, UUID datasetVersionId,
        String scoringPolicyVersion, String judgeConfigVersion) { }
```

- [ ] **Step 2：实现端口隔离**

定义 B 自有的 `DatasetSnapshotPort`、`OrchestrationReplayPort`、`KnowledgeSnapshotPort`。测试使用内存适配器；集成会话再连接 A、P5 和知识服务。禁止 import A 的类型。

- [ ] **Step 3：验证并提交**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest=*MockReplay*Test test`

Expected: PASS。

Commit: `feat(p8-b): add deterministic mock replay`

### Task 6：P8-B DashScope 审批与预算执行

**Files:**
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/runner/DashScopeApproval.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/runner/DashScopeBudgetLedger.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/runner/DashScopeReplayRunner.java`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/runner/DashScopeReplayRunnerTest.java`

- [ ] **Step 1：写失败测试**

覆盖未审批、过期、撤销、租户不匹配、清单摘要不匹配、样本/Token/费用超限、重试计费和预算耗尽停止未执行样本。

- [ ] **Step 2：实现审批和账本**

```java
public record DashScopeLimits(int maxSamples, long maxInputTokens,
        long maxOutputTokens, BigDecimal maxCost) { }

public interface DashScopeReplayClient {
    ModelReplayResponse execute(ModelReplayRequest request);
}
```

API Key 只存在于 `DashScopeReplayClient` 的生产适配器，领域任务和结果不得携带凭证。每次尝试先预留预算，完成后登记实际值，失败重试也记账。

- [ ] **Step 3：验证并提交**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest=*DashScope*Test test`

Expected: PASS。

Commit: `feat(p8-b): control dashscope regression budget`

### Task 7：P8-B 确定性评分与 Judge 辅助

**Files:**
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/scoring/DeterministicScorer.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/scoring/MetricEvidence.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/judge/JudgeScorer.java`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/scoring/DeterministicScorerTest.java`

- [ ] **Step 1：写失败测试**

覆盖意图、工具、参数、RAG 命中与引用、事实一致性、风险、转人工、客服采纳、首 Token、总耗时和成本。验证 Judge 无法把确定性红线失败改为通过，Judge 不可用时不乐观通过。

- [ ] **Step 2：实现评分证据**

每个指标输出 `PASS/FAIL/NOT_APPLICABLE/UNAVAILABLE`、实际值、期望值、证据摘要和安全红线标记。开放回答 Judge 固定模型、Prompt 及配置版本。

- [ ] **Step 3：验证并提交**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest='*Scorer*,*Judge*' test`

Expected: PASS。

Commit: `feat(p8-b): score evaluation evidence`

### Task 8：P8-B 结果聚合与版本对比

**Files:**
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/comparison/EvaluationRunSummary.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/comparison/EvaluationResultReader.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/comparison/VersionComparisonService.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/comparison/ComparisonController.java`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/comparison/VersionComparisonServiceTest.java`

- [ ] **Step 1：写失败测试**

只允许相同评测集、评分策略、执行模式和知识快照的结果比较；输出绝对值、差值、标签/风险分层变化和失败样本。

- [ ] **Step 2：实现聚合和查询 API**

结果不可只保存总分。聚合必须保留 `metric + label + riskLevel` 三个维度及样本证据引用。

- [ ] **Step 3：验证 P8-B 全量测试**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest='*Replay*,*Runner*,*Scorer*,*Judge*,*Comparison*' test`

Expected: PASS，0 failures/errors。

Run: `git diff --check`

Commit: `feat(p8-b): compare evaluation versions`

---

### Task 9：P8-C 门禁策略与判定

**Files:**
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/gate/GatePolicy.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/gate/GateEvaluationService.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/gate/GateResultPort.java`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/gate/GateEvaluationServiceTest.java`

- [ ] **Step 1：写失败测试**

测试相对退化超过阈值为 `FAIL`；跨租户、隐私泄露、虚构关键事实、绕过确认/审批、禁止工具任一红线失败；总体提升不能覆盖红线；缺失所需 DashScope 结果时不能通过。

- [ ] **Step 2：实现独立结果端口**

C 定义 `GateResultPort` 及本包 DTO，不 import B。集成会话通过适配器把 B 的 `EvaluationResultReader` 接入。

- [ ] **Step 3：验证并提交**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest=*GateEvaluation*Test test`

Expected: PASS。

Commit: `feat(p8-c): enforce evaluation gate policy`

### Task 10：P8-C 签名 Gate Decision 与验签

**Files:**
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/gate/GateDecision.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/gate/GateDecisionSigner.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/gate/GateDecisionVerifier.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/gate/GateController.java`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/gate/GateDecisionSignerTest.java`

- [ ] **Step 1：写失败测试**

覆盖 RSA/ECDSA 签名成功、内容篡改、过期、撤销、跨租户、目标版本不符、策略不符和未知 `keyId` 拒绝。

```java
public record GateTarget(String tenantId, String artifactType,
        UUID artifactVersionId, String manifestDigest) { }

public record GateProof(String canonicalPayload, String signature, String keyId) { }
```

- [ ] **Step 2：实现规范化和签名**

使用 Java 标准加密 API。私钥通过 `SigningKeyProvider` 注入；测试生成临时密钥对。证明固定目标、版本清单摘要、基线摘要、评测集、评分/门禁策略、任务和结果摘要、签发/过期时间。

- [ ] **Step 3：验证并提交**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest='*GateDecision*,*GateEvaluation*' test`

Expected: PASS。

Commit: `feat(p8-c): sign evaluation gate decisions`

### Task 11：P8-C 最小评测控制台

**Files:**
- Create: `frontend/packages/api-client/src/evaluation-client.ts`
- Modify: `frontend/packages/api-client/src/index.ts`
- Create: `frontend/apps/aliagent-admin/src/evaluation/EvaluationConsole.vue`
- Create: `frontend/apps/aliagent-admin/src/evaluation/types.ts`
- Modify: `frontend/apps/aliagent-admin/src/App.vue`
- Modify: `frontend/apps/aliagent-admin/src/assets/main.css`

- [ ] **Step 1：定义类型安全客户端**

提供候选审核、评测集、任务启动、版本比较、失败样本、门禁结果 API。统一使用现有 `AgentClient` 的 base URL、JWT 和请求 ID 约定，不复制身份逻辑。

- [ ] **Step 2：实现六块最小界面**

控制台包含候选审核、评测集管理、任务启动、版本对比、失败样本和门禁结果。敏感正文、密钥和原始事件 payload 不进入类型定义或 DOM。

- [ ] **Step 3：构建验证**

Run: `cd frontend; pnpm --filter @aliagent/api-client exec tsc --noEmit`

Expected: PASS。

Run: `cd frontend; pnpm --filter @aliagent/admin build`

Expected: PASS。

Run: `git diff --check`

Commit: `feat(p8-c): add evaluation console`

---

### Task 12：集成会话公共契约与数据库

**Files:**
- Create/Modify: `contracts/openapi/evaluation-v1.yaml`
- Create/Modify: `contracts/asyncapi/evaluation-events-v1.yaml`
- Create: `contracts/schemas/evaluation/*.schema.json`
- Create: `services/evaluation-service/src/main/resources/db/migration/V2__p8_continuous_evaluation.sql`
- Modify: `services/evaluation-service/src/main/resources/application*.yml`
- Modify: `services/evaluation-service/pom.xml`（仅确有依赖缺口时）

- [ ] 先写契约验证失败用例，再定义五类输入事件、候选/数据集/任务/结果/门禁 API 和 Gate Decision Schema。
- [ ] 建立 Inbox、候选、审核、授权、评测集版本、样本快照、任务、结果、预算、策略、证明和撤销表；所有租户表带 `tenant_id` 和租户内唯一约束。
- [ ] 将 A/B/C 内存端口替换或装配为 JDBC 适配器；每个跨服务写入和事件消费保持幂等。
- [ ] 在空 `evaluation_db` 执行 Flyway V1-V2，重复执行无错误。

### Task 13：集成会话连接版本治理与知识快照

**Files:**
- Modify: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/governance/VersionGovernanceService.java`
- Modify: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/governance/VersionAdministrationController.java`
- Modify/Create: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/catalog/`
- Modify: `services/gateway-service/` 路由配置

- [ ] 先写发布/assign 缺证明、伪造、过期、跨租户、版本不匹配均拒绝的测试；回滚已发布旧版本仍走独立审计路径。
- [ ] 为 Prompt、工作流、模型、知识发布和租户灰度增加 `GateDecisionVerifier` 客户端；验证失败默认拒绝。
- [ ] 知识发布生成不可变 `knowledgeVersionId` 快照，包含文档版本、索引参数、Embedding、混合召回和重排配置摘要。
- [ ] 将 evaluation API 通过 Gateway 暴露给授权管理员，继续移除伪造内部头并注入可信上下文。

### Task 14：集成会话端到端验收

- [ ] 按 `P8-A → P8-B → P8-C` 顺序合并；仅集成会话解决公共装配和契约冲突。
- [ ] 运行根聚合：`D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd test`，要求 10 个模块全部 `SUCCESS`。
- [ ] 运行：`cd frontend; pnpm --filter @aliagent/api-client exec tsc --noEmit` 与 `pnpm --filter @aliagent/admin build`。
- [ ] 运行：`docker compose -f deploy\docker-compose.yml config --quiet`。
- [ ] 空库执行 evaluation Flyway；跨租户候选、评测集、结果和证明访问全部拒绝。
- [ ] E2E 覆盖：线上错误事件进入候选、人工审核、发布评测集、Mock 对比、红线阻断、受控 DashScope 预算、证明签发、P5/知识发布验签、显式发布。
- [ ] 重复事件、重复任务、MQ 重投不产生重复事实；模型/Judge/知识/门禁服务故障均默认安全失败。
- [ ] 清理全部 `test-`/`rag-test-` PostgreSQL、Redis 和 MQ 资源，并记录清理前后数量。
- [ ] 运行 `git diff --check`，确认集成工作区干净后提交 P8 集成结果；不得直接合入 `master`。
