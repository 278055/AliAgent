# P10-C 安全与恢复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立双密钥轮换、跨租户安全验证、知识文件隔离扫描、受控高风险操作以及可测量 RPO/RTO 的备份恢复链路。

**Architecture:** 服务 JWT 验证支持主/次验证密钥但只使用主密钥签发；知识文件先进入 quarantine，验证 MIME、魔数和扫描结果后才进入 trusted 存储和摄入；备份与恢复使用显式目标环境、隔离实例和删除清单，Outbox 重投通过白名单工具完成。

**Tech Stack:** Java 17、Spring Boot、JJWT、MinIO、Apache Tika、PowerShell、Docker Compose、MySQL Binlog、PostgreSQL WAL/PITR、Nacos、RabbitMQ。

---

## 1. 所有权与前置检查

只修改总计划中 P10-C 独占文件。开始执行：

```powershell
git branch --show-current
git status --short
git rev-parse HEAD
```

Expected：`codex/p10-security-recovery`，工作区为空，HEAD 为共同冻结提交。

## 2. Task 1：服务 JWT 双密钥轮换

**Files:**
- Modify: `platform/platform-service-security/src/main/java/com/bn/platform/security/ServiceJwtSupport.java`
- Modify: `platform/platform-service-security/src/main/java/com/bn/platform/security/ServiceJwtSecurityConfiguration.java`
- Modify: `platform/platform-service-security/src/test/java/com/bn/platform/security/ServiceJwtAuthenticationFilterTest.java`
- Create: `platform/platform-service-security/src/test/java/com/bn/platform/security/ServiceJwtKeyRotationTest.java`
- Modify: `mall/mall-portal/src/main/java/com/macro/mall/portal/internal/read/Hs256ServiceIdentityVerifier.java`
- Modify: `mall/mall-portal/src/main/java/com/macro/mall/portal/internal/read/InternalReadFilterConfiguration.java`
- Create: `mall/mall-portal/src/test/java/com/macro/mall/portal/internal/read/Hs256ServiceIdentityRotationTest.java`
- Create: `deploy/p10/security/Rotate-ServiceJwt.ps1`

- [ ] **Step 1：写失败测试**

```java
@Test
void signsWithPrimaryAndVerifiesPrimaryOrSecondary() {
    var oldSigner = new ServiceJwtSupport(OLD_KEY);
    String oldToken = oldSigner.issue("gateway-service", "conversation-service", List.of("GET:/api/v1/conversations"));
    var rotating = new ServiceJwtSupport(NEW_KEY, List.of(OLD_KEY));
    rotating.verify(oldToken, "conversation-service", "GET:/api/v1/conversations");
    rotating.verify(rotating.issue("gateway-service", "conversation-service", List.of("GET:/api/v1/conversations")),
            "conversation-service", "GET:/api/v1/conversations");
}

@Test
void rejectsOldKeyAfterSecondaryIsRemoved() {
    assertThrows(RuntimeException.class, () -> new ServiceJwtSupport(NEW_KEY).verify(oldToken, audience, scope));
}
```

同时在 mall verifier 测试相同过渡语义。

- [ ] **Step 2：运行并确认失败**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl platform/platform-service-security -Dtest=ServiceJwtKeyRotationTest test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -f mall/pom.xml -pl mall-portal -Dtest=Hs256ServiceIdentityRotationTest test -DskipTests=false -Ddocker.skip=true
```

Expected：FAIL，构造函数和多密钥验证尚不存在。

- [ ] **Step 3：实现最小双密钥模型**

`ServiceJwtSupport` 保留现有单参数构造函数，新增：

```java
public ServiceJwtSupport(String primarySecret, List<String> verificationSecrets)
```

签发永远使用 Primary；验证按 Primary、Verification 列表依次尝试。最多允许两个有效密钥，所有密钥至少 32 字节；错误消息不得暴露命中哪个密钥。配置读取：

```text
SERVICE_JWT_PRIMARY_SECRET
SERVICE_JWT_SECONDARY_SECRET（可空）
```

为兼容现有环境，Primary 未配置时读取 `SERVICE_JWT_SECRET`；Prod Helm 必须配置 Primary。

- [ ] **Step 4：实现轮换脚本**

`Rotate-ServiceJwt.ps1` 只输出步骤和更新 Kubernetes Secret：

1. 新密钥成为 Primary，旧密钥成为 Secondary。
2. 滚动所有验证方并执行双向调用冒烟。
3. 等待最长 Token TTL 5 分钟。
4. 需要 `-StepUpToken` 才能删除 Secondary。
5. 删除后再次验证旧 Token 被拒绝、新 Token 正常。

脚本日志只输出密钥 SHA-256 指纹前 12 位，不输出值。

- [ ] **Step 5：验证并提交**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl platform/platform-service-security -am test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -f mall/pom.xml -pl mall-portal -am test -DskipTests=false -Ddocker.skip=true
git add platform mall/mall-portal/src/main/java/com/macro/mall/portal/internal/read mall/mall-portal/src/test/java/com/macro/mall/portal/internal/read deploy/p10/security
git commit -m "feat(p10-c): support audited service jwt rotation"
```

