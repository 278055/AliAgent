# P10-B 可观测与告警 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 AliAgent 的 Gateway、消息、AI、RAG、工具和实时连接链路建立统一指标、日志、Trace、仪表盘和本地告警。

**Architecture:** Spring Boot 服务通过 Micrometer 暴露 Prometheus 指标，并通过 OpenTelemetry OTLP 输出 Trace；OTel Collector 以 DaemonSet 采集标准输出日志并过滤敏感属性。Prometheus、Loki、Tempo 作为后端，Grafana 统一查询；业务埋点使用低基数标签，敏感标识只以哈希形式进入日志/Trace，不进入指标标签。

**Tech Stack:** Micrometer、Spring Boot Actuator、OpenTelemetry Java Agent/SDK、OTLP、Prometheus、Grafana、Loki、Tempo、Helm、JUnit 5、PowerShell。

---

## 1. 所有权与前置检查

只修改总计划中 P10-B 独占文件。确认：

```powershell
git branch --show-current
git status --short
git rev-parse HEAD
```

Expected：`codex/p10-observability`，工作区为空，HEAD 为共同冻结提交。

## 2. Task 1：统一遥测依赖和配置约束

**Files:**
- Modify: `pom.xml`
- Modify: `services/gateway-service/pom.xml`
- Modify: `services/conversation-service/pom.xml`
- Modify: `services/ai-orchestration-service/pom.xml`
- Modify: `services/knowledge-service/pom.xml`
- Modify: `services/evaluation-service/pom.xml`
- Modify: `services/insight-service/pom.xml`
- Modify: `mall/pom.xml`
- Modify: `mall/mall-portal/pom.xml`
- Create: `services/gateway-service/src/main/resources/application-observability.yml`
- Create: `services/conversation-service/src/main/resources/application-observability.yml`
- Create: `services/ai-orchestration-service/src/main/resources/application-observability.yml`
- Create: `services/knowledge-service/src/main/resources/application-observability.yml`
- Create: `services/evaluation-service/src/main/resources/application-observability.yml`
- Create: `services/insight-service/src/main/resources/application-observability.yml`
- Create: `mall/mall-portal/src/main/resources/application-observability.yml`
- Create: `deploy/p10/observability/tests/TelemetryConfiguration.Tests.ps1`

- [ ] **Step 1：写失败的配置测试**

测试要求所有服务暴露 `health,info,prometheus`，启用 histogram，并从环境读取 OTLP Endpoint：

```powershell
$files = @(
  'services/gateway-service/src/main/resources/application-observability.yml',
  'services/conversation-service/src/main/resources/application-observability.yml',
  'services/ai-orchestration-service/src/main/resources/application-observability.yml',
  'services/knowledge-service/src/main/resources/application-observability.yml',
  'services/evaluation-service/src/main/resources/application-observability.yml',
  'services/insight-service/src/main/resources/application-observability.yml',
  'mall/mall-portal/src/main/resources/application-observability.yml'
)
foreach ($file in $files) {
  $text = Get-Content $file -Raw
  if ($text -notmatch 'prometheus') { throw "$file 未暴露 Prometheus" }
  if ($text -notmatch 'OTEL_EXPORTER_OTLP_ENDPOINT') { throw "$file 未使用 OTLP 环境变量" }
}
```

- [ ] **Step 2：运行并确认失败**

Run：`pwsh -NoProfile -File deploy/p10/observability/tests/TelemetryConfiguration.Tests.ps1`

Expected：FAIL，配置文件尚不存在。

- [ ] **Step 3：添加依赖和 Profile**

Spring Boot 3 服务添加：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

`mall-portal` 使用 Spring Boot 2.7 兼容的 Micrometer/OTel 版本，由 `mall/pom.xml` dependencyManagement 固定；不得把 Boot 3 BOM 注入 mall。

公共 Profile 核心内容：

