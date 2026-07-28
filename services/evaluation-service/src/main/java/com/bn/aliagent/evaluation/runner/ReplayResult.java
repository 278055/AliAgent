package com.bn.aliagent.evaluation.runner;

import java.util.List;

public record ReplayResult(String intent, String answer, List<String> tools, List<String> citations,
                           boolean humanHandoff, boolean toolFailure, long firstTokenMillis,
                           long totalLatencyMillis, long inputTokens, long outputTokens, java.math.BigDecimal cost,
                           String digest) {
    public ReplayResult {
        tools = tools == null ? List.of() : List.copyOf(tools);
        citations = citations == null ? List.of() : List.copyOf(citations);
        cost = cost == null ? java.math.BigDecimal.ZERO : cost;
    }
}
