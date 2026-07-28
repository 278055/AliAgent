package com.bn.aliagent.evaluation.persistence;

import com.bn.aliagent.evaluation.replay.ReplayFixture;
import com.bn.aliagent.evaluation.runner.DatasetSnapshotPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.ArrayList;
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
            return new ReplayFixture(sampleId, "published", text(sample, "input"), optionalText(expected, "intent", "MOCK_INTENT"),
                    allowedTools, citationRequirements, !citationRequirements.isEmpty(), allowedTools.contains("mall.order.read"),
                    optionalBoolean(expected, "toolFailure", false), optionalBoolean(snapshot, "expectedHumanHandoff", false),
                    optionalBoolean(expected, "openEnded", false), strings(snapshot.path("prohibitedTools")),
                    map(snapshot.path("parameterConstraints")), strings(snapshot.path("factAssertions")),
                    strings(snapshot.path("safetyLabels")), weights(snapshot.path("weights")), strings(snapshot.path("applicableMetrics")));
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取已发布评测快照", exception);
        }
    }

    private List<String> strings(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw invalid("数组", value);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw invalid("字符串数组", value);
            }
            values.add(item.textValue());
        }
        return List.copyOf(values);
    }

    private Map<String, Object> map(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return Map.of();
        }
        if (!value.isObject()) {
            throw invalid("对象", value);
        }
        return json.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
    }

    private Map<String, Double> weights(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return Map.of();
        }
        if (!value.isObject()) {
            throw invalid("数值对象", value);
        }
        Map<String, Double> values = new LinkedHashMap<>();
        value.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isNumber()) {
                throw invalid("数值对象", value);
            }
            values.put(entry.getKey(), entry.getValue().doubleValue());
        });
        return Map.copyOf(values);
    }

    private String text(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (!value.isTextual()) {
            throw invalid(field + "字符串", value);
        }
        return value.textValue();
    }

    private String optionalText(JsonNode parent, String field, String fallback) {
        JsonNode value = parent.path(field);
        if (value.isMissingNode() || value.isNull()) return fallback;
        if (!value.isTextual()) throw invalid(field + "字符串", value);
        return value.textValue();
    }

    private boolean optionalBoolean(JsonNode parent, String field, boolean fallback) {
        JsonNode value = parent.path(field);
        if (value.isMissingNode() || value.isNull()) return fallback;
        if (!value.isBoolean()) throw invalid(field + "布尔值", value);
        return value.booleanValue();
    }

    private IllegalStateException invalid(String expected, JsonNode actual) {
        return new IllegalStateException("已发布评测快照字段类型无效，期望" + expected + "，实际为" + actual.getNodeType());
    }
}
