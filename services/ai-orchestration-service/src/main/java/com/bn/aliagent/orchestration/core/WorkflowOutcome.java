package com.bn.aliagent.orchestration.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 工作流向事务边界返回的、非敏感的洞察信号。它不包含提示词、回复、工具结果或查询语句。
 */
public record WorkflowOutcome(List<String> insightEventTypes) {
    public WorkflowOutcome {
        insightEventTypes = List.copyOf(new LinkedHashSet<>(insightEventTypes));
    }

    public static WorkflowOutcome none() {
        return new WorkflowOutcome(List.of());
    }

    public static WorkflowOutcome of(String... eventTypes) {
        return new WorkflowOutcome(List.of(eventTypes));
    }
}