## 3. Task 2：知识文件隔离与扫描

**Files:**
- Create: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/security/KnowledgeFilePolicy.java`
- Create: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/security/KnowledgeFileInspection.java`
- Create: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/security/KnowledgeFileScanner.java`
- Create: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/security/TikaKnowledgeFilePolicy.java`
- Create: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/security/ClamAvKnowledgeFileScanner.java`
- Create: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/security/KnowledgeUploadSecurityService.java`
- Test: `services/knowledge-service/src/test/java/com/bn/aliagent/knowledge/security/KnowledgeFilePolicyTest.java`
- Test: `services/knowledge-service/src/test/java/com/bn/aliagent/knowledge/security/KnowledgeUploadSecurityServiceTest.java`
- Test: `services/knowledge-service/src/test/java/com/bn/aliagent/knowledge/security/ClamAvKnowledgeFileScannerTest.java`
- Modify: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/api/KnowledgeController.java`
- Modify: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/storage/KnowledgeObjectStorage.java`
- Modify: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/storage/MinioKnowledgeObjectStorage.java`
- Test: `services/knowledge-service/src/test/java/com/bn/aliagent/knowledge/storage/MinioKnowledgeObjectStorageTest.java`

- [ ] **Step 1：写失败测试**

覆盖：

```java
@Test
void rejectsDoubleExtensionAndMagicMismatch() throws Exception {
    byte[] executable = new byte[] { 'M', 'Z', 0, 0 };
    var upload = TestUpload.of("guide.pdf.exe", "application/pdf", executable);
    assertThrows(KnowledgeFileRejectedException.class, () -> policy.inspect(upload));
}

@Test
void keepsFileInQuarantineUntilScannerPasses() throws Exception {
    var storage = new RecordingStorage();
    var service = new KnowledgeUploadSecurityService(policy, source -> ScanResult.clean("test"), storage);
    service.accept(TestUpload.text("guide.md", "safe"), "test-tenant");
    assertEquals(List.of("put:quarantine", "scan:quarantine", "promote:trusted", "delete:quarantine"), storage.actions());
}

@Test
void scannerTimeoutFailsClosedWithoutCreatingIngestionTask() {
    var scanner = (KnowledgeFileScanner) source -> { throw new ScanTimeoutException(); };
    assertThrows(KnowledgeFileRejectedException.class,
            () -> new KnowledgeUploadSecurityService(policy, scanner, storage).accept(TestUpload.text("guide.md", "safe"), "test-tenant"));
    assertEquals(0, taskRepository.count());
}

@Test
void normalizedObjectKeyCannotEscapeTenantPrefix() {
    assertThrows(KnowledgeFileRejectedException.class,
            () -> policy.inspect(TestUpload.text("../../secret.md", "safe")));
}
```

允许类型先冻结为 UTF-8 文本、Markdown、PDF、DOCX；最大文件大小从 `KNOWLEDGE_UPLOAD_MAX_BYTES` 读取，本地默认 20 MiB。

- [ ] **Step 2：运行并确认失败**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/knowledge-service -Dtest=*KnowledgeFile*Test,MinioKnowledgeObjectStorageTest test
```

Expected：FAIL，安全服务和 quarantine 操作尚不存在。

- [ ] **Step 3：实现安全管道**

上传顺序必须固定：

```text
校验大小/文件名 → 计算摘要和 Tika MIME/魔数 → 写 quarantine
→ 扫描 → 复制到 trusted → 删除 quarantine
→ 同一数据库事务创建 document/version/task/outbox
```

接口：

