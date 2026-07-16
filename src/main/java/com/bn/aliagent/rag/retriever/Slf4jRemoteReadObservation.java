package com.bn.aliagent.rag.retriever;

import com.bn.aliagent.rag.model.RagChunk;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 输出可审计的脱敏双跑指标。 */
public class Slf4jRemoteReadObservation implements RemoteReadObservation {
    private static final Logger log = LoggerFactory.getLogger(Slf4jRemoteReadObservation.class);

    @Override
    public void compared(List<RagChunk> local, List<RagChunk> remote, long latencyMillis) {
        List<String> localIds = chunkIds(local);
        List<String> remoteIds = chunkIds(remote);
        List<String> remoteVersionIds = remote.stream().map(chunk -> String.valueOf(chunk.getMetadata().get("versionId")))
                .distinct().toList();
        log.info("knowledge_remote_compare localChunkIds={} remoteChunkIds={} remoteVersionIds={} latencyMs={} overlap={}",
                new Object[] {localIds, remoteIds, remoteVersionIds, latencyMillis, overlap(localIds, remoteIds)});
    }

    @Override
    public void fallback(RemoteFailure reason, long latencyMillis) {
        log.warn("knowledge_remote_fallback reason={} latencyMs={}", reason, latencyMillis);
    }

    private List<String> chunkIds(List<RagChunk> chunks) {
        return chunks.stream().map(RagChunk::getChunkId).collect(Collectors.toList());
    }

    private double overlap(List<String> localIds, List<String> remoteIds) {
        Set<String> union = new HashSet<>(localIds);
        union.addAll(remoteIds);
        if (union.isEmpty()) {
            return 1.0d;
        }
        Set<String> intersection = new HashSet<>(localIds);
        intersection.retainAll(remoteIds);
        return (double) intersection.size() / union.size();
    }
}
