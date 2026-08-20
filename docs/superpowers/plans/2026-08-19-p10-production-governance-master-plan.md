# P10 生产化治理总实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 P0～P9 的 AliAgent 平台部署到可重复创建的三节点 K3d 环境，并完成可观测、租户灰度、安全、备份恢复和容量验收。

**Architecture:** P10 采用“协调冻结 → A/B/C 三会话并行 → 阶段集成 → D 容量验收 → 最终集成”的两波实施方式。有状态基础设施运行在独立 Docker Compose，应用与可观测组件运行在 K3d；四个任务包通过严格目录所有权避免修改同一文件。

**Tech Stack:** Docker Desktop、K3d/K3s、Helm、Docker Registry、Java 17、Spring Boot 3.4.5、Spring Boot 2.7.5（mall）、OpenTelemetry、Micrometer、Prometheus、Grafana、Loki、Tempo、PowerShell、k6。

---

## 1. 必读上下文

- 设计规范：`docs/superpowers/specs/2026-08-19-p10-production-governance-design.md`
- 总体架构：`docs/电商AI客服平台-总体架构设计.md`
- 阶段计划：`docs/电商AI客服平台-分阶段实施计划.md`
- Git 协作：`docs/多会话Git工作树协作指南.md`
- 开发命令：`docs/开发命令速查.md`
- 数据库指南：`docs/MCP配置与使用指南.md`

任何任务开始前必须确认分支、Worktree 和 HEAD 与冻结提交一致，禁止直接在主工作区开发。

## 2. 会话与 Worktree 拓扑

协调会话在 `master` 最新提交上创建集成分支：

```powershell
$repo = 'D:\Java\code\AliAgent'
$worktrees = 'D:\Java\code\AliAgent-worktrees'

git -C $repo fetch origin
git -C $repo switch master
git -C $repo pull --ff-only origin master
git -C $repo branch codex/integration-p10
git -C $repo worktree add "$worktrees\p10-integration" codex/integration-p10
```

在 P10 冻结提交产生后创建第一波三个 Worktree：

```powershell
$base = git -C "$worktrees\p10-integration" rev-parse HEAD
git -C $repo worktree add -b codex/p10-platform-deployment "$worktrees\p10-platform-deployment" $base
git -C $repo worktree add -b codex/p10-observability "$worktrees\p10-observability" $base
git -C $repo worktree add -b codex/p10-security-recovery "$worktrees\p10-security-recovery" $base
```

第一波集成通过后，再从集成分支创建 D：

```powershell
$base = git -C "$worktrees\p10-integration" rev-parse HEAD
git -C $repo worktree add -b codex/p10-capacity-acceptance "$worktrees\p10-capacity-acceptance" $base
```

## 3. 公共接口冻结

协调会话在创建 A/B/C 前冻结以下接口，不实现业务功能：

| 接口 | 冻结值 |
|---|---|
| K3d 集群名 | `aliagent-local` |
| Docker 网络 | `aliagent-platform` |
| Registry 外部地址 | `localhost:5000` |
| Registry 集群内地址 | `aliagent-registry:5000` |
| Stable 命名空间 | `aliagent-system` |
| Canary 命名空间 | `aliagent-canary` |
| 可观测命名空间 | `aliagent-observability` |
| 数据根目录 | `D:\Java\code\AliAgent\AliAgentData` |
| 集群访问基础设施 | `host.k3d.internal` + 现有固定宿主端口 |
| Canary 租户文件 | `/etc/aliagent/canary/tenants.txt` |
| 遥测协议 | OTLP gRPC `4317`、OTLP HTTP `4318` |
| Prometheus 抓取端点 | `/actuator/prometheus` |
| 部署轨道属性 | `deployment.track=stable|canary` |
| 镜像版本 | Git SHA，禁止 `latest` |
| 测试资源前缀 | `test-`、`rag-test-`、`test-restore-` |
| 租户删除端口 | `POST /internal/api/v1/retention/tenants/{tenantId}:delete` |
| 删除幂等与恢复隔离 | `X-Deletion-Manifest-Id`，恢复后开放流量前重放删除清单 |

协调会话创建 `docs/tasks/p10/P10-阶段任务边界.md`，写入本计划第 4 节的目录所有权，并提交为 A/B/C 的共同基线。

## 4. 目录所有权

### P10-A 独占

