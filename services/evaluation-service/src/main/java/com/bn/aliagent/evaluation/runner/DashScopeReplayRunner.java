package com.bn.aliagent.evaluation.runner;

import com.bn.aliagent.evaluation.replay.EvaluationManifest;
import com.bn.aliagent.evaluation.replay.ReplayFixture;
import java.time.Clock;
import java.util.Objects;

public final class DashScopeReplayRunner {
    private final DashScopeReplayClient client;
    private final DashScopeBudgetLedger ledger;
    private final Clock clock;

    public DashScopeReplayRunner(DashScopeReplayClient client) { this(client, new DashScopeBudgetLedger(), Clock.systemUTC()); }
    DashScopeReplayRunner(DashScopeReplayClient client, DashScopeBudgetLedger ledger, Clock clock) {
        this.client = Objects.requireNonNull(client); this.ledger = Objects.requireNonNull(ledger); this.clock = Objects.requireNonNull(clock);
    }

    public ReplayResult run(String tenantId, EvaluationManifest manifest, ReplayFixture fixture, DashScopeApproval approval) {
        if (approval == null || !approval.permits(tenantId, manifest.digest(), clock.instant())) throw new IllegalStateException("DashScope 回放未获有效审批");
        ledger.reserve(approval.limits());
        ModelReplayResponse response = client.execute(new ModelReplayRequest(tenantId, manifest, fixture));
        ledger.record(response);
        return new ReplayResult(response.intent(), response.answer(), response.tools(), response.citations(), response.humanHandoff(),
                response.toolFailure(), response.firstTokenMillis(), response.totalLatencyMillis(), response.inputTokens(), response.outputTokens(),
                response.cost(), fixture.sampleId() + ":dashscope");
    }
}
