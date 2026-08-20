# P10-A 平台部署与租户灰度 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Docker Desktop 上建立可重复创建的三节点 K3d 平台，构建并部署 AliAgent 服务镜像，同时实现基于可信租户白名单的 Stable/Canary 路由和快速回退。

**Architecture:** 有状态基础设施运行在独立 Compose，Registry 由 K3d 管理并持久化到 Windows 固定目录，应用通过 Helm 部署到 K3d。Gateway 在认证并覆盖客户端内部头后，根据受控白名单改写已解析的目标 URI，并写入 `X-AliAgent-Track` 供遥测区分 Stable/Canary；数据库迁移使用单独 Flyway CLI Job。

**Tech Stack:** Docker Desktop、Docker Compose、K3d/K3s、Helm 3、PowerShell、Java 17、Spring Boot、Spring Cloud Gateway、Vue 3、Nginx。

---

## 1. 所有权与前置检查

本任务只修改总计划中 P10-A 独占目录。开始时执行：

```powershell
git branch --show-current
git status --short
git rev-parse HEAD
```

Expected：分支为 `codex/p10-platform-deployment`，工作区为空，HEAD 等于 P10 冻结提交。

## 2. Task 1：固定数据目录和本地 Registry

**Files:**
- Create: `deploy/p10/platform/compose.infrastructure.yml`
- Create: `deploy/p10/platform/compose.registry.yml`
- Create: `deploy/p10/platform/.env.example`
- Create: `deploy/p10/platform/Initialize-AliAgentData.ps1`
- Create: `deploy/p10/platform/Test-PlatformCompose.ps1`
- Test: `deploy/p10/platform/tests/PlatformCompose.Tests.ps1`

- [ ] **Step 1：写失败的静态测试**

`PlatformCompose.Tests.ps1` 读取两个 Compose 文件并断言：

```powershell
$compose = Get-Content "$PSScriptRoot\..\compose.infrastructure.yml" -Raw
$registry = Get-Content "$PSScriptRoot\..\compose.registry.yml" -Raw

if ($compose -notmatch '\$\{ALIAGENT_DATA_ROOT\}') { throw '基础设施必须使用 ALIAGENT_DATA_ROOT' }
if ($registry -notmatch '5000:5000') { throw 'Registry 必须暴露 localhost:5000' }
if ($registry -notmatch 'registry/?:/var/lib/registry') { throw 'Registry 必须绑定固定数据目录' }
if ($compose -notmatch '--log-bin=mysql-bin') { throw 'MySQL 必须启用 Binlog' }
if ($compose -notmatch 'archive_timeout=300') { throw 'PostgreSQL WAL 最迟 5 分钟归档' }
```

- [ ] **Step 2：运行并确认失败**

Run：

```powershell
pwsh -NoProfile -File deploy/p10/platform/tests/PlatformCompose.Tests.ps1
```

Expected：FAIL，Compose 文件尚不存在。

- [ ] **Step 3：实现目录初始化和 Compose**

`Initialize-AliAgentData.ps1` 必须使用 `Join-Path` 创建以下目录，不删除已有内容：

```powershell
$children = 'mysql','postgres','redis','rabbitmq','minio','nacos','backups','backups\postgres-wal','observability','observability\prometheus','observability\grafana','observability\loki','observability\tempo','registry'
foreach ($child in $children) {
    New-Item -ItemType Directory -Force -Path (Join-Path $DataRoot $child) | Out-Null
}
```

