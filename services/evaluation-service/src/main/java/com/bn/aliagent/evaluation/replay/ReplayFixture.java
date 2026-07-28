package com.bn.aliagent.evaluation.replay;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 已发布的不可变回放样本。 */
public record ReplayFixture(UUID sampleId, String label, String input, String expectedIntent,
                            List<String> allowedTools, List<String> requiredCitations,
                            boolean requiresRag, boolean requiresOrderTool, boolean expectsToolFailure,
                            boolean expectsHumanHandoff, boolean openEnded,
                            List<String> prohibitedTools, Map<String, Object> parameterConstraints,
                            List<String> factAssertions, List<String> safetyLabels,
                            Map<String, Double> weights, List<String> applicableMetrics) {
    public ReplayFixture {
        if (sampleId == null || blank(input) || blank(expectedIntent)) {
            throw new IllegalArgumentException("回放样本不完整");
        }
        allowedTools = copy(allowedTools);
        requiredCitations = copy(requiredCitations);
        prohibitedTools = copy(prohibitedTools);
        parameterConstraints = parameterConstraints == null ? Map.of() : Map.copyOf(parameterConstraints);
        factAssertions = copy(factAssertions);
        safetyLabels = copy(safetyLabels);
        weights = weights == null ? Map.of() : Map.copyOf(weights);
        applicableMetrics = copy(applicableMetrics);
    }

    public ReplayFixture(UUID sampleId, String label, String input, String expectedIntent,
                         List<String> allowedTools, List<String> requiredCitations,
                         boolean requiresRag, boolean requiresOrderTool, boolean expectsToolFailure,
                         boolean expectsHumanHandoff, boolean openEnded) {
        this(sampleId, label, input, expectedIntent, allowedTools, requiredCitations, requiresRag, requiresOrderTool,
                expectsToolFailure, expectsHumanHandoff, openEnded, List.of(), Map.of(), List.of(), List.of(), Map.of(), List.of());
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