```java
public interface KnowledgeFileScanner {
    ScanResult scan(InputStream content, long contentLength, String detectedMediaType);
}

public record ScanResult(boolean clean, String engine, String signature, String diagnosticCode) { }
```

ClamAV 不可用、超时或返回未知状态时 `clean=false`。数据库只保存受控 `diagnosticCode`，不保存扫描器原始输出。MinIO bucket/prefix 使用 `quarantine/<tenant>/<uuid>` 和 `trusted/<tenant>/<uuid>`。

- [ ] **Step 4：验证**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/knowledge-service -am test
```

使用 EICAR 测试字符串作为 `test-p10-eicar.txt`，Expected：请求被拒绝、无 ingestion_task、quarantine 对象按失败保留策略清理、日志无文件正文。

- [ ] **Step 5：提交**

```powershell
git add services/knowledge-service
git commit -m "feat(p10-c): quarantine and scan knowledge uploads"
```

## 4. Task 3：租户删除编排与恢复隔离

**Files:**
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/retention/TenantDeletionService.java`
- Create: `services/conversation-service/src/main/java/com/bn/aliagent/conversation/retention/TenantDeletionController.java`
- Test: `services/conversation-service/src/test/java/com/bn/aliagent/conversation/retention/TenantDeletionServiceTest.java`
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/retention/TenantDeletionService.java`
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/retention/TenantDeletionController.java`
- Test: `services/ai-orchestration-service/src/test/java/com/bn/aliagent/orchestration/retention/TenantDeletionServiceTest.java`
- Create: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/retention/TenantDeletionService.java`
- Create: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/retention/TenantDeletionController.java`
- Test: `services/knowledge-service/src/test/java/com/bn/aliagent/knowledge/retention/TenantDeletionServiceTest.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/retention/TenantDeletionService.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/retention/TenantDeletionController.java`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/retention/TenantDeletionServiceTest.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/retention/TenantDeletionService.java`
- Create: `services/insight-service/src/main/java/com/bn/aliagent/insight/retention/TenantDeletionController.java`
- Test: `services/insight-service/src/test/java/com/bn/aliagent/insight/retention/TenantDeletionServiceTest.java`
- Create: `mall/mall-portal/src/main/java/com/macro/mall/portal/retention/TenantDeletionService.java`
- Create: `mall/mall-portal/src/main/java/com/macro/mall/portal/retention/TenantDeletionController.java`
- Test: `mall/mall-portal/src/test/java/com/macro/mall/portal/retention/TenantDeletionServiceTest.java`
- Create: `services/conversation-service/src/main/resources/db/migration/V10__p10_tenant_deletion.sql`
- Create: `services/ai-orchestration-service/src/main/resources/db/migration/V5__p10_tenant_deletion.sql`
- Create: `services/knowledge-service/src/main/resources/db/migration/V6__p10_tenant_deletion.sql`
- Create: `services/evaluation-service/src/main/resources/db/migration/V15__p10_tenant_deletion.sql`
- Create: `services/insight-service/src/main/resources/db/migration/V3__p10_tenant_deletion.sql`
- Create: `mall/mall-portal/src/main/resources/db/migration/V4__p10_tenant_deletion.sql`
- Create: `deploy/p10/security/Invoke-TenantDeletion.ps1`
- Create: `deploy/p10/security/Verify-TenantDeletion.ps1`

- [ ] **Step 1：写失败测试**

每个服务使用相同命令语义：

```java
public record TenantDeletionCommand(
        String tenantId, UUID deletionManifestId, String requestedBy,
        String reason, String stepUpToken, boolean recoveryReplay) { }
```

领域测试至少覆盖：

```java
@Test
void sameManifestIsIdempotentAndDeletesOnlyTargetTenant() {
    UUID manifest = UUID.randomUUID();
    repository.seed("test-delete-a");
    repository.seed("test-delete-b");
    service.delete(command("test-delete-a", manifest));
    service.delete(command("test-delete-a", manifest));
    assertEquals(0, repository.countTenantRows("test-delete-a"));
    assertTrue(repository.countTenantRows("test-delete-b") > 0);
    assertEquals("COMPLETED", repository.request(manifest).status());
}