```yaml
management:
  endpoints.web.exposure.include: health,info,prometheus
  tracing.sampling.probability: ${OTEL_TRACES_SAMPLER_ARG:1.0}
  metrics.distribution.percentiles-histogram.http.server.requests: true
  otlp.tracing.endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://otel-collector.aliagent-observability.svc.cluster.local:4318/v1/traces}
```

正式服务通过资源属性注入 `service.name`、`service.version`、`deployment.environment`、`deployment.track`。

- [ ] **Step 4：验证**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -f mall/pom.xml -pl mall-portal -am test -DskipTests=false -Ddocker.skip=true
pwsh -NoProfile -File deploy/p10/observability/tests/TelemetryConfiguration.Tests.ps1
```

Expected：构建通过；现有健康测试不回归。

- [ ] **Step 5：提交**

```powershell
git add pom.xml services/gateway-service/pom.xml services/conversation-service/pom.xml services/ai-orchestration-service/pom.xml services/knowledge-service/pom.xml services/evaluation-service/pom.xml services/insight-service/pom.xml services/gateway-service/src/main/resources/application-observability.yml services/conversation-service/src/main/resources/application-observability.yml services/ai-orchestration-service/src/main/resources/application-observability.yml services/knowledge-service/src/main/resources/application-observability.yml services/evaluation-service/src/main/resources/application-observability.yml services/insight-service/src/main/resources/application-observability.yml mall/pom.xml mall/mall-portal/pom.xml mall/mall-portal/src/main/resources/application-observability.yml deploy/p10/observability
git commit -m "feat(p10-b): add unified telemetry dependencies"
```

## 3. Task 2：敏感字段与标签治理

**Files:**
- Create: `services/gateway-service/src/main/java/com/bn/aliagent/gateway/telemetry/TelemetrySanitizer.java`
- Create: `services/gateway-service/src/test/java/com/bn/aliagent/gateway/telemetry/TelemetrySanitizerTest.java`
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/telemetry/TelemetrySanitizer.java`
- Create: `services/ai-orchestration-service/src/test/java/com/bn/aliagent/orchestration/telemetry/TelemetrySanitizerTest.java`
- Create: `deploy/p10/observability/tests/MetricCardinality.Tests.ps1`

- [ ] **Step 1：写失败测试**

```java
@Test
void hashesTenantAndNeverReturnsSensitiveText() {
    var sanitizer = new TelemetrySanitizer("test-observability-salt-at-least-32-bytes");
    assertEquals(16, sanitizer.reference("tenant-a").length());
    assertFalse(sanitizer.safeError("Bearer secret phone 13800138000").contains("13800138000"));
    assertFalse(sanitizer.safeError("Bearer secret").contains("secret"));
}
```

PowerShell 静态测试扫描 Prometheus 标签创建点，拒绝 `tenantId`、`orderId`、`conversationId`、`userId` 和 `requestId` 作为 tag key。

- [ ] **Step 2：运行并确认失败**

Expected：FAIL，Sanitizer 尚不存在。

- [ ] **Step 3：实现最小治理组件**

- 使用 HMAC-SHA256 和独立 `TELEMETRY_HASH_KEY` 生成 16 字符引用。
- `safeError()` 只返回异常类别和允许的错误码，不返回原始消息。
- 指标标签白名单固定为 `service`、`operation`、`outcome`、`event.type`、`deployment.track`、`dependency`。
- 日志/Trace 可以使用 `tenant.ref`、`conversation.ref`、`generation.ref`，但不得出现原始值。

