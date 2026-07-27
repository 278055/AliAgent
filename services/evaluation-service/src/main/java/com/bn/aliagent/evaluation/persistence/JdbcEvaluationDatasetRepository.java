package com.bn.aliagent.evaluation.persistence;

import com.bn.aliagent.evaluation.candidate.CandidateRepository;
import com.bn.aliagent.evaluation.candidate.CandidateReviewCommand;
import com.bn.aliagent.evaluation.candidate.CandidateStatus;
import com.bn.aliagent.evaluation.candidate.EvaluationCandidate;
import com.bn.aliagent.evaluation.candidate.ReviewAction;
import com.bn.aliagent.evaluation.candidate.ReviewingCandidateRepository;
import com.bn.aliagent.evaluation.dataset.DatasetSampleSnapshot;
import com.bn.aliagent.evaluation.anonymization.DatasetShareAuthorization;
import com.bn.aliagent.evaluation.dataset.EvaluationDataset;
import com.bn.aliagent.evaluation.dataset.EvaluationDatasetRepository;
import com.bn.aliagent.evaluation.dataset.PublishedDatasetVersion;
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

/** PostgreSQL 评测审核和数据集工作流仓储，所有读写均按租户过滤。 */
public final class JdbcEvaluationDatasetRepository implements CandidateRepository, ReviewingCandidateRepository, EvaluationDatasetRepository {
    private final JdbcTemplate jdbc;

    public JdbcEvaluationDatasetRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void save(EvaluationCandidate candidate) {
        throw new UnsupportedOperationException("数据库 profile 只能通过审核工作流更新候选");
    }

    @Override public EvaluationCandidate review(CandidateReviewCommand command, EvaluationCandidate reviewed) {
        int updated = jdbc.update("UPDATE evaluation_candidate SET status = ? WHERE tenant_id = ? AND id = ? AND status = 'PENDING_REVIEW'",
                reviewed.status().name(), command.tenantId(), command.candidateId());
        if (updated != 1) throw new IllegalStateException("候选不可审核");
        UUID reviewId = UUID.randomUUID();
        jdbc.update("INSERT INTO evaluation_candidate_review (id, tenant_id, candidate_id, reviewer_id, action, reason) VALUES (?, ?, ?, ?, ?, ?)",
                reviewId, command.tenantId(), command.candidateId(), command.reviewerId(), command.action().name(), command.reason());
        jdbc.update("INSERT INTO evaluation_audit (tenant_id, audit_id, aggregate_type, aggregate_id, event_type, actor_id, event_payload) VALUES (?, ?, 'EVALUATION_CANDIDATE', ?, 'CANDIDATE_REVIEWED', ?, ?::jsonb)",
                command.tenantId(), UUID.randomUUID(), command.candidateId(), command.reviewerId(), reviewPayload(command));
        return reviewed;
    }

    @Override public EvaluationCandidate require(String tenantId, UUID id) {
        List<EvaluationCandidate> values = jdbc.query("SELECT tenant_id, status, anonymized_body::text, body_digest FROM evaluation_candidate WHERE tenant_id = ? AND id = ?",
                (rs, row) -> new EvaluationCandidate(id, rs.getString(1), CandidateStatus.valueOf(rs.getString(2)),
                        Optional.ofNullable(rs.getString(3)), rs.getString(4), Instant.now(), Map.of(), Set.of(), ""), tenantId, id);
        if (!values.isEmpty()) return values.get(0);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_candidate WHERE id = ?", Integer.class, id);
        if (exists != null && exists > 0) throw new SecurityException("跨租户访问被拒绝");
        throw new IllegalArgumentException("候选不存在");
    }

    @Override public Collection<EvaluationCandidate> all() { return List.of(); }

    @Override public EvaluationDataset createDraft(String tenantId, String name) {
        EvaluationDataset draft = new EvaluationDataset(UUID.randomUUID(), tenantId, name, false, List.of());
        jdbc.update("INSERT INTO evaluation_dataset (id, tenant_id, name, state) VALUES (?, ?, ?, 'DRAFT')", draft.id(), tenantId, name);
        return draft;
    }

    @Override public void addCandidate(String tenantId, UUID draftId, UUID candidateId) {
        requireEditableDraft(tenantId, draftId);
        EvaluationCandidate candidate = require(tenantId, candidateId);
        if (candidate.status() != CandidateStatus.ACCEPTED) throw new IllegalStateException("只允许已接受候选加入评测集");
        Integer order = jdbc.queryForObject("SELECT COALESCE(MAX(sample_order), -1) + 1 FROM evaluation_dataset_draft_sample WHERE tenant_id = ? AND draft_id = ?", Integer.class, tenantId, draftId);
        jdbc.update("INSERT INTO evaluation_dataset_draft_sample (tenant_id, draft_id, sample_id, dataset_id, sample_payload, sample_order) VALUES (?, ?, ?, ?, ?::jsonb, ?)",
                tenantId, draftId, candidateId, draftId, candidate.body().orElseThrow(() -> new IllegalStateException("候选正文已删除")), order);
    }

