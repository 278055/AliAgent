package com.bn.aliagent.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bn.aliagent.rag.model.RagChunk;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyRagProbeControllerTest {
    @SuppressWarnings("unchecked")
    @Test
    void exposesChunksReturnedByLegacyRetriever() {
        LegacyRagProbeController controller = new LegacyRagProbeController((query, context) -> List.of(
                RagChunk.builder().chunkId("chunk-1").documentId("document-1").content("remote result").score(0.9f).build()));

        Map<String, Object> response = controller.query("policy");

        assertEquals("remote result", ((List<Map<String, Object>>) response.get("items")).get(0).get("content"));
    }
}