@Test
void missingStepUpFailsBeforeAnyDelete() {
    assertThrows(TenantDeletionRejectedException.class,
            () -> service.delete(new TenantDeletionCommand("test-delete-a", UUID.randomUUID(), "staff", "test", "", false)));
    assertTrue(repository.countTenantRows("test-delete-a") > 0);
}
```

Knowledge 额外断言删除 PostgreSQL 向量、MinIO `trusted/<tenant>/` 和 `quarantine/<tenant>/`；Conversation 额外清理 `conversation:<tenant>:` Redis keys。

- [ ] **Step 2：运行并确认失败**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/conversation-service,services/ai-orchestration-service,services/knowledge-service,services/evaluation-service,services/insight-service -Dtest=TenantDeletionServiceTest test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -f mall/pom.xml -pl mall-portal -Dtest=TenantDeletionServiceTest test -DskipTests=false -Ddocker.skip=true
```

Expected：FAIL，删除服务、控制器和幂等表尚不存在。

- [ ] **Step 3：实现统一内部端口和持久化状态**

每个服务提供：

```text
POST /internal/api/v1/retention/tenants/{tenantId}:delete
X-Service-Authorization: Bearer <service-jwt>
X-Deletion-Manifest-Id: <uuid>
X-Step-Up-Token: <short-lived-token>
```

请求体只包含 `requestedBy`、`reason`、`recoveryReplay`。控制器同时验证 Service JWT scope、路径 tenant 与命令 tenant 一致、Step-up Token audience 为 `tenant-deletion`。每个数据库新增 `tenant_deletion_request`：`manifest_id` 主键、`tenant_id`、`status`、`requested_by`、`reason_digest`、`recovery_replay`、开始/完成/失败时间和受控错误码；重复 Manifest 返回已持久化结果。

删除顺序由每个服务显式维护，先删子表再删父表。必须保留最小删除审计和 Manifest ID，但不保留已删除租户的正文或可识别数据。Mall 只删除 P6/P9 AliAgent 扩展表中的租户数据，不删除上游商城订单事实；上游会员注销继续由 mall 原业务负责，P10 报告必须写明边界。

- [ ] **Step 4：实现跨服务编排与核验**

`Invoke-TenantDeletion.ps1` 固定顺序调用 Conversation → AI Orchestration → Knowledge → Evaluation → Insight → mall 扩展端口；状态写入 `AliAgentData/backups/deletion-manifests/<manifest>.json`，只保存租户哈希、服务结果、时间和错误码。部分失败时以同一 Manifest 重试，已完成服务返回幂等成功。

`Verify-TenantDeletion.ps1` 使用只读查询和 MinIO/Redis 检查，要求所有业务数据为 0、删除审计为 1。脚本默认只允许 `test-` 租户；非测试租户需要 `-AllowNonTestTenant`、短期 Step-up Token 和明确 `-ConfirmTenantId`。

