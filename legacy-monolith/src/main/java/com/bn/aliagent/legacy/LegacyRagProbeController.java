package com.bn.aliagent.legacy;

import com.bn.aliagent.rag.model.RagChunk;
import com.bn.aliagent.rag.model.RetrievalContext;
import com.bn.aliagent.rag.retriever.Retriever;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/p3-probe/rag")
public class LegacyRagProbeController {
    private final Retriever retriever;

    public LegacyRagProbeController(Retriever retriever) {
        this.retriever = retriever;
    }

    @GetMapping("/query")
    public Map<String, Object> query(@RequestParam("q") String q) {
        List<Map<String, Object>> items = retriever.retrieve(q, RetrievalContext.defaults()).stream()
                .map(chunk -> Map.<String, Object>of("chunkId", chunk.getChunkId(), "documentId", chunk.getDocumentId(),
                        "content", chunk.getContent(), "score", chunk.getScore())).toList();
        return Map.of("items", items);
    }
}
