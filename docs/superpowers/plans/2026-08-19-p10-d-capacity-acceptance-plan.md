# P10-D 容量与生产验收 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用可重复的合成数据和 k6 场景验证 AliAgent 的 HTTP、AI、SSE、WebSocket、混合检索、订单查询、消息可靠性、灰度和故障恢复能力，并形成 P10 生产验收报告。

**Architecture:** P10-D 从 A/B/C 已集成提交派生，只消费冻结的部署、遥测、安全和恢复接口。测试分为 smoke、baseline、stress、soak、failure 五级；阈值通过 k6 options 和外部证据检查双重门禁，测试资源统一自动清理。

**Tech Stack:** k6、Java 17 辅助工具、PowerShell、PostgreSQL、MySQL、RabbitMQ、Grafana/Prometheus/Tempo/Loki、Kubernetes。

---

## 1. 前置门禁

本任务只有在 A/B/C 已合入 `codex/integration-p10` 且集成冒烟通过后才能开始。

```powershell
git branch --show-current
git status --short
git log -1 --oneline
kubectl get nodes
kubectl -n aliagent-system get pods
kubectl -n aliagent-observability get pods
```

Expected：分支为 `codex/p10-capacity-acceptance`，工作区为空；三个节点 Ready；Stable 与可观测 Pod Ready。

## 2. Task 1：测试配置、身份和清理框架

**Files:**
- Create: `tests/p10/config/local.json`
- Create: `tests/p10/lib/config.js`
- Create: `tests/p10/lib/auth.js`
- Create: `tests/p10/lib/http.js`
- Create: `tests/p10/lib/metrics.js`
- Create: `tests/p10/lib/resources.js`
- Create: `tools/p10-acceptance/Cleanup-P10TestData.ps1`
- Create: `tools/p10-acceptance/Verify-P10Environment.ps1`
- Test: `tests/p10/unit/config.test.js`
- Test: `tools/p10-acceptance/tests/AcceptanceScripts.Tests.ps1`

- [ ] **Step 1：写失败测试**

`config.test.js` 使用 k6 checks 验证：

```javascript
export default function () {
  const config = loadConfig('tests/p10/config/local.json');
  if (!config.baseUrl.startsWith('http://localhost:')) throw new Error('local baseUrl invalid');
  if (!config.resourcePrefix.startsWith('test-')) throw new Error('resource prefix invalid');
}
```

PowerShell 测试要求清理脚本只允许 `test-`、`rag-test-`、`test-restore-` 前缀，遇到其他前缀退出非 0。

- [ ] **Step 2：运行并确认失败**

```powershell
k6 run tests/p10/unit/config.test.js
pwsh -NoProfile -File tools/p10-acceptance/tests/AcceptanceScripts.Tests.ps1
```

Expected：FAIL，库和脚本尚不存在。

- [ ] **Step 3：实现框架**

`local.json` 只保存非敏感地址和负载参数；JWT Secret、数据库密码从环境读取。`resources.js` 为每次运行生成：

```javascript
const runId = `test-p10-${__ENV.P10_RUN_ID}`;
```

清理脚本先列出待删数量，校验前缀，再按外键顺序清理会话/消息、文档/chunk、测试用户、Redis key 和 MinIO 对象。默认 DryRun，执行删除必须 `-Execute -ConfirmRunId <id>`。

- [ ] **Step 4：验证并提交**

```powershell
k6 run tests/p10/unit/config.test.js
pwsh -NoProfile -File tools/p10-acceptance/tests/AcceptanceScripts.Tests.ps1
git add tests/p10 tools/p10-acceptance
git commit -m "test(p10-d): add isolated load test framework"
```

## 3. Task 2：可重复容量数据集

**Files:**
- Create: `tools/p10-data/P10DatasetGenerator.java`
- Create: `tools/p10-data/P10DatasetManifest.java`
- Create: `tools/p10-data/Generate-P10Dataset.ps1`
- Create: `tools/p10-data/Verify-P10Dataset.ps1`
- Create: `tools/p10-data/Cleanup-P10Dataset.ps1`
- Test: `tools/p10-data/P10DatasetGeneratorTest.java`

- [ ] **Step 1：写失败测试**

