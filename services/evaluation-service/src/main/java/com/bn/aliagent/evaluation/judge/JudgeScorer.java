package com.bn.aliagent.evaluation.judge;

import com.bn.aliagent.evaluation.scoring.MetricEvidence;
import com.bn.aliagent.evaluation.scoring.MetricStatus;
import java.util.List;

/** Judge 仅为开放回答给出辅助结论，无法覆盖任何确定性红线。 */
public final class JudgeScorer {
    private final JudgePort judge;
    public JudgeScorer(JudgePort judge) { this.judge = judge; }
    public MetricStatus score(List<MetricEvidence> deterministic, boolean openEnded) {
        if (deterministic.stream().anyMatch(value -> value.safetyRedline() && value.status() == MetricStatus.FAIL)) return MetricStatus.FAIL;
        if (!openEnded) return MetricStatus.NOT_APPLICABLE;
        try { return judge.evaluate(); } catch (RuntimeException exception) { return MetricStatus.UNAVAILABLE; }
    }
    @FunctionalInterface public interface JudgePort { MetricStatus evaluate(); }
}