- [ ] **Step 5：空库迁移、端到端验证和提交**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/conversation-service,services/ai-orchestration-service,services/knowledge-service,services/evaluation-service,services/insight-service -am test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -f mall/pom.xml -pl mall-portal -am test -DskipTests=false -Ddocker.skip=true
pwsh -NoProfile -File deploy/p10/security/Invoke-TenantDeletion.ps1 -TenantId test-p10-delete -DeletionManifestId ([guid]::NewGuid()) -StepUpToken $env:P10_STEP_UP_TOKEN -ConfirmTenantId test-p10-delete
pwsh -NoProfile -File deploy/p10/security/Verify-TenantDeletion.ps1 -TenantId test-p10-delete
git add services mall/mall-portal deploy/p10/security
git commit -m "feat(p10-c): orchestrate idempotent tenant deletion"
```

Expected：全部测试通过；六个数据库空库迁移成功；目标租户数据、向量、对象和缓存被清理；其他租户不受影响。

## 5. Task 4：跨租户与高风险操作安全套件

**Files:**
- Create: `tools/p10-security/P10CrossTenantAcceptance.java`
- Create: `tools/p10-security/P10HighRiskOperationAcceptance.java`
- Create: `deploy/p10/security/Invoke-CrossTenantTests.ps1`
- Create: `deploy/p10/security/Confirm-StepUp.ps1`
- Create: `deploy/p10/security/tests/SecurityScripts.Tests.ps1`

- [ ] **Step 1：写安全用例清单测试**

PowerShell 测试要求跨租户程序显式覆盖以下关键字：

```powershell
$required = 'REST','SSE','WebSocket','RabbitMQ','RAG','mall-tool','evaluation','insight','forged-header'
foreach ($item in $required) {
    if ($source -notmatch [regex]::Escape($item)) { throw "缺少安全场景 $item" }
}
```

- [ ] **Step 2：运行并确认失败**

Expected：FAIL，安全套件尚不存在。

- [ ] **Step 3：实现测试程序**

创建 `test-p10-tenant-a` 和 `test-p10-tenant-b` 两套身份与资源，逐项验证：

- B 无法读取/写入 A 的会话、消息、队列、知识、评测、洞察和订单证据。
- 伪造 `X-Tenant-Id`、`X-Subject-Id`、`X-User-Roles`、`X-AliAgent-Track` 被覆盖。
- MQ 构造信封 tenant 与可信上下文不符时被拒绝或死信，不产生事实。
- RAG 结果只来自当前租户；mall 工具拒绝非归属订单。
- SSE/WebSocket 不泄露另一租户事件。

高风险测试要求缺少 Step-up Token 时拒绝租户全删、最终撤销旧密钥、非测试租户 Canary 扩大、生产恢复覆盖和大批量 Outbox 重投。

- [ ] **Step 4：验证和清理**

```powershell
pwsh -NoProfile -File deploy/p10/security/Invoke-CrossTenantTests.ps1
pwsh -NoProfile -File deploy/p10/security/tests/SecurityScripts.Tests.ps1
```

Expected：所有拒绝场景通过，随后清理两个 `test-p10-tenant-*` 的数据和对象。

- [ ] **Step 5：提交**

```powershell
git add tools/p10-security deploy/p10/security
git commit -m "test(p10-c): add cross-tenant and step-up acceptance"
```

## 6. Task 5：备份与连续归档

**Files:**
- Create: `deploy/p10/recovery/backup/Backup-MySql.ps1`
- Create: `deploy/p10/recovery/backup/Backup-Postgres.ps1`
- Create: `deploy/p10/recovery/backup/Backup-MinIo.ps1`
- Create: `deploy/p10/recovery/backup/Export-Nacos.ps1`
- Create: `deploy/p10/recovery/backup/Export-RabbitDefinitions.ps1`
- Create: `deploy/p10/recovery/backup/New-BackupManifest.ps1`
- Create: `deploy/p10/recovery/backup/Test-ArchiveLag.ps1`
- Create: `deploy/p10/recovery/tests/BackupScripts.Tests.ps1`

- [ ] **Step 1：写失败测试**

测试要求每个脚本具有 `-Environment`、`-Destination`、`-BackupId`，拒绝空目标，文件名含 UTC 时间，生成 SHA-256 清单，不在命令输出中显示密码。

- [ ] **Step 2：确认失败**

Run：`pwsh -NoProfile -File deploy/p10/recovery/tests/BackupScripts.Tests.ps1`

Expected：FAIL，备份脚本尚不存在。

- [ ] **Step 3：实现备份**

- MySQL：`mysqldump --single-transaction --routines --events`，同时记录 `SHOW MASTER STATUS`/GTID 或等价 Binlog 位置。
- PostgreSQL：`pg_basebackup` 基础备份，记录 WAL LSN；本地连续归档延迟检查门限 5 分钟。
- MinIO：启用版本控制，导出版本化对象清单并校验 `mc stat`。
- Nacos：导出命名空间和配置；RabbitMQ：导出 definitions JSON。
- Manifest 记录组件版本、起止时间、校验和、Git SHA、恢复前置条件，不保存密码。

- [ ] **Step 4：运行本地备份**

```powershell
$id = 'test-p10-backup-' + (Get-Date -Format 'yyyyMMddHHmmss')
pwsh -NoProfile -File deploy/p10/recovery/backup/Backup-MySql.ps1 -Environment local -BackupId $id -Destination 'D:\Java\code\AliAgent\AliAgentData\backups'
pwsh -NoProfile -File deploy/p10/recovery/backup/Backup-Postgres.ps1 -Environment local -BackupId $id -Destination 'D:\Java\code\AliAgent\AliAgentData\backups'
# 继续执行 MinIO/Nacos/RabbitMQ 和 Manifest
```

Expected：清单校验通过；连续归档延迟小于 5 分钟。

- [ ] **Step 5：提交**

```powershell
git add deploy/p10/recovery
git commit -m "feat(p10-c): add verified platform backups"
```

## 7. Task 6：隔离恢复、删除清单和 Outbox 重投

**Files:**
- Create: `deploy/p10/recovery/restore/New-RestoreEnvironment.ps1`
- Create: `deploy/p10/recovery/restore/Restore-MySqlToPoint.ps1`
- Create: `deploy/p10/recovery/restore/Restore-PostgresToPoint.ps1`
- Create: `deploy/p10/recovery/restore/Restore-MinIo.ps1`
- Create: `deploy/p10/recovery/restore/Import-Nacos.ps1`
- Create: `deploy/p10/recovery/restore/Import-RabbitDefinitions.ps1`
- Create: `deploy/p10/recovery/restore/Apply-DeletionManifest.ps1`
- Create: `deploy/p10/recovery/restore/Replay-Outbox.ps1`
- Create: `deploy/p10/recovery/restore/Invoke-RecoveryDrill.ps1`
- Create: `deploy/p10/recovery/restore/Remove-RestoreEnvironment.ps1`
- Create: `deploy/p10/recovery/tests/RestoreScripts.Tests.ps1`

- [ ] **Step 1：写破坏性保护失败测试**

测试要求：

```powershell
foreach ($file in $restoreScripts) {
  $text = Get-Content $file -Raw
  if ($text -notmatch 'test-restore-') { throw "$file 未限制恢复前缀" }
  if ($text -notmatch 'ConfirmTarget') { throw "$file 缺少显式目标确认" }
}
```

`Replay-Outbox.ps1` 必须要求租户、事件类型、时间范围、最大条数、DryRun 和 Step-up Token；默认 `-DryRun`。

- [ ] **Step 2：运行并确认失败**

Expected：FAIL，恢复脚本尚不存在。

- [ ] **Step 3：实现恢复编排**

`Invoke-RecoveryDrill.ps1` 固定步骤：

```text
创建 test-restore- 隔离 Compose
→ 恢复到目标时间点
→ 恢复 MinIO 版本
→ 导入 Nacos/RabbitMQ
→ 应用 deletion manifest
→ DryRun Outbox → Step-up 后执行重投
→ 一致性 SQL/对象检查
→ 核心业务 smoke
→ 写 RPO/RTO 报告
→ 显式清理隔离环境
```

RPO 计算使用故障模拟 UTC 时间与恢复后最后一致事实时间；RTO 从演练开始到核心 smoke 全部通过。

- [ ] **Step 4：执行完整演练**

```powershell
pwsh -NoProfile -File deploy/p10/recovery/restore/Invoke-RecoveryDrill.ps1 `
  -BackupId $id `
  -TargetTimeUtc (Get-Date).ToUniversalTime().AddMinutes(-5) `
  -ConfirmTarget test-restore-p10
```

