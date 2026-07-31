# P9 运营洞察实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立租户隔离、可修订、可审计的运营洞察链路，把匿名业务事件转化为核心指标、问题雷达、投诉主题、知识缺口和受控自然语言查询。

**Architecture:** `insight-service` 独占 `insight_db`，通过 Inbox 摄入版本化事件并在持久化前匿名化；P9-A、P9-B、P9-C 分别实现事实、聚合雷达、主题查询领域能力。公共契约、Flyway、持久化适配、生产者、Gateway、前端入口和端到端装配由集成会话统一完成，避免并行任务修改同一文件。

**Tech Stack:** Java 17、Spring Boot 3.4.5、Spring JDBC、PostgreSQL 17 + pgvector、Flyway、RabbitMQ、Vue 3、TypeScript、Vite、Maven、pnpm。

---

## 1. 文件结构与所有权

### P9-A 独占

- `services/insight-service/src/main/java/com/bn/aliagent/insight/intake/`
- `services/insight-service/src/main/java/com/bn/aliagent/insight/fact/`
- `services/insight-service/src/main/java/com/bn/aliagent/insight/retention/`
- 对应 `src/test/java/` 目录

### P9-B 独占

- `services/insight-service/src/main/java/com/bn/aliagent/insight/aggregate/`
- `services/insight-service/src/main/java/com/bn/aliagent/insight/metric/`
- `services/insight-service/src/main/java/com/bn/aliagent/insight/radar/`
- 对应 `src/test/java/` 目录

### P9-C 独占

- `services/insight-service/src/main/java/com/bn/aliagent/insight/topic/`
- `services/insight-service/src/main/java/com/bn/aliagent/insight/gap/`
- `services/insight-service/src/main/java/com/bn/aliagent/insight/query/`
- `frontend/apps/aliagent-admin/src/insight/`
- 对应测试目录

### 集成会话独占

- `contracts/`
- `services/insight-service/src/main/resources/`
- `services/insight-service/pom.xml`
- `services/insight-service/src/main/java/com/bn/aliagent/insight/` 根包、`persistence/`、`adapter/`、`security/`
- `services/gateway-service/`
- `mall/` 和其他服务的 P9 生产者/核验适配
- `frontend/packages/`、`frontend/apps/aliagent-admin/src/App.vue`、全局样式
- 根工程、部署、CI、E2E

---

## 2. P9-A：事件摄入与脱敏事实

### Task 1：事件信封、策略与 Inbox 幂等

**Files:**
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/intake/InsightEventEnvelope.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/intake/InsightEventPolicy.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/intake/InsightEventInbox.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/intake/InsightIntakeRecord.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/intake/InsightIntakeService.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/intake/InsightIntakeServiceTest.java`

- [ ] **Step 1：写失败测试**

覆盖可信租户一致、未知类型/版本拒绝、同一生产者重复事件只投影一次、不同生产者相同 UUID 不冲突、失败可重试且不产生第二份事实。

```java
@Test
void duplicateProducerEventCreatesOneFact() {
    var inbox = new InsightEventInbox.InMemory();
    var facts = new RecordingFactSink();
    var service = new InsightIntakeService(
            new InsightEventPolicy(Map.of("conversation.completed", Set.of(1))),
            inbox, payload -> payload, facts, Clock.fixed(NOW, ZoneOffset.UTC));
    var event = Fixtures.completed("test-p9-a", "conversation-service", UUID.randomUUID());

    service.accept(event, "test-p9-a");
    service.accept(event, "test-p9-a");

    assertEquals(1, facts.values().size());
    assertEquals(IntakeStatus.COMPLETED,
            inbox.require("test-p9-a", "conversation-service", event.eventId()).status());
}
```

- [ ] **Step 2：运行测试并确认先失败**

Run:

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/insight-service -Dtest=InsightIntakeServiceTest test
```

Expected: FAIL，原因是 `InsightEventEnvelope`、`InsightEventInbox` 或 `InsightIntakeService` 尚不存在。

- [ ] **Step 3：实现最小领域模型**

