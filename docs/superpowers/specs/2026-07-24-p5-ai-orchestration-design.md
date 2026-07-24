# P5 AI Orchestration Runtime Design

## Scope

Complete the minimal runtime connections among the merged P5 core, adapter, and governance modules. Do not modify public contracts, enable write tools, or refactor existing domain models.

## Runtime Wiring

Add `OrchestrationRuntimeConfiguration` to register the v2 consumer, event mapper, execution store, rule-first router, orchestration service, and workflow runner. The database profile uses the JDBC store. The default profile uses the in-memory store for offline Mock verification. Application startup resumes incomplete executions.

## Read-only Workflows

- GENERAL: invoke only `ChatModelPort`, then write a terminal stream chunk with `replyMessageId`, `generationId`, and `requestId`.
- RAG: invoke only `KnowledgeRetrievalPort`; hand off safely when no citation is available; include citations in the terminal stream chunk when retrieval succeeds.
- ORDER_QUERY: invoke only `MallReadToolPort.readOrder`; never ask a model to generate order facts.
- LOGISTICS_QUERY: invoke only `MallReadToolPort.readLogistics`; never ask a model to generate logistics facts.
- HUMAN_HANDOFF: invoke neither model nor tools; write a fixed handoff message.

Extract a contiguous order number from input. Missing or invalid identifiers cause safe handoff. Existing rules route refund, cancellation, after-sales, and approval intents to HUMAN_HANDOFF.

## Security And Failure Boundaries

The execution context comes only from trusted event fields. Existing adapters propagate the service JWT, authorization snapshot, tenant, and request headers. Adapter failures for model, knowledge, or mall calls never invent policy or business facts; they write the safe handoff response. DashScope is created only when `provider=dashscope` and its API key is nonblank; all other configurations use Mock.

## Verification

Use tests first for Spring Bean wiring, five workflows, duplicate event/request handling, RAG citations, mall-tool isolation, write-intent rejection, stream identifiers, safe handoff on failures, and startup recovery. Then run module and full Maven tests, contract validation, Compose configuration validation, empty-database Flyway migration, and feasible offline Mock multi-service end-to-end tests.
