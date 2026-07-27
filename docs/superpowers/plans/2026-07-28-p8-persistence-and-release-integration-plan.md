# P8 Persistence and Release Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the P8 candidate-to-release path durable, tenant-isolated, independently verifiable, and covered by service-level end-to-end tests.

**Architecture:** Add a V3 evaluation migration and package-scoped JDBC repositories behind existing domain ports. Database profile wires only JDBC implementations. An internal service-JWT endpoint verifies persisted signed Gate Proofs; orchestration and knowledge call it before explicit publish/assignment. Gateway remains the only external identity source.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring JDBC, PostgreSQL 17, Flyway, JUnit 5, MockMvc, Spring WebClient/RestClient, Docker Compose.

---

## Files and Ownership

- `services/evaluation-service/src/main/resources/db/migration/V3__p8_persistence_details.sql`: audit, draft composition, run evidence, proof payload, and revocation storage.
- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/persistence/`: JDBC repositories and row mappers.
- `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/internal/`: internal Gate Proof verification API.
- `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/governance/`: authenticated verifier client and failure-closed configuration.
- `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/catalog/`: authenticated verifier client and immutable snapshot persistence.
- `services/gateway-service/src/main/java/com/bn/aliagent/gateway/`: evaluation route audience and role policy.
- `services/*/src/test/java/**`: isolated PostgreSQL schema and MockMvc tests.

### Task 1: Expand the Evaluation Schema

**Files:**
- Create: `services/evaluation-service/src/main/resources/db/migration/V3__p8_persistence_details.sql`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/persistence/EvaluationSchemaV3IntegrationTest.java`

- [ ] **Step 1: Write the failing schema test**

```java
@Test
void migratesV3WithTenantBoundDraftRunAndProofTables() throws Exception {
    String schema = "test_p8_v3_" + UUID.randomUUID().toString().replace("-", "");
    createSchema(schema);
    try {
        Flyway.configure().dataSource(url(schema), "postgres", "123456").schemas(schema)
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        assertEquals(3, jdbc(schema).queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class));
        assertEquals(1, jdbc(schema).queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name='evaluation_dataset_draft_sample' AND column_name='tenant_id'", Integer.class));
        assertEquals(1, jdbc(schema).queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name='evaluation_gate_proof' AND column_name='canonical_payload'", Integer.class));
    } finally { dropSchema(schema); }
}
```

- [ ] **Step 2: Run it to verify RED**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest=EvaluationSchemaV3IntegrationTest test`

Expected: failure because V3 and its tables do not exist.

- [ ] **Step 3: Add V3 DDL**

Create tables `evaluation_dataset_draft_sample`, `evaluation_run_manifest`, `evaluation_metric_evidence`, `evaluation_dashscope_approval`, `evaluation_budget_ledger`, `evaluation_gate_proof`, and `evaluation_audit`. Every table has `tenant_id NOT NULL`; each unique index includes `tenant_id`; `evaluation_gate_proof` stores `proof_id`, `decision_id`, canonical payload, signature, key id, and issued/expiry timestamps.

- [ ] **Step 4: Run it to verify GREEN**

Run the command from Step 2. Expected: 1 test, 0 failures.

- [ ] **Step 5: Commit**

```powershell
git add services/evaluation-service/src/main/resources/db/migration/V3__p8_persistence_details.sql services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/persistence/EvaluationSchemaV3IntegrationTest.java
git commit -m "feat(p8): persist evaluation workflow details"
```

### Task 2: Persist Candidate Review and Immutable Dataset Versions

**Files:**
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/persistence/JdbcEvaluationDatasetRepository.java`
- Modify: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/candidate/CandidateReviewService.java`
- Modify: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/dataset/EvaluationDatasetService.java`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/persistence/JdbcDatasetWorkflowIntegrationTest.java`

- [ ] **Step 1: Write the failing workflow test**

```java
@Test
void acceptsCandidatePublishesImmutableSnapshotAndRejectsForeignTenant() {
    UUID candidate = insertPendingCandidate("test-p8-tenant-a");
    service.review(new CandidateReviewCommand(candidate, "test-p8-tenant-a", "admin", ReviewAction.ACCEPT,
            Map.of("intent", "ORDER_QUERY"), Set.of("ORDER"), "test-p8-review"));
    EvaluationDataset draft = datasets.createDraft("test-p8-tenant-a", "test-p8-dataset");
    datasets.addCandidate("test-p8-tenant-a", draft.id(), candidate);
    PublishedDatasetVersion published = datasets.publish("test-p8-tenant-a", draft.id(), false, null);
    assertThrows(IllegalStateException.class, () -> datasets.addCandidate("test-p8-tenant-a", draft.id(), candidate));
    assertThrows(SecurityException.class, () -> datasets.requirePublished("test-p8-tenant-b", published.id()));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_sample_snapshot", Integer.class));
}
```

- [ ] **Step 2: Run it to verify RED**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest=JdbcDatasetWorkflowIntegrationTest test`

Expected: failure because review/draft/version state is not JDBC-backed.

- [ ] **Step 3: Implement the JDBC repositories and database-profile wiring**

`JdbcEvaluationDatasetRepository` must insert review audit, update only `tenant_id + id`, store draft membership, allocate the next version under a tenant-scoped unique constraint, and copy samples to immutable snapshots. Replace database-profile maps in `CandidateReviewService` and `EvaluationDatasetService` with the repository.

- [ ] **Step 4: Run GREEN and module regression**

Run the Step 2 command, then:

`D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -am test`

Expected: both commands exit 0.

- [ ] **Step 5: Commit**

```powershell
git add services/evaluation-service/src/main/java/com/bn/aliagent/evaluation services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/persistence/JdbcDatasetWorkflowIntegrationTest.java
git commit -m "feat(p8): persist reviewed evaluation datasets"
```

### Task 3: Persist Mock Runs, Evidence, Comparison and Gate Decisions

**Files:**
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/persistence/JdbcEvaluationRunRepository.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/persistence/JdbcGateDecisionRepository.java`
- Modify: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/runner/EvaluationRunService.java`
- Modify: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/gate/GateController.java`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/persistence/JdbcRunAndGateIntegrationTest.java`

- [ ] **Step 1: Write the failing run-to-proof test**

```java
@Test
void storesMockEvidenceAndMakesRevokedProofFailVerification() {
    UUID runId = runs.startMock("test-p8-tenant-a", manifest, datasetVersion);
    runs.storeResult("test-p8-tenant-a", runId, sampleId, result, evidence);
    GateController.IssuedDecision issued = gates.issue("test-p8-tenant-a", target, policy, passingResults, "test-key", 600);
    proofs.store("test-p8-tenant-a", issued);
    assertTrue(proofs.verify("test-p8-tenant-a", issued.proof(), target, policy.version()).accepted());
    proofs.revoke("test-p8-tenant-a", issued.proof().proofId(), "test-p8-revoke");
    assertFalse(proofs.verify("test-p8-tenant-a", issued.proof(), target, policy.version()).accepted());
}
```

- [ ] **Step 2: Run it to verify RED**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest=JdbcRunAndGateIntegrationTest test`

Expected: missing JDBC run/proof storage.

- [ ] **Step 3: Implement storage and verification**

Persist immutable manifest JSON/digest, each result/evidence row, and signed Gate Proof. `JdbcGateDecisionRepository` must fetch only by `tenant_id`, reject unknown/revoked/expired proofs, and pass canonical payload to `GateDecisionVerifier`. `GateController` must store only PASS proofs; FAIL remains an auditable decision but cannot produce a proof.

- [ ] **Step 4: Run GREEN and evaluation regression**

Run the Step 2 command, then module tests. Expected: exit 0.

- [ ] **Step 5: Commit**

```powershell
git add services/evaluation-service/src/main/java/com/bn/aliagent/evaluation services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/persistence/JdbcRunAndGateIntegrationTest.java
git commit -m "feat(p8): persist evaluation results and gate proofs"
```

### Task 4: Add the Internal Gate Verification API

**Files:**
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/internal/GateVerificationController.java`
- Create: `services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/internal/GateVerificationRequest.java`
- Modify: `contracts/openapi/evaluation-v1.yaml`
- Test: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/internal/GateVerificationControllerTest.java`

- [ ] **Step 1: Write the failing service-JWT test**

```java
@Test
void internalVerificationRequiresScopedJwtAndRejectsRevokedProof() throws Exception {
    mockMvc.perform(post("/internal/api/v1/evaluation/gate-proofs:verify").content(validRequest))
            .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/internal/api/v1/evaluation/gate-proofs:verify").header("X-Service-Authorization", bearer)
            .contentType("application/json").content(revokedRequest))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.accepted").value(false));
}
```

- [ ] **Step 2: Run RED**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/evaluation-service -Dtest=GateVerificationControllerTest test`

Expected: controller missing.

- [ ] **Step 3: Implement endpoint**

Require service JWT scope `POST:/internal/api/v1/evaluation/gate-proofs:verify`. The request contains `tenantId`, `artifactType`, `artifactVersionId`, `manifestDigest`, `policyVersion`, and `proof`. Delegate only to persisted proof verification and return `{code:200,message:"",data:{accepted:boolean,reason:string}}`.

- [ ] **Step 4: Run GREEN**

Run the Step 2 command. Expected: 0 failures.

- [ ] **Step 5: Commit**

```powershell
git add contracts/openapi/evaluation-v1.yaml services/evaluation-service/src/main/java/com/bn/aliagent/evaluation/internal services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/internal
git commit -m "feat(p8): expose internal persisted gate verification"
```

### Task 5: Replace P5 and Knowledge Reject-All Ports With Clients

**Files:**
- Create: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/governance/EvaluationGateDecisionClient.java`
- Create: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/catalog/EvaluationKnowledgeGateClient.java`
- Modify: `services/ai-orchestration-service/src/main/java/com/bn/aliagent/orchestration/governance/VersionGovernanceService.java`
- Modify: `services/knowledge-service/src/main/java/com/bn/aliagent/knowledge/catalog/KnowledgeGateConfiguration.java`
- Test: `services/ai-orchestration-service/src/test/java/com/bn/aliagent/orchestration/governance/EvaluationGateDecisionClientTest.java`
- Test: `services/knowledge-service/src/test/java/com/bn/aliagent/knowledge/catalog/EvaluationKnowledgeGateClientTest.java`

- [ ] **Step 1: Write failing failure-closed client tests**

```java
@Test
void rejectsWhenVerifierReturnsFalseOrCannotBeReached() {
    assertThrows(SecurityException.class, () -> client.requirePass("test-p8-tenant", VersionType.MODEL, UUID.randomUUID(), "m", "p", "proof"));
}
```

- [ ] **Step 2: Run RED**

Run each module's targeted client test. Expected: client class missing.

- [ ] **Step 3: Implement clients**

Use the existing service-JWT helper to issue a scoped token, call the evaluation internal endpoint with a bounded timeout, parse only `data.accepted`, and throw `SecurityException` for network exceptions, non-2xx, malformed responses, or `accepted=false`. Wire base URL and secret from configuration.

- [ ] **Step 4: Run GREEN and both module suites**

Run targeted tests, then:

`D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/ai-orchestration-service,services/knowledge-service -am test`

Expected: exit 0.

- [ ] **Step 5: Commit**

```powershell
git add services/ai-orchestration-service services/knowledge-service
git commit -m "feat(p8): verify persisted gates before release"
```

### Task 6: Gateway Evaluation Routing and Roles

**Files:**
- Modify: `services/gateway-service/src/main/java/com/bn/aliagent/gateway/TrustedIdentityGatewayFilter.java`
- Modify: `services/gateway-service/src/main/resources/application.yml`
- Test: `services/gateway-service/src/test/java/com/bn/aliagent/gateway/TrustedIdentityGatewayFilterTest.java`

- [ ] **Step 1: Write a failing role/audience test**

```java
@Test
void evaluationRouteUsesEvaluationAudienceAndRejectsMember() {
    MockServerWebExchange member = exchange("/api/v1/evaluation/candidates", memberToken);
    filter.filter(member, chain).block();
    assertEquals(HttpStatus.FORBIDDEN, member.getResponse().getStatusCode());
    ServiceJwtSupport jwt = new ServiceJwtSupport(serviceSecret);
    jwt.verify(forwardedStaffHeader(), "evaluation-service", "GET:/api/v1/evaluation/candidates");
}
```

- [ ] **Step 2: Run RED**

Run: `D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/gateway-service -Dtest=TrustedIdentityGatewayFilterTest test`

Expected: evaluation route gets the wrong audience or member access is accepted.

- [ ] **Step 3: Implement route policy**

Set audience to `evaluation-service` for `/api/v1/evaluation/**`; require `STAFF` plus `EVALUATION_ADMIN`, `VERSION_ADMIN`, or `DASHSCOPE_APPROVER` according to path. Preserve removal/reinjection of internal headers.

- [ ] **Step 4: Run GREEN and gateway suite**

Run targeted test then `mvn -pl services/gateway-service -am test`. Expected: exit 0.

- [ ] **Step 5: Commit**

```powershell
git add services/gateway-service
git commit -m "feat(p8): secure evaluation gateway access"
```

### Task 7: Full Database and Service E2E Acceptance

**Files:**
- Create: `tools/P8PersistenceE2eAcceptance.java`
- Create: `services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/P8ReleaseFlowIntegrationTest.java`

- [ ] **Step 1: Write the failing release-flow test**

```java
@Test
void eventToAcceptedDatasetMockGateAndExplicitPublishIsAuditable() {
    UUID candidate = intakeTestEvent("test-p8-e2e");
    acceptCandidate(candidate);
    UUID datasetVersion = publishDataset(candidate);
    UUID baselineRun = runMock(datasetVersion, baselineManifest);
    UUID candidateRun = runMock(datasetVersion, candidateManifest);
    GateProof proof = issuePassProof(candidateRun, baselineRun);
    assertThrows(SecurityException.class, () -> publishWith(forgedProof));
    publishWith(proof);
    revoke(proof);
    assertThrows(SecurityException.class, () -> publishWith(proof));
}
```

- [ ] **Step 2: Run RED**

Run the test against an isolated temporary PostgreSQL schema. Expected: failure until all ports are JDBC-wired and verifier clients can communicate.

- [ ] **Step 3: Implement only missing wiring found by the test**

Configure isolated service base URLs and service JWT secrets. Start evaluation-service, orchestration-service, knowledge-service, and Gateway against Compose PostgreSQL/Redis/RabbitMQ; use `test-p8-` tenant, users, Redis keys, and queues. Do not enable DashScope external execution; exercise its rejection paths with a missing approval.

- [ ] **Step 4: Run full verification and record cleanup counts**

Run:

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd test
cd frontend
D:\Java_Tools\nodejs\pnpm.cmd --filter @aliagent/api-client exec tsc --noEmit
D:\Java_Tools\nodejs\pnpm.cmd --filter @aliagent/admin build
```

Run Compose validation with process-scoped test variables. Query test resource counts before cleanup, delete the test schema/database, `test-p8-*` Redis keys, and P8 test queues, then query again. Expected: all zero after cleanup.

- [ ] **Step 5: Commit**

```powershell
git add tools/P8PersistenceE2eAcceptance.java services/evaluation-service/src/test/java/com/bn/aliagent/evaluation/P8ReleaseFlowIntegrationTest.java
git commit -m "test(p8): verify durable release flow end to end"
```

## Plan Self-Review

- V3 schema, JDBC repositories, internal verification, verifier clients, Gateway policy, service E2E and cleanup are each covered by an explicit task.
- The plan uses the same `tenantId`, target fields and `accepted` response contract across Tasks 3-5.
- All production changes begin with a focused failing test and finish with a command that must pass.