```java
public record InsightEventEnvelope(
        UUID eventId, String eventType, int eventVersion, Instant occurredAt,
        String tenantId, String traceId, String producer, Map<String, Object> payload) {
    public InsightEventEnvelope {
        payload = Map.copyOf(payload);
    }
}

public interface InsightEventInbox {
    Optional<InsightIntakeRecord> reserve(InsightEventEnvelope event, String digest, Instant now);
    void complete(String tenantId, String producer, UUID eventId, Instant now);
    void fail(String tenantId, String producer, UUID eventId, Instant now);
    InsightIntakeRecord require(String tenantId, String producer, UUID eventId);
}
```

`InsightIntakeService.accept()` 固定顺序：验证可信租户和事件策略、计算不含原始正文的摘要、预留 Inbox、匿名化、写事实、完成 Inbox；异常时标记失败并重新抛出。异常消息不得包含 payload。

- [ ] **Step 4：验证并提交**

Run: 上述定向测试。

Expected: PASS，0 failures/errors。

Commit: `feat(p9-a): add idempotent insight intake`

### Task 2：确定性匿名化与事实投影

**Files:**
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/fact/InsightAnonymizationPolicy.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/fact/DeterministicFactAnonymizer.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/fact/AnonymizedFact.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/fact/InsightFactProjector.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/fact/InsightFactSink.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/fact/DeterministicFactAnonymizerTest.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/fact/InsightFactProjectorTest.java`

- [ ] **Step 1：写敏感数据失败测试**

```java
@Test
void stripsPiiAndCreatesTenantScopedReferences() {
    var input = Map.<String, Object>of(
            "orderId", "ORDER-1", "phone", "13800138000",
            "text", "联系 test@example.com，地址北京市朝阳区测试路1号");
    var result = anonymizer.anonymize(input, "test-p9-a");

    assertFalse(result.canonicalJson().contains("13800138000"));
    assertFalse(result.canonicalJson().contains("test@example.com"));
    assertFalse(result.canonicalJson().contains("测试路1号"));
    assertTrue(result.canonicalJson().contains("ref-"));
    assertEquals("insight-anon-v1", result.ruleVersion());
}
```

同时验证相同业务标识在不同租户产生不同引用，`PRIVATE KEY` 等不可安全处理内容进入隔离且不写事实。

- [ ] **Step 2：确认测试先失败**

Run:

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/insight-service -Dtest='*AnonymizerTest,*FactProjectorTest' test
```

Expected: FAIL。

- [ ] **Step 3：实现匿名化和事实模型**

```java
public record AnonymizedFact(
        UUID factId, String tenantId, FactType type, Instant occurredAt,
        Map<String, String> dimensions, Map<String, BigDecimal> measures,
        Map<String, String> evidenceRefs, UUID sourceEventId, int revision,
        UUID supersedesFactId, String anonymizationRuleVersion) {
    public AnonymizedFact {
        dimensions = Map.copyOf(dimensions);
        measures = Map.copyOf(measures);
        evidenceRefs = Map.copyOf(evidenceRefs);
    }
}

public interface InsightFactSink {
    void append(AnonymizedFact fact);
}
```

匿名化策略删除敏感键，清理手机号、邮箱、证件号、Token 和地址，对 `orderId/conversationId/userId/agentId` 使用 `HMAC-SHA256(tenantId + ":" + value)`。投影器只从事件类型对应白名单字段创建事实，金额只接受生产者提供的数值。

- [ ] **Step 4：验证并提交**

Expected: 两组测试 PASS。

Commit: `feat(p9-a): project anonymized insight facts`

### Task 3：迟到修订、重算清单与保留策略

