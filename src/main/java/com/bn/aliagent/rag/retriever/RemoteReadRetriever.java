package com.bn.aliagent.rag.retriever;

import com.bn.aliagent.rag.model.RagChunk;
import com.bn.aliagent.rag.model.RetrievalContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/** 以本地 RAG 为安全回退的远程知识读取路由器。 */
public class RemoteReadRetriever implements Retriever {
    private final Retriever localRetriever;
    private final RemoteKnowledgeReadProperties properties;
    private final RemoteKnowledgeClient remoteClient;
    private final TrustedKnowledgeContextResolver contextResolver;
    private final RemoteReadObservation observation;
    private final Executor comparisonExecutor;

    public RemoteReadRetriever(Retriever localRetriever, RemoteKnowledgeReadProperties properties,
            RemoteKnowledgeClient remoteClient, TrustedKnowledgeContextResolver contextResolver,
            RemoteReadObservation observation, Executor comparisonExecutor) {
        this.localRetriever = localRetriever;
        this.properties = properties;
        this.remoteClient = remoteClient;
        this.contextResolver = contextResolver;
        this.observation = observation;
        this.comparisonExecutor = comparisonExecutor;
    }

    @Override
    public List<RagChunk> retrieve(String query, RetrievalContext context) {
        Optional<TrustedKnowledgeContext> trustedContext = contextResolver.resolve();
        if (!properties.enabled()) {
            return localRetriever.retrieve(query, context);
        }
        if (trustedContext.isEmpty() || !properties.isTenantEnabled(trustedContext.get().tenantId())) {
            if (trustedContext.isEmpty()) {
                observation.fallback(RemoteFailure.MISSING_TRUSTED_CONTEXT, 0L);
            }
            return localRetriever.retrieve(query, context);
        }
        if (properties.dualRun()) {
            List<RagChunk> local = localRetriever.retrieve(query, context);
            comparisonExecutor.execute(() -> compare(local, query, context, trustedContext.get()));
            return local;
        }
        List<RagChunk> local = localRetriever.retrieve(query, context);
        long startedAt = System.nanoTime();
        RemoteKnowledgeResult remote = remoteClient.retrieve(query, context.getTopK(), trustedContext.get());
        long latencyMillis = elapsedMillis(startedAt);
        if (!remote.isSuccess()) {
            observation.fallback(remote.failure(), latencyMillis);
            return local;
        }
        List<RagChunk> remoteChunks = toRagChunks(remote.items());
        observation.compared(local, remoteChunks, latencyMillis);
        return remoteChunks;
    }

    private void compare(List<RagChunk> local, String query, RetrievalContext context, TrustedKnowledgeContext trustedContext) {
        long startedAt = System.nanoTime();
        RemoteKnowledgeResult remote = remoteClient.retrieve(query, context.getTopK(), trustedContext);
        long latencyMillis = elapsedMillis(startedAt);
        if (remote.isSuccess()) {
            observation.compared(local, toRagChunks(remote.items()), latencyMillis);
        } else {
            observation.fallback(remote.failure(), latencyMillis);
        }
    }

    private List<RagChunk> toRagChunks(List<RemoteKnowledgeItem> items) {
        return items.stream().map(item -> RagChunk.builder().chunkId(item.chunkId()).documentId(item.documentId())
                .content(item.content()).score(item.score()).metadata(Map.of("versionId", item.versionId(), "citation",
                        Map.of("documentId", item.documentId(), "versionId", item.versionId(), "chunkId", item.chunkId())))
                .build()).toList();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
