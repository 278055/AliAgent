package com.bn.aliagent.evaluation.scoring;

import com.bn.aliagent.evaluation.judge.JudgeScorer;
import com.bn.aliagent.evaluation.replay.ReplayFixture;
import com.bn.aliagent.evaluation.runner.ReplayResult;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DeterministicScorerTest {
    @Test void 对意图工具引用安全转人工时延和成本产生证据() {
        var fixture = new ReplayFixture(UUID.randomUUID(), "risk", "input", "RAG", List.of("search"), List.of("kb://1"), true, false, false, false, false);
        var result = new ReplayResult("RAG", "answer", List.of("search"), List.of("kb://1"), false, false, 10, 20, 2, 3, new BigDecimal("0.01"), "x");
        var evidence = new DeterministicScorer().score(fixture, result);
        assertEquals(MetricStatus.PASS, evidence.stream().filter(e -> e.metric().equals("intent")).findFirst().orElseThrow().status());
        assertTrue(evidence.stream().anyMatch(e -> e.metric().equals("ragCitation") && e.status() == MetricStatus.PASS));
        assertTrue(evidence.stream().anyMatch(e -> e.metric().equals("cost")));
    }
    @Test void Judge不能覆盖安全红线且不可用不乐观通过() {
        var evidence = List.of(new MetricEvidence("safety", MetricStatus.FAIL, "unsafe", "safe", "rule", true));
        assertEquals(MetricStatus.FAIL, new JudgeScorer(() -> MetricStatus.PASS).score(evidence, true));
        assertEquals(MetricStatus.UNAVAILABLE, new JudgeScorer(() -> { throw new IllegalStateException(); }).score(List.of(), true));
    }
}
