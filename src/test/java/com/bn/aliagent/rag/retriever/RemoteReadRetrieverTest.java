package com.bn.aliagent.rag.retriever;

import com.bn.aliagent.rag.model.RagChunk;
import com.bn.aliagent.rag.model.RetrievalContext;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteReadRetrieverTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void featureDisabledUsesLocalRetrieverWithoutRemoteCall() {
        RecordingClient remote = new RecordingClient(RemoteKnowledgeResult.success(List.of(remoteChunk())));
        RemoteReadRetriever retriever = retriever(false, false, remote, new RecordingObservation());

        List<RagChunk> result = retriever.retrieve("查询", RetrievalContext.defaults());

        assertEquals(List.of("local-chunk"), ids(result));
        assertEquals(0, remote.calls);
    }

    @Test
    void enabledTenantUsesRemoteResultAndForwardsOnlyTrustedHeaders() {
        RecordingClient remote = new RecordingClient(RemoteKnowledgeResult.success(List.of(remoteChunk())));
        RemoteReadRetriever retriever = retriever(true, false, remote, new RecordingObservation());
        bindTrustedRequest("tenant-a");

        List<RagChunk> result = retriever.retrieve("查询", RetrievalContext.defaults());

        assertEquals(List.of("remote-chunk"), ids(result));
        assertEquals("tenant-a", remote.context.tenantId());
        assertEquals("Bearer service-jwt", remote.context.serviceAuthorization());
        assertEquals("version-1", result.get(0).getMetadata().get("versionId"));
    }

    @Test
    void nonWhitelistedTenantUsesLocalRetriever() {
        RecordingClient remote = new RecordingClient(RemoteKnowledgeResult.success(List.of(remoteChunk())));
        RemoteReadRetriever retriever = retriever(true, false, remote, new RecordingObservation());
        bindTrustedRequest("tenant-b");

        List<RagChunk> result = retriever.retrieve("查询", RetrievalContext.defaults());

        assertEquals(List.of("local-chunk"), ids(result));
        assertEquals(0, remote.calls);
    }

    @Test
    void protocolFailureFallsBackToLocal() {
        RecordingClient remote = new RecordingClient(RemoteKnowledgeResult.failure(RemoteFailure.PROTOCOL_ERROR));
        RecordingObservation observation = new RecordingObservation();
        RemoteReadRetriever retriever = retriever(true, false, remote, observation);
        bindTrustedRequest("tenant-a");

        List<RagChunk> result = retriever.retrieve("查询", RetrievalContext.defaults());

        assertEquals(List.of("local-chunk"), ids(result));
        assertEquals(RemoteFailure.PROTOCOL_ERROR, observation.failure);
    }

    @Test
    void timeoutFallsBackToLocalAndRecordsReason() {
        RecordingClient remote = new RecordingClient(RemoteKnowledgeResult.failure(RemoteFailure.TIMEOUT));
        RecordingObservation observation = new RecordingObservation();
        RemoteReadRetriever retriever = retriever(true, false, remote, observation);
        bindTrustedRequest("tenant-a");

        List<RagChunk> result = retriever.retrieve("查询", RetrievalContext.defaults());

        assertEquals(List.of("local-chunk"), ids(result));
        assertEquals(RemoteFailure.TIMEOUT, observation.failure);
    }

    @Test
    void noGroundingFallsBackToLocalWithoutInventingCitation() {
        RecordingClient remote = new RecordingClient(RemoteKnowledgeResult.noGrounding());
        RecordingObservation observation = new RecordingObservation();
        RemoteReadRetriever retriever = retriever(true, false, remote, observation);
        bindTrustedRequest("tenant-a");

        List<RagChunk> result = retriever.retrieve("查询", RetrievalContext.defaults());

        assertEquals(List.of("local-chunk"), ids(result));
        assertEquals(RemoteFailure.NO_GROUNDING, observation.failure);
        assertTrue(result.stream().noneMatch(chunk -> "remote-chunk".equals(chunk.getChunkId())));
    }

    @Test
    void dualRunKeepsLocalResultVisibleAndRecordsComparison() {
        RecordingClient remote = new RecordingClient(RemoteKnowledgeResult.success(List.of(remoteChunk())));
        RecordingObservation observation = new RecordingObservation();
        RemoteReadRetriever retriever = retriever(true, true, remote, observation);
        bindTrustedRequest("tenant-a");

        List<RagChunk> result = retriever.retrieve("查询", RetrievalContext.defaults());

        assertEquals(List.of("local-chunk"), ids(result));
        assertEquals(List.of("local-chunk"), observation.localIds);
        assertEquals(List.of("remote-chunk"), observation.remoteIds);
    }

    private RemoteReadRetriever retriever(boolean enabled, boolean dualRun, RecordingClient remote,
            RecordingObservation observation) {
        RemoteKnowledgeReadProperties properties = new RemoteKnowledgeReadProperties(enabled, List.of("tenant-a"), dualRun,
                "http://knowledge-service", Duration.ofSeconds(1));
        Retriever local = (query, context) -> List.of(RagChunk.builder().chunkId("local-chunk").documentId("local-doc")
                .content("本地知识").score(0.9f).build());
        return new RemoteReadRetriever(local, properties, remote, new TrustedKnowledgeContextResolver(), observation,
                Runnable::run);
    }

    private void bindTrustedRequest(String tenantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", tenantId);
        request.addHeader("X-Subject-Id", "subject-1");
        request.addHeader("X-Subject-Type", "MEMBER");
        request.addHeader("X-User-Roles", "USER");
        request.addHeader("X-User-Permissions", "KNOWLEDGE_READ");
        request.addHeader("X-Authorization-Snapshot-Id", "snapshot-1");
        request.addHeader("X-Trace-Id", "trace-1");
        request.addHeader("X-Service-Authorization", "Bearer service-jwt");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static RemoteKnowledgeItem remoteChunk() {
        return new RemoteKnowledgeItem("remote-doc", "version-1", "remote-chunk", "远程知识", 0.8f);
    }

    private static List<String> ids(List<RagChunk> chunks) {
        return chunks.stream().map(RagChunk::getChunkId).toList();
    }

    private static final class RecordingClient implements RemoteKnowledgeClient {
        private final RemoteKnowledgeResult result;
        private int calls;
        private TrustedKnowledgeContext context;

        private RecordingClient(RemoteKnowledgeResult result) {
            this.result = result;
        }

        @Override
        public RemoteKnowledgeResult retrieve(String query, int topK, TrustedKnowledgeContext trustedContext) {
            calls++;
            context = trustedContext;
            return result;
        }
    }

    private static final class RecordingObservation implements RemoteReadObservation {
        private RemoteFailure failure;
        private List<String> localIds = List.of();
        private List<String> remoteIds = List.of();

        @Override
        public void compared(List<RagChunk> local, List<RagChunk> remote, long latencyMillis) {
            localIds = ids(local);
            remoteIds = ids(remote);
        }

        @Override
        public void fallback(RemoteFailure reason, long latencyMillis) {
            failure = reason;
        }
    }
}
