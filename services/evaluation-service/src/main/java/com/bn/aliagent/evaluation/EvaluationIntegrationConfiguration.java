package com.bn.aliagent.evaluation;

import com.bn.aliagent.evaluation.anonymization.DeterministicAnonymizer;
import com.bn.aliagent.evaluation.intake.CandidateSink;
import com.bn.aliagent.evaluation.intake.EventInbox;
import com.bn.aliagent.evaluation.intake.EventIntakeService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class EvaluationIntegrationConfiguration {
    @Bean EventInbox evaluationEventInbox() { return new EventInbox.InMemory(); }
    @Bean CandidateSink evaluationCandidateSink() { return candidate -> { }; }
    @Bean EventIntakeService eventIntakeService(EventInbox inbox, CandidateSink sink,
            @Value("${evaluation.anonymization.key}") String anonymizationKey) {
        return new EventIntakeService(inbox, (payload, tenantId) -> {
            var result = new DeterministicAnonymizer(anonymizationKey).anonymize(payload, tenantId);
            return new com.bn.aliagent.evaluation.intake.AnonymizedPayload(
                    result.status() == com.bn.aliagent.evaluation.anonymization.AnonymizationStatus.SAFE
                            ? com.bn.aliagent.evaluation.intake.IntakeAnonymizationStatus.SAFE
                            : com.bn.aliagent.evaluation.intake.IntakeAnonymizationStatus.QUARANTINED,
                    result.canonicalJson(), result.ruleVersion(), result.digest());
        }, sink, Clock.systemUTC());
    }
}