`compose.infrastructure.yml` 复用现有镜像、端口和健康检查，把命名卷替换为 `${ALIAGENT_DATA_ROOT}/<service>` 绑定挂载；容器统一加入 `aliagent-platform` 外部网络。MySQL 启动参数包含 `--server-id=1 --log-bin=mysql-bin --binlog-format=ROW --binlog-expire-logs-seconds=604800`。PostgreSQL 挂载 `${ALIAGENT_DATA_ROOT}/backups/postgres-wal:/backups/wal` 并配置 `wal_level=replica`、`archive_mode=on`、`archive_timeout=300` 和幂等 `archive_command`。`compose.registry.yml` 只允许在独立 `registry-standalone` Profile 中手工启动，默认平台流程不启动它；Task 2 使用 `k3d registry create` 启动真实 Registry 并绑定相同目录，避免两套 Registry 同时占用 5000 端口。

- [ ] **Step 4：验证**

```powershell
pwsh -NoProfile -File deploy/p10/platform/Initialize-AliAgentData.ps1 -DataRoot 'D:\Java\code\AliAgent\AliAgentData'
docker compose -f deploy/p10/platform/compose.infrastructure.yml --env-file deploy/p10/platform/.env.example config --quiet
docker compose -f deploy/p10/platform/compose.registry.yml --env-file deploy/p10/platform/.env.example config --quiet
pwsh -NoProfile -File deploy/p10/platform/tests/PlatformCompose.Tests.ps1
```

Expected：全部退出码 0；脚本重复执行不改变或删除已有文件。

- [ ] **Step 5：提交**

```powershell
git add deploy/p10/platform
git commit -m "feat(p10-a): add persistent infrastructure and registry"
```

## 3. Task 2：三节点 K3d 生命周期

**Files:**
- Create: `deploy/p10/platform/k3d/registries.yaml`
- Create: `deploy/p10/platform/New-AliAgentCluster.ps1`
- Create: `deploy/p10/platform/Remove-AliAgentCluster.ps1`
- Create: `deploy/p10/platform/Test-AliAgentCluster.ps1`
- Test: `deploy/p10/platform/tests/K3dScripts.Tests.ps1`

- [ ] **Step 1：写失败测试**

断言创建脚本包含三个 Server、连接统一 Docker 网络和 `aliagent-registry`，并映射固定端口：

```powershell
$script = Get-Content "$PSScriptRoot\..\New-AliAgentCluster.ps1" -Raw
foreach ($required in '--servers 3','--agents 0','--network aliagent-platform','aliagent-registry:5000') {
    if ($script -notmatch [regex]::Escape($required)) { throw "缺少 $required" }
}
```

- [ ] **Step 2：确认测试失败**

Run：`pwsh -NoProfile -File deploy/p10/platform/tests/K3dScripts.Tests.ps1`

Expected：FAIL，脚本尚不存在。

- [ ] **Step 3：实现生命周期脚本**

创建命令固定为：

```powershell
k3d registry create aliagent-registry --port 5000 --volume 'D:\Java\code\AliAgent\AliAgentData\registry:/var/lib/registry' `
  --network aliagent-platform
k3d cluster create aliagent-local `
  --servers 3 --agents 0 `
  --network aliagent-platform `
  --registry-use aliagent-registry:5000 `
  --volume 'D:\Java\code\AliAgent\AliAgentData\observability:/var/lib/aliagent-observability@all' `
  --port '8080:80@loadbalancer' `
  --port '13000:30000@server:0' `
  --wait
```

脚本先幂等创建 Docker 网络 `aliagent-platform`；Registry 使用 `k3d registry create` 管理并写入固定数据目录，基础设施 Compose 加入同一外部网络，容器名固定为 `aliagent-mysql`、`aliagent-postgres`、`aliagent-redis`、`aliagent-rabbitmq`、`aliagent-minio`、`aliagent-nacos`。创建后等待三个 Server 和 Traefik `Ready`，并创建三个命名空间。删除脚本必须要求 `-ConfirmClusterName aliagent-local`，只删除 K3d 集群；Registry、Docker 网络、`AliAgentData` 和 Compose 数据默认保留，只有额外提供 `-RemoveRegistry` 才删除 Registry 容器且仍不删除数据目录。

- [ ] **Step 4：验证真实集群**