```java
@Test
void sameSeedProducesSameManifestDigest() {
    var first = generator.generate(new Request("test-p10-data", 42L, 100, 1000));
    var second = generator.generate(new Request("test-p10-data", 42L, 100, 1000));
    assertEquals(first.digest(), second.digest());
}

@Test
void refusesNonTestTenantPrefix() {
    assertThrows(IllegalArgumentException.class,
            () -> generator.generate(new Request("tenant-prod", 42L, 1, 1)));
}
```

- [ ] **Step 2：运行并确认失败**

编译测试辅助类或使用 JUnit Console；Expected：FAIL，Generator 尚不存在。

- [ ] **Step 3：实现生成器**

生成器参数必须包含租户、随机种子、商品数、订单数、会话数。Baseline 默认生成 10 万商品和 100 万订单；为避免一次事务和超大内存，按 1000 行批量提交并输出进度。订单归属、状态、物流、售后和类目分布由固定种子决定。

知识商品文本进入 `knowledge_db` 的测试文档/Chunk 和向量；若本地真实嵌入成本不可接受，使用固定 1024 维可重复测试向量，但报告必须标记为合成向量，不用于质量结论。

- [ ] **Step 4：验证数据**

```powershell
pwsh -NoProfile -File tools/p10-data/Generate-P10Dataset.ps1 -TenantId test-p10-capacity -Seed 20260819 -Products 100000 -Orders 1000000
pwsh -NoProfile -File tools/p10-data/Verify-P10Dataset.ps1 -TenantId test-p10-capacity -Products 100000 -Orders 1000000
```

Expected：数量、租户隔离、索引和 Manifest SHA-256 均正确；重复 Seed 得到相同摘要。

- [ ] **Step 5：提交**

```powershell
git add tools/p10-data
git commit -m "test(p10-d): add reproducible capacity dataset"
```

## 4. Task 3：HTTP、SSE 和 AI 场景

**Files:**
- Create: `tests/p10/scenarios/http-smoke.js`
- Create: `tests/p10/scenarios/consumer-baseline.js`
- Create: `tests/p10/scenarios/ai-streaming.js`
- Create: `tests/p10/scenarios/rag-order.js`
- Create: `tests/p10/scenarios/sse-recovery.js`
- Create: `tests/p10/thresholds.js`
- Test: `tests/p10/unit/thresholds.test.js`

- [ ] **Step 1：写阈值失败测试**

`thresholds.js` 固定：

```javascript
export const thresholds = {
  http_req_failed: ['rate<0.01'],
  'http_req_duration{kind:ordinary}': ['p(95)<500'],
  ai_first_token_ms: ['p(95)<3000'],
  ai_task_success: ['rate>=0.99'],
  stream_recovery_success: ['rate>=0.999'],
};
```

单元测试断言任何脚本不得覆盖为更宽松门限。

- [ ] **Step 2：运行并确认失败**

Run：`k6 run tests/p10/unit/thresholds.test.js`

Expected：FAIL，阈值模块尚不存在。

- [ ] **Step 3：实现场景**

- `http-smoke`：健康、鉴权、创建会话、普通查询、未授权拒绝。
- `consumer-baseline`：100→300 VU，登录/会话/普通查询混合。
- `ai-streaming`：50 个并发 AI 任务，解析 SSE 第一个业务 Token 到达时间；HTTP Header 时间不视为首 Token。
- `rag-order`：RAG、授权订单查询、非归属订单拒绝，分别打 `kind` tag。
- `sse-recovery`：断开后携带 Last-Event-ID/平台恢复参数重连，验证消息不丢失且不重复。

所有业务请求使用独立 `requestId` 和 Idempotency-Key；失败响应采样时先脱敏。

- [ ] **Step 4：运行 smoke 和 baseline**

```powershell
k6 run -e P10_RUN_ID=smoke-001 tests/p10/scenarios/http-smoke.js
k6 run -e P10_RUN_ID=baseline-001 tests/p10/scenarios/consumer-baseline.js
k6 run -e P10_RUN_ID=ai-001 tests/p10/scenarios/ai-streaming.js
k6 run -e P10_RUN_ID=rag-order-001 tests/p10/scenarios/rag-order.js
k6 run -e P10_RUN_ID=sse-001 tests/p10/scenarios/sse-recovery.js
```

Expected：普通查询 P95 小于 500 ms、AI 首 Token P95 小于 3 秒且其他门限通过；外部模型抖动时报告端到端、平台排队和模型调用三段耗时，不删除慢样本。

