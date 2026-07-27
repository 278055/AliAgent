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
import com.bn.aliagent.evaluation.runner.DatasetSnapshotPort;
import com.bn.aliagent.evaluation.runner.EvaluationRunService;
import com.bn.aliagent.evaluation.runner.MockReplayRunner;
import com.bn.aliagent.evaluation.gate.GateController;
import com.bn.aliagent.evaluation.gate.GateDecisionSigner;
import com.bn.aliagent.evaluation.gate.GateDecisionVerifier;
import com.bn.aliagent.evaluation.gate.GateEvaluationService;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.util.Map;

@Configuration
class EvaluationIntegrationConfiguration {
    @Bean @Profile("database") EvaluationRunRepository jdbcEvaluationRunRepository(JdbcTemplate jdbc) { return new com.bn.aliagent.evaluation.persistence.JdbcEvaluationRunRepository(jdbc); }
    @Bean @Profile("database") com.bn.aliagent.evaluation.persistence.JdbcGateDecisionRepository jdbcGateDecisionRepository(JdbcTemplate jdbc) { return new com.bn.aliagent.evaluation.persistence.JdbcGateDecisionRepository(jdbc); }
    @Bean @Profile("database") DatasetSnapshotPort jdbcDatasetSnapshotPort() { return (tenantId, versionId) -> java.util.List.of(); }
    @Bean @Profile("database") EvaluationRunService jdbcEvaluationRunService(DatasetSnapshotPort datasets, EvaluationRunRepository runs) { return new EvaluationRunService(datasets, new MockReplayRunner(), runs); }
    @Bean @Profile("!database") DatasetSnapshotPort inMemoryDatasetSnapshotPort() { return (tenantId, versionId) -> java.util.List.of(); }
    @Bean @Profile("!database") EvaluationRunService inMemoryEvaluationRunService(DatasetSnapshotPort datasets) { return new EvaluationRunService(datasets, new MockReplayRunner()); }
    @Bean @Profile("database") GateKeys gateKeys() {
        try { return new GateKeys(KeyPairGenerator.getInstance("EC").generateKeyPair()); }
        catch (Exception exception) { throw new IllegalStateException("无法初始化 Gate 签名密钥", exception); }
    }
    @Bean @Profile("database") GateDecisionSigner gateDecisionSigner(GateKeys keys) { return new GateDecisionSigner(keyId -> new GateDecisionSigner.SigningKey(keyId, keys.pair().getPrivate(), "SHA256withECDSA")); }
    @Bean @Profile("database") GateDecisionVerifier gateDecisionVerifier(GateKeys keys) { return new GateDecisionVerifier(Map.of("database", new GateDecisionVerifier.VerificationKey(keys.pair().getPublic(), "SHA256withECDSA")), proofId -> true, Clock.systemUTC()); }
    @Bean @Profile("database") GateController jdbcGateController(GateDecisionSigner signer, com.bn.aliagent.evaluation.persistence.JdbcGateDecisionRepository decisions) { return new GateController(new GateEvaluationService(), signer, Clock.systemUTC(), decisions); }
    @Bean @Profile("!database") GateDecisionSigner inMemoryGateDecisionSigner() {
        try { var pair = KeyPairGenerator.getInstance("EC").generateKeyPair(); return new GateDecisionSigner(keyId -> new GateDecisionSigner.SigningKey(keyId, pair.getPrivate(), "SHA256withECDSA")); }
        catch (Exception exception) { throw new IllegalStateException("无法初始化 Gate 签名密钥", exception); }
    }
    @Bean @Profile("!database") GateController inMemoryGateController(GateDecisionSigner signer) { return new GateController(new GateEvaluationService(), signer, Clock.systemUTC()); }
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
    record GateKeys(KeyPair pair) { }
}