```powershell
pwsh -NoProfile -File deploy/p10/platform/New-AliAgentCluster.ps1
kubectl get nodes
pwsh -NoProfile -File deploy/p10/platform/Test-AliAgentCluster.ps1
pwsh -NoProfile -File deploy/p10/platform/Remove-AliAgentCluster.ps1 -ConfirmClusterName aliagent-local
```

Expected：三个节点均 `Ready`；删除后 `k3d cluster list` 不含 `aliagent-local`，`k3d registry list` 仍包含 `aliagent-registry`，Registry 数据目录仍存在。

- [ ] **Step 5：提交**

```powershell
git add deploy/p10/platform
git commit -m "feat(p10-a): add three-server k3d lifecycle"
```

## 4. Task 3：应用镜像和静态管理端

**Files:**
- Create: `deploy/p10/platform/images/gateway-service.Dockerfile`
- Create: `deploy/p10/platform/images/conversation-service.Dockerfile`
- Create: `deploy/p10/platform/images/ai-orchestration-service.Dockerfile`
- Create: `deploy/p10/platform/images/knowledge-service.Dockerfile`
- Create: `deploy/p10/platform/images/evaluation-service.Dockerfile`
- Create: `deploy/p10/platform/images/insight-service.Dockerfile`
- Create: `deploy/p10/platform/images/postgres-migrations.Dockerfile`
- Create: `deploy/p10/platform/images/mysql-migrations.Dockerfile`
- Create: `mall/mall-portal/Dockerfile.p10`
- Create: `frontend/apps/aliagent-admin/Dockerfile`
- Create: `frontend/apps/aliagent-admin/nginx.conf`
- Create: `deploy/p10/platform/Build-PushImages.ps1`
- Test: `deploy/p10/platform/tests/ContainerFiles.Tests.ps1`

- [ ] **Step 1：写容器安全失败测试**

测试遍历 Dockerfile，要求非 Root 用户、健康检查兼容端点和不可变基础镜像标签：

```powershell
foreach ($file in $dockerfiles) {
    $text = Get-Content $file -Raw
    if ($text -notmatch '(?m)^USER\s+') { throw "$file 未设置非 Root USER" }
    if ($text -match '(?m)^FROM\s+[^\s]+:latest') { throw "$file 使用 latest" }
}
```

- [ ] **Step 2：运行并确认失败**

Run：`pwsh -NoProfile -File deploy/p10/platform/tests/ContainerFiles.Tests.ps1`

Expected：FAIL，Dockerfile 尚不存在。

- [ ] **Step 3：实现 Java 镜像**

六个 Spring Boot 3 服务使用相同运行模式，但每个 Dockerfile 只复制所属 JAR；Docker build context 固定为仓库根目录：

```dockerfile
FROM eclipse-temurin:17-jre-jammy
RUN useradd --system --uid 10001 --create-home aliagent
WORKDIR /app
COPY services/gateway-service/target/gateway-service-0.0.1-SNAPSHOT.jar app.jar
USER 10001
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
```

`mall-portal` 使用与其 Java 8 字节码兼容的 Temurin 17 JRE 运行，构建前使用 `-Ddocker.skip=true` 绕过上游固定 Docker Host。管理端使用 Node 构建阶段和非 Root Nginx 运行阶段，`/api/` 反向代理至 Gateway Service。

`postgres-migrations.Dockerfile` 基于固定版本 Flyway CLI，复制五个 PostgreSQL 服务的 `db/migration/` 到独立 locations；`mysql-migrations.Dockerfile` 复制 `mall-portal` 迁移并包含固定版本 MySQL 驱动。迁移 Job 不复用业务进程启动，避免 RabbitMQ Listener、Scheduler 或 Web Server 导致 Job 不退出。

- [ ] **Step 4：实现构建推送脚本**