- [ ] **Step 5：提交**

```powershell
git add tests/p10
git commit -m "test(p10-d): cover consumer ai rag and sse capacity"
```

## 5. Task 4：客服 WebSocket 与消息可靠性

**Files:**
- Create: `tests/p10/scenarios/agent-websocket.js`
- Create: `tests/p10/scenarios/message-reliability.js`
- Create: `tools/p10-acceptance/Inject-RabbitFailure.ps1`
- Create: `tools/p10-acceptance/Restart-Consumer.ps1`
- Test: `tools/p10-acceptance/tests/FailureInjection.Tests.ps1`

- [ ] **Step 1：写失败测试**

要求故障注入脚本只允许 `aliagent-local`、`aliagent-system` 和明确 Deployment 白名单，且每个动作都有恢复命令和 finally 清理。

- [ ] **Step 2：运行并确认失败**

Expected：FAIL，脚本尚不存在。

- [ ] **Step 3：实现场景**

- `agent-websocket`：20→50 客服连接，心跳、接管、转派、公开回复、断线重连。
- `message-reliability`：重复发布同一事件、RabbitMQ 短暂停止、消费者滚动重启、Outbox 重投。
- 验证最终业务结果唯一、消息可恢复、人工接管后 AI 不公开发言。

WebSocket 每个 VU 必须正常关闭；脚本结束后在线连接指标回到基线。

- [ ] **Step 4：运行验证**

```powershell
k6 run -e P10_RUN_ID=ws-001 tests/p10/scenarios/agent-websocket.js
k6 run -e P10_RUN_ID=mq-001 tests/p10/scenarios/message-reliability.js
```

Expected：恢复成功率 ≥99.9%，重复消息不产生重复业务结果，消费者重启无消息丢失。

- [ ] **Step 5：提交**

```powershell
git add tests/p10 tools/p10-acceptance
git commit -m "test(p10-d): verify websocket and message reliability"
```

## 6. Task 5：Canary、滚动发布和故障场景

**Files:**
- Create: `tests/p10/scenarios/canary.js`
- Create: `tests/p10/scenarios/failure.js`
- Create: `tools/p10-acceptance/Inject-DependencyFailure.ps1`
- Create: `tools/p10-acceptance/Verify-RollingAvailability.ps1`
- Create: `tools/p10-acceptance/Verify-CanaryRollback.ps1`

- [ ] **Step 1：写失败的范围保护测试**

故障脚本必须限定可注入依赖：`model`、`redis`、`rabbitmq`、`mall-portal`、单个应用 Pod。数据库故障只允许隔离测试实例，不允许停止用户现有数据库容器。

- [ ] **Step 2：确认失败**

Expected：FAIL，脚本尚不存在。

- [ ] **Step 3：实现故障与灰度验证**

- Canary 测试租户 100% 命中 Canary，Stable 租户 0% 命中。
- 伪造 `X-AliAgent-Track` 无效。
- 注入 Canary 5xx 或延迟，验证告警和清空白名单后流量回到 Stable。
- 滚动升级 Gateway/Conversation/AI 时持续发送 smoke，核心请求不中断。
- 模型故障返回受控转人工提示；mall 故障拒绝生成订单/物流事实。
- Redis、RabbitMQ 和单 Pod 故障恢复后状态一致。

- [ ] **Step 4：运行验证**

```powershell
k6 run -e P10_RUN_ID=canary-001 tests/p10/scenarios/canary.js
k6 run -e P10_RUN_ID=failure-001 tests/p10/scenarios/failure.js
pwsh -NoProfile -File tools/p10-acceptance/Verify-RollingAvailability.ps1
pwsh -NoProfile -File tools/p10-acceptance/Verify-CanaryRollback.ps1
```

Expected：Canary 退化不扩大；回退后测试租户恢复 Stable；故障时不虚构业务事实。

- [ ] **Step 5：提交**

```powershell
git add tests/p10 tools/p10-acceptance
git commit -m "test(p10-d): verify canary rollback and failure safety"
```

## 7. Task 6：Stress、Soak 与证据采集