- `deploy/p10/platform/`
- `deploy/p10/helm/aliagent/`
- `deploy/p10/platform/images/gateway-service.Dockerfile`
- `deploy/p10/platform/images/conversation-service.Dockerfile`
- `deploy/p10/platform/images/ai-orchestration-service.Dockerfile`
- `deploy/p10/platform/images/knowledge-service.Dockerfile`
- `deploy/p10/platform/images/evaluation-service.Dockerfile`
- `deploy/p10/platform/images/insight-service.Dockerfile`
- `mall/mall-portal/Dockerfile.p10`
- `frontend/apps/aliagent-admin/Dockerfile`
- `frontend/apps/aliagent-admin/nginx.conf`
- `services/gateway-service/src/main/java/com/bn/aliagent/gateway/canary/`
- `services/gateway-service/src/test/java/com/bn/aliagent/gateway/canary/`
- `services/gateway-service/src/main/resources/application-kubernetes.yml`
- `docs/runbooks/p10/platform-*.md`

### P10-B 独占

- 根 `pom.xml` 中仅限遥测版本和公共依赖管理
- `services/gateway-service/pom.xml`、`services/conversation-service/pom.xml`、`services/ai-orchestration-service/pom.xml`
- `services/knowledge-service/pom.xml`、`services/evaluation-service/pom.xml`、`services/insight-service/pom.xml` 中仅限遥测依赖
- `mall/pom.xml`、`mall/mall-portal/pom.xml` 中仅限 Micrometer/镜像插件解耦
- 六个独立服务各自的 `src/main/resources/application-observability.yml`
- `mall/mall-portal/src/main/resources/application-observability.yml`
- 六个独立服务各自的 `src/main/java/<服务包>/telemetry/`
- 六个独立服务各自的 `src/test/java/<服务包>/telemetry/`
- `deploy/p10/observability/`
- `docs/runbooks/p10/observability-*.md`

### P10-C 独占

- `platform/platform-service-security/`
- `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/security/`
- `services/knowledge-service/src/test/java/com/bn/aliagent/knowledge/security/`
- `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/api/KnowledgeController.java`
- `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/storage/`
- `services/knowledge-service/src/test/java/com/bn/aliagent/knowledge/storage/`
- `services/conversation-service/src/main/java/com/bn/aliagent/conversation/retention/` 及对应测试
- `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/retention/` 及对应测试
- `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/retention/` 及对应测试
- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/retention/` 及对应测试
- `services/insight-service/src/main/java/com/bn/aliagent/insight/retention/` 及对应测试
- `mall/mall-portal/src/main/java/com/macro/mall/portal/retention/` 及对应测试
- `services/conversation-service/src/main/resources/db/migration/V10__p10_tenant_deletion.sql`
- `services/ai-orchestration-service/src/main/resources/db/migration/V5__p10_tenant_deletion.sql`
- `services/knowledge-service/src/main/resources/db/migration/V6__p10_tenant_deletion.sql`
- `services/evaluation-service/src/main/resources/db/migration/V15__p10_tenant_deletion.sql`
- `services/insight-service/src/main/resources/db/migration/V3__p10_tenant_deletion.sql`
- `mall/mall-portal/src/main/resources/db/migration/V4__p10_tenant_deletion.sql`
- `mall/mall-portal/src/main/java/com/macro/mall/portal/internal/read/Hs256ServiceIdentityVerifier.java`
- `mall/mall-portal/src/main/java/com/macro/mall/portal/internal/read/InternalReadFilterConfiguration.java`
- `deploy/p10/security/`
- `deploy/p10/recovery/`
- `tools/p10-security/`
- `docs/runbooks/p10/security-*.md`、`docs/runbooks/p10/recovery-*.md`

### P10-D 独占

- `tests/p10/`
- `tools/p10-acceptance/`
- `tools/p10-data/`
- `docs/reports/p10/`
- `docs/runbooks/p10/capacity-*.md`

### 集成会话独占

- `.gitignore`
- `.github/`
- `contracts/` 中 P10 运维端口和 JSON Schema
- `deploy/p10/README.md`
- 公共 Helm Values 接口冲突
- A/B/C/D 合并冲突
- 最终端口、Secret 键名、镜像清单和验收入口
- 本计划以外任何公共文件

任务会话发现必须修改他人所有文件时，只记录接口缺口并停止修改，由集成会话处理。

## 5. 分计划

- P10-A：`docs/superpowers/plans/2026-08-19-p10-a-platform-deployment-plan.md`
- P10-B：`docs/superpowers/plans/2026-08-19-p10-b-observability-plan.md`
- P10-C：`docs/superpowers/plans/2026-08-19-p10-c-security-recovery-plan.md`
- P10-D：`docs/superpowers/plans/2026-08-19-p10-d-capacity-acceptance-plan.md`

## 6. 第一波并行和提交要求

A、B、C 从同一冻结提交开始，可同时执行。每个任务必须：

1. 使用对应 Worktree，不切换到他人分支。
2. 按分计划进行 TDD，先看到目标测试失败。
3. 每个逻辑任务单独提交，禁止一个巨型提交。
4. 执行所属模块测试、静态检查和 `git diff --check`。
5. 保持工作区干净后向集成会话报告提交 SHA、验证证据和接口缺口。
6. 不推送、不创建 PR、不合入 `master`，除非协调者明确要求。

## 7. 第一波集成

集成顺序固定为 A → B → C：

```powershell
git switch codex/integration-p10
git merge --no-ff codex/p10-platform-deployment
git merge --no-ff codex/p10-observability
git merge --no-ff codex/p10-security-recovery
```

合并后执行：

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -f mall/pom.xml -pl mall-portal -am test -DskipTests=false -Ddocker.skip=true
pnpm --dir frontend --filter @aliagent/admin build
helm lint deploy/p10/helm/aliagent
helm lint deploy/p10/observability/helm/aliagent-observability
git diff --check
```

