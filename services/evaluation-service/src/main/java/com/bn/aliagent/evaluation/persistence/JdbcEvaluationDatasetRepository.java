package com.bn.aliagent.evaluation.persistence;

import com.bn.aliagent.evaluation.candidate.CandidateRepository;
import com.bn.aliagent.evaluation.candidate.CandidateReviewCommand;
import com.bn.aliagent.evaluation.candidate.CandidateStatus;
import com.bn.aliagent.evaluation.candidate.EvaluationCandidate;
import com.bn.aliagent.evaluation.candidate.ReviewAction;
import com.bn.aliagent.evaluation.candidate.ReviewingCandidateRepository;
import com.bn.aliagent.evaluation.dataset.DatasetSampleSnapshot;
import com.bn.aliagent.evaluation.anonymization.DatasetShareAuthorization;
import com.bn.aliagent.evaluation.anonymization.AnonymizationStatus;
import com.bn.aliagent.evaluation.anonymization.PublicDatasetAnonymizer;
import com.bn.aliagent.evaluation.dataset.EvaluationDataset;
import com.bn.aliagent.evaluation.dataset.EvaluationDatasetRepository;
import com.bn.aliagent.evaluation.dataset.PublishedDatasetVersion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL 评测审核和数据集工作流仓储，所有读写均按租户过滤。 */
public final class JdbcEvaluationDatasetRepository implements CandidateRepository, ReviewingCandidateRepository, EvaluationDatasetRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;
    private final PublicDatasetAnonymizer publicAnonymizer;
    private final TransactionTemplate transactions;

    public JdbcEvaluationDatasetRepository(JdbcTemplate jdbc, PublicDatasetAnonymizer publicAnonymizer) {
        this.jdbc = jdbc; this.publicAnonymizer = publicAnonymizer;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    @Override public void save(EvaluationCandidate candidate) {
        throw new UnsupportedOperationException("数据库 profile 只能通过审核工作流更新候选");
    }

    @Override public EvaluationCandidate review(CandidateReviewCommand command, EvaluationCandidate reviewed) {
        return transactions.execute(status -> {
            int updated = jdbc.update("UPDATE evaluation_candidate SET status = ?, review_expected = ?::jsonb, review_labels = ?::jsonb WHERE tenant_id = ? AND id = ? AND status = 'PENDING_REVIEW'", reviewed.status().name(), mapJson(reviewed.expected()), labelsJson(reviewed.labels()), command.tenantId(), command.candidateId());
            if (updated != 1) throw new IllegalStateException("候选不可审核");
            jdbc.update("INSERT INTO evaluation_candidate_review (id, tenant_id, candidate_id, reviewer_id, action, reason) VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), command.tenantId(), command.candidateId(), command.reviewerId(), command.action().name(), command.reason());
            jdbc.update("INSERT INTO evaluation_audit (tenant_id, audit_id, aggregate_type, aggregate_id, event_type, actor_id, event_payload) VALUES (?, ?, 'EVALUATION_CANDIDATE', ?, 'CANDIDATE_REVIEWED', ?, ?::jsonb)", command.tenantId(), UUID.randomUUID(), command.candidateId(), command.reviewerId(), reviewPayload(command));
            return reviewed;
        });
    }

    @Override public EvaluationCandidate require(String tenantId, UUID id) {
        List<EvaluationCandidate> values = jdbc.query("SELECT tenant_id, status, anonymized_body::text, body_digest, review_expected::text, review_labels::text FROM evaluation_candidate WHERE tenant_id = ? AND id = ?",
                (rs, row) -> new EvaluationCandidate(id, rs.getString(1), CandidateStatus.valueOf(rs.getString(2)),
                        Optional.ofNullable(rs.getString(3)), rs.getString(4), Instant.now(), jsonMap(rs.getString(5)), jsonLabels(rs.getString(6)), ""), tenantId, id);
        if (!values.isEmpty()) return values.get(0);
        throw new SecurityException("候选访问被拒绝");
    }

    @Override public Collection<EvaluationCandidate> all() { return List.of(); }

    @Override public EvaluationDataset createDraft(String tenantId, String name) {
        EvaluationDataset draft = new EvaluationDataset(UUID.randomUUID(), tenantId, name, false, List.of());
        jdbc.update("INSERT INTO evaluation_dataset (id, tenant_id, name, state) VALUES (?, ?, ?, 'DRAFT')", draft.id(), tenantId, name);
        return draft;
    }

    @Override public void addCandidate(String tenantId, UUID draftId, UUID candidateId) {
        transactions.executeWithoutResult(status -> {
            requireEditableDraftLocked(tenantId, draftId);
            EvaluationCandidate candidate = require(tenantId, candidateId);
            if (candidate.status() != CandidateStatus.ACCEPTED) throw new IllegalStateException("只允许已接受候选加入评测集");
            Integer order = jdbc.queryForObject("SELECT COALESCE(MAX(sample_order), -1) + 1 FROM evaluation_dataset_draft_sample WHERE tenant_id = ? AND draft_id = ?", Integer.class, tenantId, draftId);
            jdbc.update("INSERT INTO evaluation_dataset_draft_sample (tenant_id, draft_id, sample_id, dataset_id, sample_payload, sample_order) VALUES (?, ?, ?, ?, ?::jsonb, ?)", tenantId, draftId, candidateId, draftId, candidate.body().orElseThrow(() -> new IllegalStateException("候选正文已删除")), order);
        });
    }

    @Override public PublishedDatasetVersion publish(String tenantId, UUID draftId, boolean publiclyShared, DatasetShareAuthorization authorization) {
        return transactions.execute(status -> publishLocked(tenantId, draftId, publiclyShared, authorization));
    }

    private PublishedDatasetVersion publishLocked(String tenantId, UUID draftId, boolean publiclyShared, DatasetShareAuthorization authorization) {
        EvaluationDataset draft = requireEditableDraftLocked(tenantId, draftId);
        List<DatasetSampleSnapshot> samples = jdbc.query("SELECT sample_id, sample_payload::text FROM evaluation_dataset_draft_sample WHERE tenant_id = ? AND draft_id = ? ORDER BY sample_order",
                (rs, row) -> { EvaluationCandidate candidate = require(tenantId, UUID.fromString(rs.getString(1))); String input = rs.getString(2); if (publiclyShared) { var result = publicAnonymizer.anonymize(Map.of("input", input), tenantId, authorization); if (result.status() != AnonymizationStatus.SAFE) throw new IllegalStateException("公共匿名化失败"); input = result.canonicalJson(); } return new DatasetSampleSnapshot(candidate.id(), input, candidate.expected(), candidate.labels(), Set.of(), Set.of(), Map.of(), Set.of(), Set.of(), Set.of(), false, Map.of(), Set.of()); }, tenantId, draftId);
        int version = jdbc.queryForObject("SELECT COALESCE(MAX(version_number), 0) + 1 FROM evaluation_dataset_version WHERE tenant_id = ? AND dataset_id = ?", Integer.class, tenantId, draftId);
        String digest = digest(samples);
        UUID versionId = UUID.randomUUID();
        jdbc.update("INSERT INTO evaluation_dataset_version (id, tenant_id, dataset_id, version_number, content_digest, visibility) VALUES (?, ?, ?, ?, ?, ?)",
                versionId, tenantId, draftId, version, digest, publiclyShared ? "PUBLIC" : "PRIVATE");
        for (DatasetSampleSnapshot sample : samples) jdbc.update("INSERT INTO evaluation_sample_snapshot (id, tenant_id, dataset_version_id, sample_json) VALUES (?, ?, ?, ?::jsonb)", UUID.randomUUID(), tenantId, versionId, snapshotJson(sample));
        int updated = jdbc.update("UPDATE evaluation_dataset SET state = 'PUBLISHED' WHERE tenant_id = ? AND id = ? AND state = 'DRAFT'", tenantId, draftId);
        if (updated != 1) throw new IllegalStateException("已发布草稿不可编辑");
        return new PublishedDatasetVersion(versionId, tenantId, draft.id(), version, digest, publiclyShared, samples);
    }

    @Override public PublishedDatasetVersion requirePublished(String tenantId, UUID datasetVersionId) {
        List<PublishedDatasetVersion> versions = jdbc.query("SELECT tenant_id, dataset_id, version_number, content_digest, visibility FROM evaluation_dataset_version WHERE tenant_id = ? AND id = ?",
                (rs, row) -> new PublishedDatasetVersion(datasetVersionId, rs.getString(1), UUID.fromString(rs.getString(2)), rs.getInt(3), rs.getString(4), "PUBLIC".equals(rs.getString(5)), samples(tenantId, datasetVersionId)), tenantId, datasetVersionId);
        if (!versions.isEmpty()) return versions.get(0);
        throw new SecurityException("评测集版本访问被拒绝");
    }

    private EvaluationDataset requireEditableDraft(String tenantId, UUID draftId) {
        List<EvaluationDataset> drafts = jdbc.query("SELECT name, state FROM evaluation_dataset WHERE tenant_id = ? AND id = ?", (rs, row) ->
                new EvaluationDataset(draftId, tenantId, rs.getString(1), "PUBLISHED".equals(rs.getString(2)), List.of()), tenantId, draftId);
        if (drafts.isEmpty()) {
            throw new SecurityException("评测集访问被拒绝");
        }
        if (drafts.get(0).published()) throw new IllegalStateException("已发布草稿不可编辑");
        return drafts.get(0);
    }
    private EvaluationDataset requireEditableDraftLocked(String tenantId, UUID draftId) {
        List<EvaluationDataset> drafts = jdbc.query("SELECT name, state FROM evaluation_dataset WHERE tenant_id = ? AND id = ? FOR UPDATE", (rs, row) -> new EvaluationDataset(draftId, tenantId, rs.getString(1), "PUBLISHED".equals(rs.getString(2)), List.of()), tenantId, draftId);
        if (drafts.isEmpty()) throw new SecurityException("评测集访问被拒绝");
        if (drafts.get(0).published()) throw new IllegalStateException("已发布草稿不可编辑");
        return drafts.get(0);
    }

    private static String reviewPayload(CandidateReviewCommand command) {
        return "{\"action\":\"" + command.action().name() + "\",\"reason\":\"" + escape(command.reason()) + "\",\"expected\":\"" + escape(String.valueOf(command.expected())) + "\",\"labels\":\"" + escape(String.valueOf(command.labels())) + "\"}";
    }
    private List<DatasetSampleSnapshot> samples(String tenantId, UUID versionId) { return jdbc.query("SELECT sample_json::text FROM evaluation_sample_snapshot WHERE tenant_id = ? AND dataset_version_id = ? ORDER BY id", (rs, row) -> { Map<String, Object> value = jsonMap(rs.getString(1)); return new DatasetSampleSnapshot(null, String.valueOf(value.get("input")), mapValue(value.get("expected")), labelsValue(value.get("labels")), Set.of(), Set.of(), Map.of(), Set.of(), Set.of(), Set.of(), false, Map.of(), Set.of()); }, tenantId, versionId); }
    @SuppressWarnings("unchecked") private static Map<String, Object> mapValue(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }
    @SuppressWarnings("unchecked") private static Set<String> labelsValue(Object value) { return value instanceof Collection<?> values ? ((Collection<Object>) values).stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet()) : Set.of(); }
    private static String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static Map<String, Object> jsonMap(String value) { return read(value, new TypeReference<>() { }, Map.of()); }
    private static Set<String> jsonLabels(String value) { return Set.copyOf(read(value, new TypeReference<>() { }, List.<String>of())); }
    private static String snapshotJson(DatasetSampleSnapshot sample) { return write(Map.of("input", sample.input(), "expected", sample.expected(), "labels", sample.labels())); }
    private static String mapJson(Map<String, Object> value) { return write(value); }
    private static String labelsJson(Set<String> value) { return write(value); }
    private static String write(Object value) { try { return JSON.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("无法序列化评测元数据", exception); } }
    private static <T> T read(String value, TypeReference<T> type, T fallback) { try { return value == null ? fallback : JSON.readValue(value, type); } catch (Exception exception) { throw new IllegalStateException("无法读取评测元数据", exception); } }
    private static String digest(List<DatasetSampleSnapshot> samples) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(samples.toString().getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }
}