- [ ] **Step 4：验证并提交**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/gateway-service,services/ai-orchestration-service -am test
pwsh -NoProfile -File deploy/p10/observability/tests/MetricCardinality.Tests.ps1
git add services/gateway-service services/ai-orchestration-service deploy/p10/observability
git commit -m "feat(p10-b): enforce telemetry privacy and cardinality"
```

## 4. Task 3：关键业务指标和 Span

**Files:**
- Create: `services/gateway-service/src/main/java/com/bn/aliagent/gateway/telemetry/GatewayTelemetry.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/telemetry/ConversationTelemetry.java`
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/telemetry/AiTelemetry.java`
- Create: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/telemetry/KnowledgeTelemetry.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/telemetry/EvaluationTelemetry.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/telemetry/InsightTelemetry.java`
- Create: `mall/mall-portal/src/main/java/com/macro/mall/portal/telemetry/MallTelemetry.java`
- Test: `services/gateway-service/src/test/java/com/bn/aliagent/gateway/telemetry/GatewayTelemetryTest.java`
- Test: `services/conversation-service/src/test/java/com/bn/aliagent/conversation/telemetry/ConversationTelemetryTest.java`
- Test: `services/ai-orchestration-service/src/test/java/com/bn/aliagent/orchestration/telemetry/AiTelemetryTest.java`
- Test: `services/knowledge-service/src/test/java/com/bn/aliagent/knowledge/telemetry/KnowledgeTelemetryTest.java`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/telemetry/EvaluationTelemetryTest.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/telemetry/InsightTelemetryTest.java`
- Test: `mall/mall-portal/src/test/java/com/macro/mall/portal/telemetry/MallTelemetryTest.java`
- Modify: `services/gateway-service/src/main/java/com/bn/aliagent/gateway/TrustedIdentityGatewayFilter.java`
- Modify: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/core/ConversationOutboxDispatcher.java`
- Modify: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/SseStreamController.java`
- Modify: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/api/RealtimeWebSocketController.java`
- Modify: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/messaging/AiReplyRequestedV2Consumer.java`
- Modify: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/runtime/ReadOnlyWorkflowRunner.java`
- Modify: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/retrieval/RetrievalService.java`
- Modify: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/ingestion/IngestionOutboxDispatcher.java`
- Modify: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/runner/EvaluationRunService.java`
- Modify: `services/insight-service/src/main/java/com/bn/aliagent/insight/adapter/InsightRabbitEventConsumer.java`
- Modify: `mall/mall-portal/src/main/java/com/macro/mall/portal/internal/read/InternalReadService.java`
- Modify: `mall/mall-portal/src/main/java/com/macro/mall/portal/aftersale/messaging/MallInsightOutboxDispatcher.java`
- Modify only within existing classes when necessary to call the telemetry facade; do not change business outcomes.

- [ ] **Step 1：写失败测试**

使用 `SimpleMeterRegistry` 验证低基数指标：

```java
@Test
void recordsAiFirstTokenWithoutTenantTag() {
    var registry = new SimpleMeterRegistry();
    new AiTelemetry(registry).firstToken(Duration.ofMillis(850), "success", "stable");
    var timer = registry.get("aliagent.ai.first.token").timer();
    assertEquals(1, timer.count());
    assertNull(timer.getId().getTag("tenantId"));
}
```

至少覆盖：Gateway 路由、RabbitMQ 发布/消费、AI 排队/首 Token/总耗时/Token/成本、RAG 向量/关键词/融合/重排、工具调用、Outbox、SSE/WebSocket 在线/断线/恢复。

- [ ] **Step 2：运行并确认失败**

Run：

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/gateway-service,services/conversation-service,services/ai-orchestration-service,services/knowledge-service,services/evaluation-service,services/insight-service -Dtest=*TelemetryTest test
```

Expected：FAIL，Telemetry facade 尚不存在。

- [ ] **Step 3：实现 facade 和 Observation**

每个 facade 只暴露明确业务方法，不允许调用方自由创建 tag：

```java
public final class AiTelemetry {
    private final MeterRegistry registry;
    public void firstToken(Duration duration, String outcome, String track) {
        Timer.builder("aliagent.ai.first.token")
                .tag("outcome", outcome).tag("deployment.track", track)
                .publishPercentileHistogram().register(registry).record(duration);
    }
}
```

Trace Span 使用 `ObservationRegistry` 包裹操作，异常只记录异常类型和受控错误码。对尚未提供真实 Token/成本值的适配器记录 `unknown`/0 并单独计数 `aliagent.ai.usage.unavailable`，禁止估造事实。

- [ ] **Step 4：验证业务回归**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/gateway-service,services/conversation-service,services/ai-orchestration-service,services/knowledge-service,services/evaluation-service,services/insight-service -am test
git diff --check
```

