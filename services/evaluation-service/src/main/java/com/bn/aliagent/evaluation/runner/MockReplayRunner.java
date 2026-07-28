package com.bn.aliagent.evaluation.runner;

import com.bn.aliagent.evaluation.replay.EvaluationManifest;
import com.bn.aliagent.evaluation.replay.ReplayFixture;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** 仅由固定清单和样本推导结果，绝不调用网络、模型或生产工具。 */
public final class MockReplayRunner implements OrchestrationReplayPort {
    private int externalCalls;

    @Override
    public ReplayResult replay(String tenantId, EvaluationManifest manifest, ReplayFixture fixture) { return run(tenantId, manifest, fixture); }

    public ReplayResult run(String tenantId, EvaluationManifest manifest, ReplayFixture fixture) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("租户不能为空");
        String intent = fixture.expectsHumanHandoff() ? "HUMAN_HANDOFF" : fixture.expectsToolFailure() ? "TOOL_FAILURE" : fixture.expectedIntent();
        List<String> tools = fixture.requiresOrderTool() ? List.of("mall.order.read") : List.of();
        List<String> citations = fixture.requiresRag() ? List.of("mock://" + manifest.knowledgeVersionId()) : List.of();
        String digest = digest(manifest.digest() + "|" + fixture.sampleId() + "|" + fixture.input() + "|" + intent);
        return new ReplayResult(intent, "mock:" + intent, tools, citations, fixture.expectsHumanHandoff(), fixture.expectsToolFailure(),
                1, 1, 0, 0, BigDecimal.ZERO, digest);
    }

    public int externalCalls() { return externalCalls; }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte aByte : bytes) result.append(String.format("%02x", aByte));
            return result.toString();
        } catch (Exception exception) { throw new IllegalStateException("无法生成回放摘要", exception); }
    }
}
