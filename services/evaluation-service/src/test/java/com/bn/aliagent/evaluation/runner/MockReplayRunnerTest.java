package com.bn.aliagent.evaluation.runner;

import com.bn.aliagent.evaluation.replay.EvaluationManifest;
import com.bn.aliagent.evaluation.replay.ReplayFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockReplayRunnerTest {
    private static final String TENANT = "test-tenant";

    @Test
    void 拒绝缺少版本字段的清单() {
        assertThrows(IllegalArgumentException.class, () -> new EvaluationManifest(
                null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "tools-v1", "rules-v1",
                UUID.randomUUID(), "scoring-v1", "judge-v1"));
    }

    @Test
    void 相同清单和样本得到稳定摘要且不调用外部系统() {
        var manifest = manifest();
        var fixture = new ReplayFixture(UUID.randomUUID(), "普通问答", "你好", "GENERAL", List.of(), List.of(),
                false, false, false, false, false);
        var runner = new MockReplayRunner();

        var first = runner.run(TENANT, manifest, fixture);
        var second = runner.run(TENANT, manifest, fixture);

        assertEquals(first.digest(), second.digest());
        assertEquals(0, runner.externalCalls());
        assertEquals("GENERAL", first.intent());
    }

    @Test
    void 能确定性回放知识订单工具故障和转人工样本() {
        var runner = new MockReplayRunner();
        var manifest = manifest();

        assertEquals("RAG", runner.run(TENANT, manifest, fixture("RAG", true, false, false, false)).intent());
        assertEquals("ORDER_QUERY", runner.run(TENANT, manifest, fixture("ORDER_QUERY", false, true, false, false)).intent());
        assertEquals("TOOL_FAILURE", runner.run(TENANT, manifest, fixture("TOOL_FAILURE", false, false, true, false)).intent());
        assertEquals("HUMAN_HANDOFF", runner.run(TENANT, manifest, fixture("HUMAN_HANDOFF", false, false, false, true)).intent());
    }

    @Test
    void evaluationDatasetReadRejectsForeignTenant() {
        var manifest = manifest();
        var fixture = fixture("GENERAL", false, false, false, false);
        var datasets = (DatasetSnapshotPort) (tenantId, datasetVersionId) -> {
            if (!TENANT.equals(tenantId)) throw new SecurityException("跨租户读取被拒绝");
            return List.of(fixture);
        };
        var service = new EvaluationRunService(datasets,
                (tenantId, evaluationManifest, replayFixture) -> new MockReplayRunner().run(tenantId, evaluationManifest, replayFixture));

        assertThrows(SecurityException.class, () -> service.run("test-tenant-b", manifest));
    }

    private static ReplayFixture fixture(String intent, boolean rag, boolean order, boolean failure, boolean handoff) {
        return new ReplayFixture(UUID.randomUUID(), intent, "test input", intent, List.of(), List.of(), rag, order, failure, handoff, false);
    }

    private static EvaluationManifest manifest() {
        return new EvaluationManifest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "tools-v1", "rules-v1", UUID.randomUUID(), "scoring-v1", "judge-v1");
    }
}