Expected：原有测试和新遥测测试全部通过，业务返回值未改变。

- [ ] **Step 5：提交**

```powershell
git add services
git commit -m "feat(p10-b): instrument critical platform paths"
```

## 5. Task 4：OTel Collector 与完整轻量栈

**Files:**
- Create: `deploy/p10/observability/helm/aliagent-observability/Chart.yaml`
- Create: `deploy/p10/observability/helm/aliagent-observability/values.yaml`
- Create: `deploy/p10/observability/helm/aliagent-observability/values-local.yaml`
- Create: `deploy/p10/observability/helm/aliagent-observability/templates/otel-collector-daemonset.yaml`
- Create: `deploy/p10/observability/helm/aliagent-observability/templates/otel-collector-service.yaml`
- Create: `deploy/p10/observability/helm/aliagent-observability/templates/rbac.yaml`
- Create: `deploy/p10/observability/helm/aliagent-observability/templates/prometheus.yaml`
- Create: `deploy/p10/observability/helm/aliagent-observability/templates/loki.yaml`
- Create: `deploy/p10/observability/helm/aliagent-observability/templates/tempo.yaml`
- Create: `deploy/p10/observability/helm/aliagent-observability/templates/grafana.yaml`
- Create: `deploy/p10/observability/helm/aliagent-observability/templates/webhook-receiver.yaml`
- Create: `deploy/p10/observability/Install-Observability.ps1`
- Test: `deploy/p10/observability/tests/ObservabilityHelm.Tests.ps1`

- [ ] **Step 1：写 Helm 失败测试**

断言渲染结果包含：OTLP 4317/4318、每节点 filelog 采集、Prometheus 7 天、Loki 3 天、Tempo 3 天、`/var/lib/aliagent-observability/<component>` hostPath 持久化、资源 request/limit 和敏感属性删除 processor。该宿主路径由 P10-A 创建 K3d 时映射自 `AliAgentData/observability`。

- [ ] **Step 2：运行并确认失败**

Run：`pwsh -NoProfile -File deploy/p10/observability/tests/ObservabilityHelm.Tests.ps1`

Expected：FAIL，Chart 尚不存在。

- [ ] **Step 3：实现最小 Chart**

Collector pipeline：

```yaml
receivers:
  otlp:
    protocols: { grpc: {}, http: {} }
  filelog:
    include: [/var/log/pods/*/*/*.log]
    start_at: end
processors:
  memory_limiter: {}
  attributes/privacy:
    actions:
      - { key: http.request.header.authorization, action: delete }
      - { key: enduser.id, action: delete }
      - { key: tenantId, action: delete }
  batch: {}
exporters:
  otlp/tempo: { endpoint: tempo:4317, tls: { insecure: true } }
  loki: { endpoint: http://loki:3100/loki/api/v1/push }
```

Collector 使用 DaemonSet 挂载只读 `/var/log/pods` 采集容器标准输出，并通过 Service 接收应用 OTLP Trace；Prometheus 直接抓取应用 `/actuator/prometheus` 和 Collector 自身指标。Prometheus、Grafana、Loki、Tempo 分别使用 `/var/lib/aliagent-observability/prometheus|grafana|loki|tempo` 的 `hostPath`，不得使用会随 K3d 销毁的匿名卷。Loki 不解析 JWT/Prompt/订单正文为标签。Grafana 预置 Prometheus、Loki、Tempo 数据源。

- [ ] **Step 4：安装验证**

```powershell
helm lint deploy/p10/observability/helm/aliagent-observability -f deploy/p10/observability/helm/aliagent-observability/values-local.yaml
pwsh -NoProfile -File deploy/p10/observability/Install-Observability.ps1
kubectl -n aliagent-observability get pod,svc
```

