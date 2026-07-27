package com.bn.aliagent.evaluation.scoring;

import com.bn.aliagent.evaluation.replay.ReplayFixture;
import com.bn.aliagent.evaluation.runner.ReplayResult;
import java.util.ArrayList;
import java.util.List;

/** 可复算的规则评分，未配置要求的指标明确标记为不适用。 */
public final class DeterministicScorer {
    public List<MetricEvidence> score(ReplayFixture fixture, ReplayResult result) {
        List<MetricEvidence> evidence = new ArrayList<>();
        evidence.add(equal("intent", result.intent(), fixture.expectedIntent(), false));
        evidence.add(toolEvidence(fixture, result));
        evidence.add(citationEvidence(fixture, result));
        evidence.add(new MetricEvidence("safety", result.toolFailure() ? MetricStatus.FAIL : MetricStatus.PASS,
                Boolean.toString(result.toolFailure()), "no tool failure", "确定性安全规则", true));
        evidence.add(equal("humanHandoff", Boolean.toString(result.humanHandoff()), Boolean.toString(fixture.expectsHumanHandoff()), true));
        evidence.add(new MetricEvidence("firstTokenLatency", MetricStatus.PASS, Long.toString(result.firstTokenMillis()), "recorded", "回放首 token", false));
        evidence.add(new MetricEvidence("totalLatency", MetricStatus.PASS, Long.toString(result.totalLatencyMillis()), "recorded", "回放总耗时", false));
        evidence.add(new MetricEvidence("cost", MetricStatus.PASS, result.cost().toPlainString(), "recorded", "回放成本", false));
        evidence.add(new MetricEvidence("parameters", MetricStatus.NOT_APPLICABLE, "", "", "样本未定义参数约束", false));
        evidence.add(new MetricEvidence("facts", MetricStatus.NOT_APPLICABLE, "", "", "样本未定义事实断言", false));
        evidence.add(new MetricEvidence("adoption", MetricStatus.NOT_APPLICABLE, "", "", "样本未定义客服采纳要求", false));
        return List.copyOf(evidence);
    }
    private MetricEvidence toolEvidence(ReplayFixture fixture, ReplayResult result) {
        if (fixture.allowedTools().isEmpty()) return new MetricEvidence("tool", MetricStatus.NOT_APPLICABLE, result.tools().toString(), "", "样本未定义工具", false);
        return new MetricEvidence("tool", result.tools().equals(fixture.allowedTools()) ? MetricStatus.PASS : MetricStatus.FAIL, result.tools().toString(), fixture.allowedTools().toString(), "允许工具匹配", true);
    }
    private MetricEvidence citationEvidence(ReplayFixture fixture, ReplayResult result) {
        if (!fixture.requiresRag()) return new MetricEvidence("ragCitation", MetricStatus.NOT_APPLICABLE, result.citations().toString(), "", "样本未要求 RAG", false);
        boolean pass = !result.citations().isEmpty() && (fixture.requiredCitations().isEmpty() || result.citations().containsAll(fixture.requiredCitations()));
        return new MetricEvidence("ragCitation", pass ? MetricStatus.PASS : MetricStatus.FAIL, result.citations().toString(), fixture.requiredCitations().toString(), "知识快照引用", true);
    }
    private MetricEvidence equal(String metric, String actual, String expected, boolean redline) {
        return new MetricEvidence(metric, actual.equals(expected) ? MetricStatus.PASS : MetricStatus.FAIL, actual, expected, "确定性比较", redline);
    }
}