**Files:**
- Create: `tests/p10/scenarios/stress.js`
- Create: `tests/p10/scenarios/soak.js`
- Create: `tools/p10-acceptance/Collect-P10Evidence.ps1`
- Create: `tools/p10-acceptance/Compare-P10Thresholds.ps1`
- Create: `docs/reports/p10/P10-容量基线.md`

- [ ] **Step 1：写证据完整性失败测试**

`Compare-P10Thresholds.ps1` 必须拒绝缺少下列字段的 k6 JSON：P95、错误率、首 Token、成功率、VU、持续时间、Git SHA、镜像 SHA、节点资源峰值。

- [ ] **Step 2：确认失败**

Expected：FAIL，证据工具尚不存在。

- [ ] **Step 3：实现 Stress/Soak**

- Stress 分级提升在线用户和 AI 并发，记录首次违反保护门限的容量点。
- Soak 持续 2 小时为本地默认，支持参数提升到 4 小时；每 5 分钟采集 JVM、Pod、节点、连接池、RabbitMQ、磁盘和在线连接。
- 不能以 OOM、无限积压或主机失去响应作为容量保护；系统应先排队或拒绝。

- [ ] **Step 4：执行**

```powershell
k6 run --out json=docs/reports/p10/stress.json -e P10_RUN_ID=stress-001 tests/p10/scenarios/stress.js
k6 run --out json=docs/reports/p10/soak.json -e P10_RUN_ID=soak-001 -e SOAK_DURATION=2h tests/p10/scenarios/soak.js
pwsh -NoProfile -File tools/p10-acceptance/Collect-P10Evidence.ps1 -Output docs/reports/p10/evidence
pwsh -NoProfile -File tools/p10-acceptance/Compare-P10Thresholds.ps1 -Report docs/reports/p10/stress.json
```

Expected：报告写明达标容量、容量拐点、资源峰值和限制条件。

- [ ] **Step 5：提交**

大体积原始遥测文件不得提交；只提交脱敏摘要和小型聚合 JSON。

```powershell
git add tests/p10 tools/p10-acceptance docs/reports/p10/P10-容量基线.md
git commit -m "test(p10-d): establish capacity and soak baseline"
```

## 8. Task 7：生产验收报告和清理

**Files:**
- Create: `tools/p10-acceptance/Invoke-P10Acceptance.ps1`
- Create: `docs/reports/p10/P10-生产验收报告.md`
- Create: `docs/runbooks/p10/capacity-test.md`

- [ ] **Step 1：编排最终验收**

`Invoke-P10Acceptance.ps1` 按总计划第 9 节顺序调用环境检查、smoke、baseline、Canary、跨租户、安全、恢复、stress/soak 和清理；每一步写 UTC 开始/结束和退出码。失败时执行安全清理但保留脱敏证据。

- [ ] **Step 2：生成报告**

报告必须包含：

- Git/镜像/Helm 版本。
- 三节点、Pod 分布、健康和滚动发布。
- Trace ID、仪表盘、告警证据。
- Stable/Canary 路由和回退。
- 跨租户、文件扫描、密钥轮换和高风险操作。
- k6 参数、门限、容量拐点和资源峰值。
- 实测 RPO/RTO。
- 本地与正式生产差异及剩余风险。

- [ ] **Step 3：清理并核验**

```powershell
pwsh -NoProfile -File tools/p10-acceptance/Cleanup-P10TestData.ps1 -RunId <run-id> -Execute -ConfirmRunId <run-id>
pwsh -NoProfile -File tools/p10-data/Cleanup-P10Dataset.ps1 -TenantId test-p10-capacity -Execute -ConfirmTenantId test-p10-capacity
```

查询数据库、MinIO、Redis 和 RabbitMQ，确认无本次前缀资源。停止故障注入辅助容器和测试进程，不删除用户持久化基础设施。

- [ ] **Step 4：最终验证并提交**

```powershell
k6 run tests/p10/scenarios/http-smoke.js
pwsh -NoProfile -File tools/p10-acceptance/Invoke-P10Acceptance.ps1 -Mode verify-report
git diff --check
git add tests/p10 tools/p10-acceptance tools/p10-data docs/reports/p10 docs/runbooks/p10
git commit -m "test(p10-d): complete production acceptance evidence"
git status --short
```

Expected：工作区为空；报告中所有强制门禁均为 PASS，或明确列出未通过项且不声称 P10 完成。向集成会话报告最终 SHA 和证据目录。