    @Override public PublishedDatasetVersion publish(String tenantId, UUID draftId, boolean publiclyShared, DatasetShareAuthorization authorization) {
        if (publiclyShared) throw new UnsupportedOperationException("数据库 profile 的公共发布尚未配置匿名化授权");
        EvaluationDataset draft = requireEditableDraft(tenantId, draftId);
        List<DatasetSampleSnapshot> samples = jdbc.query("SELECT sample_id, sample_payload::text FROM evaluation_dataset_draft_sample WHERE tenant_id = ? AND draft_id = ? ORDER BY sample_order",
                (rs, row) -> new DatasetSampleSnapshot(UUID.fromString(rs.getString(1)), rs.getString(2), Map.of(), Set.of(), Set.of(), Set.of(), Map.of(), Set.of(), Set.of(), Set.of(), false, Map.of(), Set.of()), tenantId, draftId);
        int version = jdbc.queryForObject("SELECT COALESCE(MAX(version_number), 0) + 1 FROM evaluation_dataset_version WHERE tenant_id = ? AND dataset_id = ?", Integer.class, tenantId, draftId);
        String digest = digest(samples);
        UUID versionId = UUID.randomUUID();
        jdbc.update("INSERT INTO evaluation_dataset_version (id, tenant_id, dataset_id, version_number, content_digest, visibility) VALUES (?, ?, ?, ?, ?, ?)",
                versionId, tenantId, draftId, version, digest, publiclyShared ? "PUBLIC" : "PRIVATE");
        for (DatasetSampleSnapshot sample : samples) jdbc.update("INSERT INTO evaluation_sample_snapshot (id, tenant_id, dataset_version_id, sample_json) VALUES (?, ?, ?, ?::jsonb)",
                UUID.randomUUID(), tenantId, versionId, sample.input());
        int updated = jdbc.update("UPDATE evaluation_dataset SET state = 'PUBLISHED' WHERE tenant_id = ? AND id = ? AND state = 'DRAFT'", tenantId, draftId);
        if (updated != 1) throw new IllegalStateException("已发布草稿不可编辑");
        return new PublishedDatasetVersion(versionId, tenantId, draft.id(), version, digest, publiclyShared, samples);
    }

    @Override public PublishedDatasetVersion requirePublished(String tenantId, UUID datasetVersionId) {
        List<PublishedDatasetVersion> versions = jdbc.query("SELECT tenant_id, dataset_id, version_number, content_digest, visibility FROM evaluation_dataset_version WHERE tenant_id = ? AND id = ?",
                (rs, row) -> new PublishedDatasetVersion(datasetVersionId, rs.getString(1), UUID.fromString(rs.getString(2)), rs.getInt(3), rs.getString(4), "PUBLIC".equals(rs.getString(5)), List.of()), tenantId, datasetVersionId);
        if (!versions.isEmpty()) return versions.get(0);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_dataset_version WHERE id = ?", Integer.class, datasetVersionId);
        if (exists != null && exists > 0) throw new SecurityException("跨租户访问被拒绝");
        throw new IllegalArgumentException("评测集版本不存在");
    }

    private EvaluationDataset requireEditableDraft(String tenantId, UUID draftId) {
        List<EvaluationDataset> drafts = jdbc.query("SELECT name, state FROM evaluation_dataset WHERE tenant_id = ? AND id = ?", (rs, row) ->
                new EvaluationDataset(draftId, tenantId, rs.getString(1), "PUBLISHED".equals(rs.getString(2)), List.of()), tenantId, draftId);
        if (drafts.isEmpty()) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_dataset WHERE id = ?", Integer.class, draftId);
            if (exists != null && exists > 0) throw new SecurityException("跨租户访问被拒绝");
            throw new IllegalArgumentException("评测集不存在");
        }
        if (drafts.get(0).published()) throw new IllegalStateException("已发布草稿不可编辑");
        return drafts.get(0);
    }

    private static String reviewPayload(CandidateReviewCommand command) {
        return "{\"action\":\"" + command.action().name() + "\",\"reason\":\"" + escape(command.reason()) + "\",\"expected\":\"" + escape(String.valueOf(command.expected())) + "\",\"labels\":\"" + escape(String.valueOf(command.labels())) + "\"}";
    }
    private static String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String digest(List<DatasetSampleSnapshot> samples) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(samples.toString().getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }
}