然后执行平台启动、遥测关联、灰度回退、文件扫描、密钥轮换和恢复冒烟。未通过前不得创建 P10-D Worktree。

## 8. 第二波 P10-D

P10-D 从已集成 A/B/C 的提交派生，开发 k6 场景、数据生成器和最终验收编排。D 不修复平台实现；发现缺陷时提交最小复现和证据，由集成会话在对应所有权目录修复。

## 9. 最终集成和阶段门禁

```powershell
git switch codex/integration-p10
git merge --no-ff codex/p10-capacity-acceptance
```

最终验收必须按以下顺序执行：

1. 从干净状态创建 Registry、基础设施和三节点 K3d。
2. 构建并推送 Git SHA 镜像。
3. Helm 部署 Stable，验证迁移、健康检查和滚动升级。
4. 关联一个完整 `traceId` 的指标、日志和 Trace。
5. 部署 Canary，验证测试租户命中、普通租户不命中和一键回退。
6. 验证跨租户拒绝、恶意文件拒绝、双密钥轮换和高风险操作保护。
7. 执行 k6 smoke、baseline、stress、soak 和 failure。
8. 执行隔离恢复演练并测量实际 RPO/RTO。
9. 清理 `test-`、`rag-test-`、`test-restore-` 数据。
10. 生成 `docs/reports/p10/P10-生产验收报告.md`。

阶段出口：所有设计规范第 14 节证据齐全，`RPO ≤ 15 分钟`、`RTO ≤ 2 小时`，性能门禁通过，集成工作区干净。

## 10. 规范覆盖映射

| 设计要求 | 实施任务 |
|---|---|
| K3d、Registry、镜像、Helm、健康、优雅停机、NetworkPolicy、PDB、HPA | P10-A Task 1～4 |
| 可信租户 Canary 与回退 | P10-A Task 5 |
| OTel、Prometheus、Grafana、Loki、Tempo、Span、指标、告警 | P10-B Task 1～6 |
| Secret 轮换、跨租户、文件扫描、高风险操作 | P10-C Task 1～3 |
| 数据删除、备份、PITR、MinIO/Nacos/RabbitMQ、Outbox 重投 | P10-C Task 4～6 |
| 100～300 用户、50 AI、20～50 WebSocket、10 万商品、百万订单 | P10-D Task 2～6 |
| 首 Token、普通 P95、幂等、滚动发布、故障降级 | P10-D Task 3～6 |
| 最终证据与测试数据清理 | P10-D Task 7 与集成会话 |

## 11. 完成后的 Git 流程

完成 P10 集成测试后：

1. 推送 `codex/integration-p10`。
2. 创建到 `master` 的 PR。
3. PR 只包含 P10 设计后的实施提交和验收文档。
4. PR 合并后在 `master` 重跑根 Maven、mall-portal、前端构建和 P10 smoke。
5. 确认合并后再清理 A/B/C/D 分支与 Worktree；含未跟踪用户文件的 Worktree 不得强制删除。