Expected：输出 `RPO_MINUTES <= 15`、`RTO_MINUTES <= 120`；订单、会话、知识、售后 Saga、Outbox/Inbox 和对象引用一致。

- [ ] **Step 5：提交**

```powershell
git add deploy/p10/recovery
git commit -m "feat(p10-c): add isolated recovery and outbox replay"
```

## 8. Task 7：文档与最终验证

**Files:**
- Create: `docs/runbooks/p10/security-key-rotation.md`
- Create: `docs/runbooks/p10/security-file-intake.md`
- Create: `docs/runbooks/p10/recovery-backup.md`
- Create: `docs/runbooks/p10/recovery-drill.md`

- [ ] **Step 1：运行完整测试**

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl platform/platform-service-security,services/knowledge-service -am test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -f mall/pom.xml -pl mall-portal -am test -DskipTests=false -Ddocker.skip=true
pwsh -NoProfile -File deploy/p10/security/tests/SecurityScripts.Tests.ps1
pwsh -NoProfile -File deploy/p10/recovery/tests/BackupScripts.Tests.ps1
pwsh -NoProfile -File deploy/p10/recovery/tests/RestoreScripts.Tests.ps1
git diff --check
```

- [ ] **Step 2：验证测试数据清理**

确认 PostgreSQL/MySQL/MinIO/Redis 中无 `test-p10-` 和 `test-restore-` 残留；只保留脱敏演练报告和备份 Manifest。

- [ ] **Step 3：提交并报告**

```powershell
git add docs/runbooks/p10 platform services/knowledge-service mall/mall-portal deploy/p10/security deploy/p10/recovery tools/p10-security
git commit -m "docs(p10-c): document security and recovery operations"
git status --short
```

Expected：工作区为空。报告最终 SHA、实测 RPO/RTO、密钥轮换、恶意文件和跨租户验证证据。
