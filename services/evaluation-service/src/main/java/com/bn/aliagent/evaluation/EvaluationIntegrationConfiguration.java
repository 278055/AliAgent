package com.bn.aliagent.evaluation;

import com.bn.aliagent.evaluation.anonymization.DeterministicAnonymizer;
import com.bn.aliagent.evaluation.intake.CandidateSink;
import com.bn.aliagent.evaluation.intake.EventInbox;
import com.bn.aliagent.evaluation.intake.EventIntakeService;
import com.bn.aliagent.evaluation.candidate.CandidateRepository;
import com.bn.aliagent.evaluation.candidate.CandidateReviewService;
import com.bn.aliagent.evaluation.dataset.EvaluationDatasetService;
import com.bn.aliagent.evaluation.anonymization.PublicDatasetAnonymizer;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.bn.aliagent.evaluation.runner.EvaluationRunRepository;

@Configuration
class EvaluationIntegrationConfiguration {
    @Bean @Profile("database") EvaluationRunRepository jdbcEvaluationRunRepository(JdbcTemplate jdbc) { return new com.bn.aliagent.evaluation.persistence.JdbcEvaluationRunRepository(jdbc); }
    @Bean @Profile("!database") EventInbox evaluationEventInbox() { return new EventInbox.InMemory(); }
    @Bean @Profile("database") EventInbox jdbcEvaluationEventInbox(JdbcTemplate jdbc) { return new com.bn.aliagent.evaluation.persistence.JdbcEventInbox(jdbc); }
    @Bean @Profile("!database") CandidateSink evaluationCandidateSink() { return candidate -> { }; }
    @Bean @Profile("database") CandidateSink jdbcEvaluationCandidateSink(JdbcTemplate jdbc) { return new com.bn.aliagent.evaluation.persistence.JdbcCandidateSink(jdbc); }
    @Bean @Profile("!database") CandidateRepository evaluationCandidateRepository() { return new CandidateRepository.InMemory(); }
    @Bean @Profile("database") CandidateRepository jdbcEvaluationCandidateRepository(JdbcTemplate jdbc,
            @Value("${evaluation.anonymization.key}") String anonymizationKey) { return new com.bn.aliagent.evaluation.persistence.JdbcEvaluationDatasetRepository(jdbc, new PublicDatasetAnonymizer(new DeterministicAnonymizer(anonymizationKey))); }
    @Bean CandidateReviewService candidateReviewService(CandidateRepository repository) { return new CandidateReviewService(repository); }
    @Bean EvaluationDatasetService evaluationDatasetService(CandidateRepository repository,
            @Value("${evaluation.anonymization.key}") String anonymizationKey) {
        return new EvaluationDatasetService(repository, new PublicDatasetAnonymizer(new DeterministicAnonymizer(anonymizationKey)));
    }
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