`Build-PushImages.ps1 -GitSha <sha>` 必须拒绝空 SHA 和 `latest`，依次执行 Maven、pnpm、`docker build`、`docker push`，镜像格式：

```text
localhost:5000/aliagent/gateway-service:<sha>
localhost:5000/aliagent/conversation-service:<sha>
localhost:5000/aliagent/ai-orchestration-service:<sha>
localhost:5000/aliagent/knowledge-service:<sha>
localhost:5000/aliagent/evaluation-service:<sha>
localhost:5000/aliagent/insight-service:<sha>
localhost:5000/aliagent/mall-portal:<sha>
localhost:5000/aliagent/aliagent-admin:<sha>
localhost:5000/aliagent/postgres-migrations:<sha>
localhost:5000/aliagent/mysql-migrations:<sha>
```

- [ ] **Step 5：验证**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd package -DskipTests
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -f mall/pom.xml -pl mall-portal -am package -DskipTests -Ddocker.skip=true
pnpm --dir frontend --filter @aliagent/admin build
pwsh -NoProfile -File deploy/p10/platform/tests/ContainerFiles.Tests.ps1
pwsh -NoProfile -File deploy/p10/platform/Build-PushImages.ps1 -GitSha (git rev-parse --short=12 HEAD)
curl.exe http://localhost:5000/v2/_catalog
```

Expected：八个仓库可在 Registry 目录中查询；容器以非 Root 用户运行。

- [ ] **Step 6：提交**

```powershell
git add deploy/p10/platform/images mall/mall-portal/Dockerfile.p10 frontend/apps/aliagent-admin deploy/p10/platform/Build-PushImages.ps1 deploy/p10/platform/tests/ContainerFiles.Tests.ps1
git commit -m "feat(p10-a): add immutable application images"
```

## 5. Task 4：Helm Chart、迁移和运行治理

**Files:**
- Create: `deploy/p10/helm/aliagent/Chart.yaml`
- Create: `deploy/p10/helm/aliagent/values.yaml`
- Create: `deploy/p10/helm/aliagent/values-local.yaml`
- Create: `deploy/p10/helm/aliagent/values-test.yaml`
- Create: `deploy/p10/helm/aliagent/values-prod.yaml`
- Create: `deploy/p10/helm/aliagent/values.schema.json`
- Create: `deploy/p10/helm/aliagent/templates/_helpers.tpl`
- Create: `deploy/p10/helm/aliagent/templates/deployment.yaml`
- Create: `deploy/p10/helm/aliagent/templates/service.yaml`
- Create: `deploy/p10/helm/aliagent/templates/configmap.yaml`
- Create: `deploy/p10/helm/aliagent/templates/migration-job.yaml`
- Create: `deploy/p10/helm/aliagent/templates/networkpolicy.yaml`
- Create: `deploy/p10/helm/aliagent/templates/pdb.yaml`
- Create: `deploy/p10/helm/aliagent/templates/hpa.yaml`
- Create: `deploy/p10/helm/aliagent/templates/serviceaccount.yaml`
- Create: `deploy/p10/helm/aliagent/templates/admin.yaml`
- Create: `deploy/p10/helm/aliagent/templates/ingress.yaml`
- Create: `deploy/p10/platform/Install-AliAgent.ps1`
- Test: `deploy/p10/platform/tests/HelmChart.Tests.ps1`

- [ ] **Step 1：写 Helm 渲染失败测试**

测试执行：

```powershell
helm lint deploy/p10/helm/aliagent -f deploy/p10/helm/aliagent/values-local.yaml
helm template aliagent deploy/p10/helm/aliagent -f deploy/p10/helm/aliagent/values-local.yaml | Out-File $rendered
```

并断言所有应用容器包含 `runAsNonRoot`、CPU/内存 request/limit、三类 Probe、`terminationGracePeriodSeconds`，且 Secret 只通过 `secretKeyRef` 引用。

- [ ] **Step 2：运行并确认失败**

Expected：FAIL，Chart 尚不存在。

- [ ] **Step 3：实现 Chart**

Values 必须定义八个部署项、两个迁移镜像、Stable/Canary 轨道、镜像 SHA、外部基础设施容器 DNS 和 Secret 名称。数据库服务使用迁移 Job 串行执行 Flyway CLI；应用 Deployment 启动参数关闭自动 Flyway，避免多副本竞争。Local Ingress 通过 Traefik 将 `localhost:8080` 路由至 Gateway；管理端使用 NodePort `30000`，由 K3d 映射为 `localhost:13000`。

Probe 路径：

```yaml
startupProbe:
  httpGet: { path: /actuator/health/liveness, port: http }
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: http }
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: http }
```

Java 服务启用 `server.shutdown=graceful`、`management.endpoint.health.probes.enabled=true`。核心服务本地 2 副本，其他服务和 `mall-portal` 本地 1 副本；Prod Values 按设计提升副本。HPA 本地 `enabled: false`，Prod Values 可启用。

- [ ] **Step 4：验证渲染和安装**

```powershell
pwsh -NoProfile -File deploy/p10/platform/tests/HelmChart.Tests.ps1
pwsh -NoProfile -File deploy/p10/platform/Install-AliAgent.ps1 -Track stable -ImageTag (git rev-parse --short=12 HEAD)
kubectl -n aliagent-system get deploy,pod,svc,job
kubectl -n aliagent-system rollout status deployment/gateway-service --timeout=180s
```

Expected：所有迁移 Job 成功，Pod Ready，未在模板中出现 Secret 明文。

- [ ] **Step 5：提交**

```powershell
git add deploy/p10/helm deploy/p10/platform services/gateway-service/src/main/resources/application-kubernetes.yml
git commit -m "feat(p10-a): add governed helm deployment"
```

## 6. Task 5：可信租户 Canary 路由

**Files:**
- Create: `services/gateway-service/src/main/java/com/bn/aliagent/gateway/canary/CanaryTenantMatcher.java`
- Create: `services/gateway-service/src/main/java/com/bn/aliagent/gateway/canary/FileBackedCanaryTenantMatcher.java`
- Create: `services/gateway-service/src/main/java/com/bn/aliagent/gateway/canary/CanaryRoutingGatewayFilter.java`
- Create: `services/gateway-service/src/test/java/com/bn/aliagent/gateway/canary/FileBackedCanaryTenantMatcherTest.java`
- Create: `services/gateway-service/src/test/java/com/bn/aliagent/gateway/canary/CanaryRoutingGatewayFilterTest.java`
- Modify: `services/gateway-service/src/main/java/com/bn/aliagent/gateway/TrustedIdentityGatewayFilter.java`
- Modify: `services/gateway-service/src/test/java/com/bn/aliagent/gateway/TrustedIdentityGatewayFilterTest.java`
- Modify: `services/gateway-service/src/main/resources/application-kubernetes.yml`
- Create: `deploy/p10/platform/Set-CanaryTenants.ps1`

- [ ] **Step 1：写失败测试**

核心断言：

```java
@Test
void clientCannotForgeTrackAndTrustedTenantSelectsCanary() {
    var matcher = tenant -> tenant.equals("test-canary");
    var exchange = trustedExchange("test-canary", "canary-from-client");
    filter(matcher).filter(exchange, chainCapturingHeaders()).block();
    assertEquals("canary", forwarded.getFirst("X-AliAgent-Track"));
}

