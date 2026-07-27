package com.bn.aliagent.evaluation.runner;

import com.bn.aliagent.evaluation.replay.EvaluationManifest;
import com.bn.aliagent.evaluation.replay.ReplayFixture;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DashScopeReplayRunnerTest {
    @Test void 未审批或清单不匹配时拒绝执行() {
        var manifest = manifest(); var fixture = fixture();
        var runner = new DashScopeReplayRunner(request -> response());
        assertThrows(IllegalStateException.class, () -> runner.run("test-tenant", manifest, fixture, null));
        var approval = new DashScopeApproval("test-tenant", manifest.digest(), Instant.now().plusSeconds(60), false, new DashScopeLimits(1, 10, 10, BigDecimal.ONE));
        assertThrows(IllegalStateException.class, () -> runner.run("other", manifest, fixture, approval));
    }
    @Test void 预留实际计费且超过预算停止() {
        var manifest = manifest(); var approval = new DashScopeApproval("test-tenant", manifest.digest(), Instant.now().plusSeconds(60), false, new DashScopeLimits(1, 10, 10, new BigDecimal("0.10")));
        var runner = new DashScopeReplayRunner(request -> response());
        assertEquals("GENERAL", runner.run("test-tenant", manifest, fixture(), approval).intent());
        assertThrows(IllegalStateException.class, () -> runner.run("test-tenant", manifest, fixture(), approval));
    }
    private static ModelReplayResponse response() { return new ModelReplayResponse("GENERAL", "answer", List.of(), List.of(), false, false, 2, 5, 2, 3, new BigDecimal("0.05")); }
    private static ReplayFixture fixture() { return new ReplayFixture(UUID.randomUUID(), "test", "hi", "GENERAL", List.of(), List.of(), false, false, false, false, false); }
    private static EvaluationManifest manifest() { return new EvaluationManifest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "tools", "rules", UUID.randomUUID(), "score", "judge"); }
}
