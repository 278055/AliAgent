package com.bn.aliagent.evaluation.replay;

import java.util.List;
import java.util.UUID;

/** B 自有的已发布评测样本快照，不依赖候选或数据集实现类型。 */
public record ReplayFixture(UUID sampleId, String label, String input, String expectedIntent,
                            List<String> allowedTools, List<String> requiredCitations,
                            boolean requiresRag, boolean requiresOrderTool, boolean expectsToolFailure,
                            boolean expectsHumanHandoff, boolean openEnded) {
    public ReplayFixture {
        if (sampleId == null || blank(input) || blank(expectedIntent)) throw new IllegalArgumentException("回放样本不完整");
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        requiredCitations = requiredCitations == null ? List.of() : List.copyOf(requiredCitations);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