@Test
void stableTenantCannotBeForcedIntoCanary() {
    var exchange = trustedExchange("tenant-stable", "canary");
    filter(tenant -> false).filter(exchange, chainCapturingHeaders()).block();
    assertEquals("stable", forwarded.getFirst("X-AliAgent-Track"));
}
```

同时测试文件热加载：完整行匹配、空行和 `#` 注释忽略、读取失败保持最后一次成功快照、空文件等同全部 Stable。

- [ ] **Step 2：运行并确认失败**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/gateway-service -Dtest=FileBackedCanaryTenantMatcherTest,CanaryRoutingGatewayFilterTest,TrustedIdentityGatewayFilterTest test
```

Expected：FAIL，新类和 `X-AliAgent-Track` 清洗尚不存在。

- [ ] **Step 3：实现最小路由**

- 将 `X-AliAgent-Track` 加入 Gateway 内部头清单，客户端值必须先删除。
- `FileBackedCanaryTenantMatcher` 仅加载 `/etc/aliagent/canary/tenants.txt`，每 5 秒检查修改时间，使用不可变 Set 原子替换。
- `CanaryRoutingGatewayFilter` 只读取已由 `TrustedIdentityGatewayFilter` 注入的可信 `X-Tenant-Id`，写入 `stable|canary`。
- Spring Cloud Gateway 的路由谓词早于 GlobalFilter 执行，因此不得使用“先写 Header、再用 Header Predicate 选路由”。`CanaryRoutingGatewayFilter` 的 Order 固定为 `10001`，在 `RouteToRequestUrlFilter`（10000）之后、Netty 路由之前运行；它根据已匹配的 route ID 和可信租户，把 `ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR` 从 Stable URI 改写为对应的 `*-canary.aliagent-canary.svc.cluster.local`。未启用 Canary 的服务保持 Stable URI。
- `Set-CanaryTenants.ps1` 只接受 `test-` 租户，除非显式提供 `-AllowNonTestTenant` 和二次确认令牌；空列表执行回退。

- [ ] **Step 4：验证**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/gateway-service test
pwsh -NoProfile -File deploy/p10/platform/Set-CanaryTenants.ps1 -TenantId test-p10-canary
# 用两个测试 JWT 请求同一 API，分别确认 Canary/Stable Pod 日志中的 deployment.track
pwsh -NoProfile -File deploy/p10/platform/Set-CanaryTenants.ps1 -Clear
```

