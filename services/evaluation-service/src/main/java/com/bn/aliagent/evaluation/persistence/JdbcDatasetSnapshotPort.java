package com.bn.aliagent.evaluation.persistence;

import com.bn.aliagent.evaluation.replay.ReplayFixture;
import com.bn.aliagent.evaluation.runner.DatasetSnapshotPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** 将已发布的不可变快照映射为回放样本。 */
public final class JdbcDatasetSnapshotPort implements DatasetSnapshotPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public JdbcDatasetSnapshotPort(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ReplayFixture> published(String tenantId, UUID datasetVersionId) {
        return jdbc.query("SELECT id, sample_json::text, snapshot_payload::text FROM evaluation_sample_snapshot WHERE tenant_id = ? AND dataset_version_id = ? ORDER BY id",
                (rs, row) -> fixture(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), tenantId, datasetVersionId);
    }

    private ReplayFixture fixture(UUID sampleId, String sampleValue, String snapshotValue) {
        try {
            JsonNode sample = json.readTree(sampleValue);
            JsonNode snapshot = json.readTree(snapshotValue);
            JsonNode expected = sample.path("expected");
            List<String> allowedTools = strings(snapshot.path("allowedTools"));
            List<String> citationRequirements = strings(snapshot.path("citationRequirements"));
            return new ReplayFixture(sampleId, "published", sample.path("input").asText(), expected.path("intent").asText("MOCK_INTENT"),
                    allowedTools, citationRequirements, !citationRequirements.isEmpty(), allowedTools.contains("mall.order.read"),
                    expected.path("toolFailure").asBoolean(false), snapshot.path("expectedHumanHandoff").asBoolean(false),
                    expected.path("openEnded").asBoolean(false), strings(snapshot.path("prohibitedTools")),
                    map(snapshot.path("parameterConstraints")), strings(snapshot.path("factAssertions")),
                    strings(snapshot.path("safetyLabels")), weights(snapshot.path("weights")), strings(snapshot.path("applicableMetrics")));
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取已发布评测快照", exception);
        }
    }

    private List<String> strings(JsonNode value) {
        return value.isArray() ? json.convertValue(value, new TypeReference<List<String>>() { }) : List.of();
    }

    private Map<String, Object> map(JsonNode value) {
        return value.isObject() ? json.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() { }) : Map.of();
    }

    private Map<String, Double> weights(JsonNode value) {
        return value.isObject() ? json.convertValue(value, new TypeReference<LinkedHashMap<String, Double>>() { }) : Map.of();
    }
}