**Files:**
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/fact/LateEventPolicy.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/fact/FactRevisionService.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/fact/RecalculationRequest.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/retention/InsightRetentionPolicy.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/retention/InsightRetentionService.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/fact/FactRevisionServiceTest.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/retention/InsightRetentionServiceTest.java`

- [ ] **Step 1：写时间边界失败测试**

验证 7 天内生成自动重算窗口，超过 7 天生成 `MANUAL_REVIEW`；正常发生在订单创建 30 天后的退款不因业务年龄被判迟到；事实修订保留 `supersedesFactId`；90 天清理事实和向量候选，但保留雷达摘要引用。

```java
@Test
void arrivalAfterSevenDaysRequiresManualRecalculation() {
    var decision = policy.classify(
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-07-08T00:00:01Z"));
    assertEquals(LateEventDecision.MANUAL_REVIEW, decision);
}
```

- [ ] **Step 2：实现可测试的时钟与端口**

```java
public interface RecalculationQueue {
    void enqueue(RecalculationRequest request);
}

public interface RetentionTarget {
    int deleteExpiredFacts(String tenantId, Instant before, int batchSize);
    int deleteExpiredTopicMembers(String tenantId, Instant before, int batchSize);
}
```

所有清理按租户、截止时间和批次执行；重复执行结果稳定。长期审计不属于 A 的删除目标。

- [ ] **Step 3：验证 P9-A 全量并提交**

Run:

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/insight-service -Dtest='*Intake*,*Anonym*,*Fact*,*Retention*' test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/insight-service test
git diff --check
```

Expected: PASS，0 failures/errors；工作区仅包含 A 独占目录。

Commit: `feat(p9-a): add revisions and retention policy`

---

## 3. P9-B：指标聚合与问题雷达

### Task 4：不可变指标定义与核心口径

**Files:**
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/metric/MetricDefinition.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/metric/MetricCatalog.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/metric/MetricFact.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/metric/MetricCalculator.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/metric/MetricCalculatorTest.java`

- [ ] **Step 1：为每个口径写失败测试**

至少覆盖：未支付取消不进入退款分母、部分退款金额累计但订单只计一次、后续退款修订订单归属周期、重复转人工只计一次、AI 消息不作为人工首响、满意度只接受显式用户评价、未评价不进入满意度但进入评价覆盖率分母、一个会话多原因知识缺口总体只计一次。

```java
@Test
void refundBelongsToPaidOrderCreationPeriod() {
    var result = calculator.calculate(REFUND_RATE, Fixtures.orderPeriodFacts());
    assertEquals(new BigDecimal("0.5000"), result.value());
    assertEquals(1L, result.numerator());
    assertEquals(2L, result.denominator());
    assertEquals(LocalDate.of(2026, 6, 1), result.periodStart());
}
```

- [ ] **Step 2：实现指标定义和计算器**

```java
public record MetricDefinition(
        String metric, int version, TimeSemantics timeSemantics,
        Set<String> allowedDimensions, int minimumSampleSize) {
    public MetricDefinition {
        allowedDimensions = Set.copyOf(allowedDimensions);
    }
}

public record MetricValue(
        String tenantId, String metric, int definitionVersion,
        Instant windowStart, Instant windowEnd, long numerator,
        long denominator, BigDecimal value, long sampleSize) { }
```

`MetricCatalog.v1()` 固定七类指标定义及允许维度。除法使用明确舍入规则；分母为零返回 `NO_DATA`，不得返回零值假装有数据。

- [ ] **Step 3：验证并提交**

Run: `mvn ... -Dtest=MetricCalculatorTest test`

Expected: PASS。

Commit: `feat(p9-b): define operational metrics`

### Task 5：小时/日聚合与修订版本

**Files:**
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/aggregate/AggregationWindow.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/aggregate/AggregateRevision.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/aggregate/AggregationStore.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/aggregate/HourlyAggregationService.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/aggregate/DailyFinalizationService.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/aggregate/AggregationRetentionService.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/aggregate/AggregationServiceTest.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/aggregate/AggregationRetentionServiceTest.java`

- [ ] **Step 1：写聚合修订失败测试**

验证租户业务时区的小时/日边界、日终固化、迟到事实生成 revision 2 且保留 revision 1、重复事实不重复计数、维度顺序不影响唯一键；小时聚合只清理 180 天前数据，日聚合只清理 2 年前数据，阈值/雷达/审核/安全审计不属于该清理服务目标。

- [ ] **Step 2：实现聚合端口和服务**

```java
public interface AggregationStore {
    Optional<AggregateRevision> latest(AggregateKey key);
    AggregateRevision append(AggregateRevision revision, int expectedPreviousRevision);
}

public record AggregateKey(
        String tenantId, String metric, int definitionVersion,
        WindowGranularity granularity, Instant windowStart,
        SortedMap<String, String> dimensions) { }
```

小时增量只追加新修订；日终服务读取当天最终小时修订，生成日聚合。乐观版本不匹配抛出冲突，由调度器重试，禁止覆盖历史行。

```java
public interface AggregateRetentionTarget {
    int deleteHourlyBefore(String tenantId, Instant cutoff, int batchSize);
    int deleteDailyBefore(String tenantId, LocalDate cutoff, int batchSize);
}
```

`AggregationRetentionService` 使用注入的 `Clock`，按租户分批清理小时聚合 180 天前数据和日聚合 2 年前数据；重复执行必须幂等。

- [ ] **Step 3：验证并提交**

Commit: `feat(p9-b): aggregate hourly and daily metrics`

### Task 6：异常检测与问题雷达状态机

**Files:**
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/radar/ThresholdRule.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/radar/TrendBaseline.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/radar/AnomalyDetector.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/radar/ProblemRadar.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/radar/RadarService.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/radar/AnomalyDetectorTest.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/radar/RadarServiceTest.java`

- [ ] **Step 1：写异常和状态失败测试**

验证 7/28 天同周期趋势、绝对阈值优先、样本不足、同租户/问题/维度/窗口去重、不同租户不合并，以及仅允许冻结的四条状态迁移。

```java
@Test
void rejectsIllegalRadarTransition() {
    var radar = Fixtures.openRadar();
    assertThrows(IllegalStateException.class,
            () -> radar.transition(RadarStatus.RESOLVED, "test-operator", "未确认直接解决"));
}
```

- [ ] **Step 2：实现雷达固定证据**

```java
public record ProblemRadar(
        UUID radarId, String tenantId, String problemType,
        Map<String, String> dimensions, Instant windowStart, Instant windowEnd,
        int metricVersion, int thresholdVersion, int aggregateRevision,
        BaselineReference baseline, List<String> triggerReasons,
        RiskLevel riskLevel, RadarStatus status, long version) { }
```

`RadarService.detect()` 先检查样本量，再计算趋势和绝对阈值；合并键固定为租户、问题类型、规范化维度和检测窗口。处置操作要求主管角色，由集成控制器传入可信主体。

- [ ] **Step 3：验证 P9-B 全量并提交**

Run:

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/insight-service -Dtest='*Metric*,*Aggregation*,*Radar*,*Anomaly*' test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/insight-service test
git diff --check
```

Expected: PASS，0 failures/errors。

Commit: `feat(p9-b): detect and manage problem radars`

---

## 4. P9-C：主题、知识缺口、受控查询与页面

### Task 7：规则分类与版本化聚类

**Files:**
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/topic/TopicRuleClassifier.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/topic/EmbeddingPort.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/topic/ClusteringPort.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/topic/TopicClusterService.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/topic/TopicClusterServiceTest.java`

- [ ] **Step 1：写规则优先和租户隔离失败测试**

验证隐私/欺诈/监管/资金先由规则分类，不发送模型；其余文本才向量化；不同租户绝不进入同一聚类；每个聚类固定规则、Embedding、算法参数和成员快照版本。

- [ ] **Step 2：实现端口**

```java
public interface EmbeddingPort {
    EmbeddingVector embed(String tenantId, String anonymizedText, String modelVersion);
}

public interface ClusteringPort {
    List<ClusterMembership> cluster(String tenantId, String problemType,
            List<EmbeddingVector> vectors, ClusteringParameters parameters);
}
```

服务只接受已匿名文本。规则命中直接生成确定性分类；聚类端口返回成员关系，AI 命名层无权修改。

- [ ] **Step 3：验证并提交**

Commit: `feat(p9-c): classify and cluster insight topics`

### Task 8：AI 命名、高风险审核与知识缺口

**Files:**
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/topic/TopicNamingPort.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/topic/TopicPublicationService.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/topic/TopicReview.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/gap/KnowledgeGap.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/gap/KnowledgeGapService.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/topic/TopicPublicationServiceTest.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/gap/KnowledgeGapServiceTest.java`

- [ ] **Step 1：写发布边界失败测试**

验证普通主题达到样本、稳定度和置信度后自动发布；高风险主题始终等待主管；模型失败得到 `PENDING_NAMING`；重命名不改变成员；知识缺口合并多个信号但不会调用知识发布端口。

- [ ] **Step 2：实现发布决定**

```java
public record TopicPublicationDecision(
        TopicState state, String displayName, String summary,
        boolean supervisorReviewRequired, String modelVersion,
        String promptVersion) { }

public record KnowledgeGap(
        UUID gapId, String tenantId, String problemType,
        Set<GapSignal> signals, String knowledgeVersion,
        long evidenceCount, GapStatus status) { }
```

AI 输入仅含脱敏代表样本。审核允许接受、重命名或拒绝展示；成员快照只读。

- [ ] **Step 3：验证并提交**

Commit: `feat(p9-c): govern topics and knowledge gaps`

### Task 9：受控指标查询语义层

**Files:**
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/query/QueryPlan.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/query/QueryActor.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/query/QueryPlanValidator.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/query/ControlledQueryExecutor.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/query/QueryResult.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/query/QueryPlanValidatorTest.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/query/ControlledQueryExecutorTest.java`

- [ ] **Step 1：写白名单与攻击输入失败测试**

验证合法退款率计划、未知指标、禁止维度、超过时间跨度、过多分组、租户不匹配、运营人员具体订单核验、以及包含 `select/from/join/--/;`、表名、列名或函数表达式的计划全部拒绝。受控查询明确禁止 SQL、表名、列名、函数、连接条件和任意表达式。

```java
@Test
void rejectsSqlAndSensitiveDimensions() {
    assertThrows(InvalidQueryPlanException.class,
            () -> validator.validate(Fixtures.plan("refund_rate; SELECT * FROM orders"), actor));
    assertThrows(InvalidQueryPlanException.class,
            () -> validator.validate(Fixtures.planWithDimension("phone"), actor));
}
```

- [ ] **Step 2：实现封闭查询计划**

```java
public record QueryPlan(
        String metric, AllowedTimeRange timeRange,
        Set<String> dimensions, Map<String, String> filters,
        AllowedComparison comparison) {
    public QueryPlan {
        dimensions = Set.copyOf(dimensions);
        filters = Map.copyOf(filters);
    }
}

public record QueryResult(
        String metric, int definitionVersion, Instant periodStart,
        Instant periodEnd, Instant dataCutoff, int aggregateRevision,
        List<ResultRow> rows, List<String> limitations) { }
```

所有枚举为封闭类型；服务端从指标目录取得维度白名单。执行器只调用 `MetricSnapshotReader` 端口，不拼接 SQL。

- [ ] **Step 3：验证并提交**

Commit: `feat(p9-c): execute controlled metric queries`

### Task 10：高对比运营页面组件

**Files:**
- Create: `frontend/apps/aliagent-admin/src/insight/InsightConsole.vue`
- Create: `frontend/apps/aliagent-admin/src/insight/ProblemRadarPanel.vue`
- Create: `frontend/apps/aliagent-admin/src/insight/MetricOverviewPanel.vue`
- Create: `frontend/apps/aliagent-admin/src/insight/TopicGapPanel.vue`
- Create: `frontend/apps/aliagent-admin/src/insight/ControlledQueryPanel.vue`
- Create: `frontend/apps/aliagent-admin/src/insight/types.ts`
- Create: `frontend/apps/aliagent-admin/src/insight/insight.css`
- Test: `frontend/apps/aliagent-admin/src/insight/insight-state.test.ts`

- [ ] **Step 1：写页面状态测试**

验证默认页为问题雷达、加载/空/错误/权限不足状态、查询计划确认后才能执行、高风险主题显示待审核、匿名样本显示“已脱敏”。

- [ ] **Step 2：实现独立页面组件**

```ts
export type RadarStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'IGNORED'

export interface InsightConsoleState {
  activeView: 'radar' | 'metrics' | 'topics' | 'query'
  radars: ProblemRadarView[]
  metrics: MetricCardView[]
  topics: TopicView[]
  queryPlan?: QueryPlanView
  pending: boolean
  error: string
}
```

CSS 使用浅色卡片、`#172033` 深色正文、蓝色高对比标签、浅绿/浅红状态背景并保留深色边框。C 只实现 `src/insight/`，不修改 `App.vue`、API Client 出口或全局 CSS。

- [ ] **Step 3：验证 P9-C 全量并提交**

Run:

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/insight-service -Dtest='*Topic*,*Cluster*,*Gap*,*Query*' test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/insight-service test
D:\Java_Tools\nodejs\pnpm.cmd --dir frontend --filter '@aliagent/admin' build
git diff --check
```

Expected: 后端测试和前端构建 PASS；页面正文与卡片背景满足高对比设计。

Commit: `feat(p9-c): add operational insight console`

---

## 5. 集成会话：公共契约、持久化与全链路

### Task 11：冻结 P9 公共契约

**Files:**
- Create: `contracts/asyncapi/insight-events-v1.yaml`
- Create: `contracts/schemas/insight/event-envelope-v1.schema.json`
- Create: `contracts/schemas/insight/order-paid-v1.schema.json`
- Create: `contracts/schemas/insight/refund-succeeded-v1.schema.json`
- Create: `contracts/schemas/insight/logistics-exception-v1.schema.json`
- Create: `contracts/schemas/insight/conversation-signals-v1.schema.json`
- Create: `contracts/openapi/insight-service-v1.yaml`
- Modify: `contracts/contract-validator/src/test/java/com/bn/aliagent/contracts/ContractValidatorTest.java`

- [ ] **Step 1：先写契约失败测试**

测试事件必须包含公共信封、Topic 与 `eventVersion` 一致、payload 不含手机号/地址/完整订单字段；OpenAPI 的运营与主管操作具有明确安全要求。

- [ ] **Step 2：添加版本化契约**

事件版本不足时创建 v2，不给已发布 v1 追加必填字段。订单关联使用 `evidenceRef`；退款事件携带事实金额、订单归属时间和非敏感原因编码。

- [ ] **Step 3：验证契约**

Run:

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl contracts/contract-validator test
```

Expected: PASS。

Commit: `feat(p9): define insight contracts`

### Task 12：Flyway、JDBC 与应用装配

**Files:**
- Create: `services/insight-service/src/main/resources/db/migration/V2__p9_operational_insight.sql`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/persistence/JdbcInsightEventInbox.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/persistence/JdbcInsightFactRepository.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/persistence/JdbcAggregationStore.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/persistence/JdbcRadarRepository.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/persistence/JdbcTopicGapRepository.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/InsightIntegrationConfiguration.java`
- Modify: `services/insight-service/src/main/java/com/bn/aliagent/insight/EventReceiverController.java`
- Modify: `services/insight-service/src/main/resources/application-database.yml`
- Modify: `services/insight-service/pom.xml`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/persistence/P9PersistenceIntegrationTest.java`

- [ ] **Step 1：写空库和租户隔离失败测试**

创建唯一 `test_p9_...` schema，执行 Flyway，验证所有业务表有 `tenant_id`，Inbox 唯一键含 `tenant_id + producer + event_id`，不同租户相同引用不冲突，修订行不覆盖历史。

- [ ] **Step 2：实现表和适配器**

至少包含 Inbox、匿名事实、事实修订、小时/日聚合、指标定义、阈值、雷达、雷达操作、主题、主题成员、主题审核、知识缺口、重算清单和安全审计。每个跨租户查询必须显式传入租户。

- [ ] **Step 3：替换日志占位控制器**

控制器从 `X-Tenant-Id` 取得可信租户，将强类型信封交给 `InsightIntakeService`，返回 `202`；不得打印 payload。

- [ ] **Step 4：验证并清理**

Run: `mvn -pl services/insight-service test`

Expected: PASS；`test_p9_...` schema 在 `@AfterAll` 删除。

Commit: `feat(p9): persist and wire insight domains`

### Task 13：生产者、模型/向量和 `mall` 核验适配

**Files:**
- Modify: `mall/` 中订单支付、退款成功、物流异常 Outbox 生产者
- Modify: `services/conversation-service/` 中会话完成、评价、转人工和首次人工公开回复生产者
- Modify: `services/ai-orchestration-service/` 中回答质量信号与 QueryPlan 生成适配
- Modify: `services/knowledge-service/` 中知识版本信号
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/adapter/DashScopeTopicNamingAdapter.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/adapter/PgVectorClusteringAdapter.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/adapter/MallEvidenceVerificationClient.java`
- Test: 对应生产者和适配器测试

- [ ] **Step 1：生产者先写失败测试**

验证业务写入和 Outbox 同事务、重试不重复生成事实语义、事件不含敏感字段、订单金额来自 `mall`。

- [ ] **Step 2：实现适配器**

AI 只生成白名单 QueryPlan 和主题名称；向量聚类固定模型/参数版本。`mall` 核验要求服务 JWT、主管身份快照、雷达 ID、原因和 `evidenceRef`，结果不持久化到 `insight_db`。

- [ ] **Step 3：验证故障降级**

模型失败返回待命名；`mall` 失败拒绝具体核验；MQ 故障保留 Outbox。不得生成替代业务事实。

Commit: `feat(p9): connect insight fact sources`

### Task 14：Gateway、API Client 与前端入口

**Files:**
- Modify: `services/gateway-service/src/main/java/com/bn/aliagent/gateway/TrustedIdentityGatewayFilter.java`
- Modify: `services/gateway-service/src/main/resources/application.yml`
- Create: `frontend/packages/api-client/src/insight-client.ts`
- Modify: `frontend/packages/api-client/src/index.ts`
- Modify: `frontend/apps/aliagent-admin/src/App.vue`
- Modify: `frontend/apps/aliagent-admin/src/assets/main.css`
- Test: Gateway 权限测试、前端构建

- [ ] **Step 1：写 Gateway 权限失败测试**

验证消费者拒绝；运营角色可读指标/雷达/匿名样本；只有主管能修改阈值、处置雷达、审核高风险主题和核验订单；客户端伪造内部头被移除；服务 JWT audience 为 `insight-service`。

- [ ] **Step 2：接入 API 与页面**

`/api/v1/insights/**` 路由到 insight-service。`App.vue` 默认显示 `InsightConsole`，保留进入持续评测控制台的导航。API Client 只发送业务参数，不发送内部租户头。

- [ ] **Step 3：验证**

Run:

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/gateway-service test
D:\Java_Tools\nodejs\pnpm.cmd --dir frontend --filter '@aliagent/admin' build
```

Expected: PASS。

Commit: `feat(p9): expose operational insight console`

### Task 15：P9 全链路验收

**Files:**
- Create: `docs/baselines/P9-运营洞察验收报告.md`
- Create or Modify: P9 E2E 脚本，归集到集成会话独占的测试目录

- [ ] **Step 1：按顺序合并**

```powershell
git merge --no-ff codex/p9-insight-intake
git merge --no-ff codex/p9-insight-radar
git merge --no-ff codex/p9-insight-query-ui
```

每次合并后检查目录越权；公共文件冲突说明任务边界被违反，先审查再处理。

- [ ] **Step 2：运行完整验证**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd test
D:\Java_Tools\nodejs\pnpm.cmd --dir frontend --filter '@aliagent/admin' build
git diff --check
```

Expected: 根 Reactor 全模块 `SUCCESS`，前端构建成功，0 failures/errors。

- [ ] **Step 3：执行业务验收矩阵**

覆盖：退款归属周期、重复事件、7 天迟到修订、超期人工重算、转人工和首响、满意度与覆盖率、知识缺口、趋势/绝对阈值雷达、高风险主题审核、受控 QueryPlan、跨租户拒绝、运营/主管权限、模型/MQ/`mall` 故障降级。

- [ ] **Step 4：清理并形成证据**

删除所有 `test-` / `rag-test-` 数据、临时 schema、Redis 键和 MQ 测试队列；停止验收服务。报告记录提交 SHA、命令、测试数量、环境、清理结果和仍存在的风险。

- [ ] **Step 5：提交集成结果**

Commit: `feat(p9): complete operational insight integration`

只有集成分支干净且验收报告完整后，才能创建合入 `master` 的 PR。
