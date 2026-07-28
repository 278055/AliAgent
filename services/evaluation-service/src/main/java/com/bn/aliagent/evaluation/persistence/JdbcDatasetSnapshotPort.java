package com.bn.aliagent.evaluation.persistence;

import com.bn.aliagent.evaluation.replay.ReplayFixture;
import com.bn.aliagent.evaluation.runner.DatasetSnapshotPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** 将已发布的不可变样本快照映射为回放输入。 */
public final class JdbcDatasetSnapshotPort implements DatasetSnapshotPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public JdbcDatasetSnapshotPort(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ReplayFixture> published(String tenantId, UUID datasetVersionId) {
        return jdbc.query("SELECT id, sample_json::text FROM evaluation_sample_snapshot WHERE tenant_id = ? AND dataset_version_id = ? ORDER BY id",
                (rs, row) -> fixture(rs.getObject(1, UUID.class), rs.getString(2)), tenantId, datasetVersionId);
    }

    private ReplayFixture fixture(UUID sampleId, String value) {
        try {
            JsonNode sample = json.readTree(value);
            JsonNode expected = sample.path("expected");
            String intent = expected.path("intent").asText("MOCK_INTENT");
            return new ReplayFixture(sampleId, "published", sample.path("input").asText(), intent,
                    List.of(), List.of(), false, false, false, false, false);
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取已发布评测快照", exception);
        }
    }
}