Expected：伪造 Track 无效；测试租户命中 Canary；清空后全部回到 Stable。

- [ ] **Step 5：提交**

```powershell
git add services/gateway-service deploy/p10/platform deploy/p10/helm/aliagent
git commit -m "feat(p10-a): add trusted tenant canary routing"
```

## 7. Task 6：平台任务验收

**Files:**
- Create: `docs/runbooks/p10/platform-local-topology.md`
- Create: `docs/runbooks/p10/platform-canary-rollback.md`
- Modify: `deploy/p10/platform/Test-AliAgentCluster.ps1`

- [ ] **Step 1：运行所属测试**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/gateway-service test
pnpm --dir frontend --filter @aliagent/admin build
pwsh -NoProfile -File deploy/p10/platform/tests/PlatformCompose.Tests.ps1
pwsh -NoProfile -File deploy/p10/platform/tests/K3dScripts.Tests.ps1
pwsh -NoProfile -File deploy/p10/platform/tests/ContainerFiles.Tests.ps1
pwsh -NoProfile -File deploy/p10/platform/tests/HelmChart.Tests.ps1
helm lint deploy/p10/helm/aliagent -f deploy/p10/helm/aliagent/values-local.yaml
git diff --check
```

- [ ] **Step 2：运行真实部署冒烟**

从空 K3d 集群执行 Registry、基础设施、镜像、Helm、Stable、Canary、回退和销毁重建。记录三个节点、Pod 分布、健康检查和 Registry 保留证据。

- [ ] **Step 3：提交文档并报告**

```powershell
git add docs/runbooks/p10 deploy/p10 services/gateway-service
git commit -m "docs(p10-a): document platform deployment and rollback"
git status --short
git log --oneline --decorate -6
```

Expected：工作区为空。向集成会话报告最终 SHA、验证命令、实际端口和任何公共接口缺口。
