package com.bn.aliagent.knowledge.retrieval;

import com.bn.aliagent.knowledge.api.TrustedKnowledgeRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database")
public class RetrievalController {
    private final RetrievalService retrievalService;

    public RetrievalController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping("/api/v1/knowledge/retrieval:query")
    public Map<String, Object> retrieve(HttpServletRequest request, @RequestBody RetrievalQuery requestBody) {
        RetrievalResponse response = retrievalService.retrieve(requestBody.query(), TrustedKnowledgeRequestContext.require(request), requestBody.resolvedTopK());
        List<Map<String, Object>> items = response.items().stream().map(item -> Map.<String, Object>of("documentId", item.documentId(),
                "versionId", item.versionId(), "chunkId", item.chunkId(), "content", item.content(), "score", item.score(),
                "citation", Map.of("documentId", item.documentId(), "versionId", item.versionId(), "chunkId", item.chunkId()))).toList();
        return Map.of("code", 200, "message", response.message(), "data", Map.of("items", items));
    }

    public record RetrievalQuery(String query, Integer topK) {
        public int resolvedTopK() { return topK == null ? 10 : topK; }
    }
}