Expected：全部 Pod Ready；Grafana 可查询三类数据源。

- [ ] **Step 5：提交**

```powershell
git add deploy/p10/observability
git commit -m "feat(p10-b): deploy complete observability stack"
```

## 6. Task 5：仪表盘和告警

**Files:**
- Create: `deploy/p10/observability/grafana/dashboards/service-overview.json`
- Create: `deploy/p10/observability/grafana/dashboards/ai-rag.json`
- Create: `deploy/p10/observability/grafana/dashboards/messaging-realtime.json`
- Create: `deploy/p10/observability/grafana/dashboards/business.json`
- Create: `deploy/p10/observability/grafana/dashboards/canary.json`
- Create: `deploy/p10/observability/prometheus/rules/aliagent-alerts.yml`
- Create: `deploy/p10/observability/Test-Alerts.ps1`
- Test: `deploy/p10/observability/tests/DashboardsAndAlerts.Tests.ps1`

- [ ] **Step 1：写失败测试**

测试解析所有 JSON 和 YAML，断言仪表盘不使用高基数标签，告警覆盖：普通 P95、首 Token、错误率、RabbitMQ 积压、Outbox、下游连续失败、连接池、磁盘和 Canary 退化。

- [ ] **Step 2：运行并确认失败**

Expected：FAIL，文件尚不存在。

- [ ] **Step 3：实现规则**

示例规则：

```yaml
- alert: AliAgentHttpP95TooHigh
  expr: histogram_quantile(0.95, sum by (le, service_name) (rate(http_server_requests_seconds_bucket[5m]))) > 0.5
  for: 5m
  labels: { severity: warning }
```

Canary 规则必须有 `sum(increase(http_server_requests_seconds_count{deployment_track="canary"}[5m])) >= 100` 的最小样本门槛，同时比较错误率超过 Stable 2 个百分点或 2 倍，或者 P95 高出 50%。

- [ ] **Step 4：验证**

```powershell
pwsh -NoProfile -File deploy/p10/observability/tests/DashboardsAndAlerts.Tests.ps1
pwsh -NoProfile -File deploy/p10/observability/Test-Alerts.ps1
```

Expected：测试触发 HTTP 延迟告警并由本地 Webhook 收到，恢复后告警回到正常。

- [ ] **Step 5：提交**

```powershell
git add deploy/p10/observability
git commit -m "feat(p10-b): add dashboards and actionable alerts"
```

## 7. Task 6：端到端 Trace 与文档

**Files:**
- Create: `deploy/p10/observability/Verify-TraceCorrelation.ps1`
- Create: `docs/runbooks/p10/observability-local-stack.md`
- Create: `docs/runbooks/p10/observability-alert-response.md`

- [ ] **Step 1：验证关联链路**

脚本提交一个 `test-p10-observability` 会话请求，捕获响应 `X-Trace-Id`，再查询 Tempo 和 Loki，断言同一 Trace 至少包含 Gateway、Conversation、RabbitMQ、AI 和 RAG/工具路径中的实际参与组件。组件未参与时不得制造 Span；报告需说明实际链路分支。

- [ ] **Step 2：运行完整验证**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -f mall/pom.xml -pl mall-portal -am test -DskipTests=false -Ddocker.skip=true
helm lint deploy/p10/observability/helm/aliagent-observability -f deploy/p10/observability/helm/aliagent-observability/values-local.yaml
pwsh -NoProfile -File deploy/p10/observability/Verify-TraceCorrelation.ps1
git diff --check
```

- [ ] **Step 3：提交并报告**

```powershell
git add deploy/p10/observability docs/runbooks/p10 services mall
git commit -m "docs(p10-b): document telemetry and alert operations"
git status --short
```

Expected：工作区为空。报告最终 SHA、Trace ID 示例、告警验证和 A 所需的 Helm 接口缺口。
