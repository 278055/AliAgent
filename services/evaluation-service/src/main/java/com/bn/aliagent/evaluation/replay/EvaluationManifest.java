package com.bn.aliagent.evaluation.replay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** 固定一次离线回放依赖的全部版本，防止比较条件漂移。 */
public record EvaluationManifest(
        UUID promptVersionId, UUID workflowVersionId, UUID modelVersionId,
        UUID knowledgeVersionId, String toolContractVersion,
        String deterministicRuleVersion, UUID datasetVersionId,
        String scoringPolicyVersion, String judgeConfigVersion) {

    public EvaluationManifest {
        if (promptVersionId == null || workflowVersionId == null || modelVersionId == null || knowledgeVersionId == null
                || datasetVersionId == null || blank(toolContractVersion) || blank(deterministicRuleVersion)
                || blank(scoringPolicyVersion) || blank(judgeConfigVersion)) {
            throw new IllegalArgumentException("评测清单的版本字段不能为空");
        }
    }

    public String digest() {
        return sha256(String.join("|", promptVersionId.toString(), workflowVersionId.toString(), modelVersionId.toString(),
                knowledgeVersionId.toString(), toolContractVersion, deterministicRuleVersion, datasetVersionId.toString(),
                scoringPolicyVersion, judgeConfigVersion));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte valueByte : hash) result.append(String.format("%02x", valueByte));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
