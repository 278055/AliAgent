package com.bn.aliagent.evaluation.dataset;

import com.bn.aliagent.evaluation.anonymization.DatasetShareAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 控制器只接受网关注入且服务 JWT 已验证的身份头。 */
public final class DatasetController {
    private static final String VERIFIED_ATTRIBUTE = "com.bn.platform.security.ServiceJwtAuthenticationFilter.verified";
    private final EvaluationDatasetService datasets;
    public DatasetController(EvaluationDatasetService datasets) { this.datasets = datasets; }
    public EvaluationDataset createDraft(String name, HttpServletRequest request) { TrustedIdentity identity = identity(request); return datasets.createDraft(identity.tenantId(), name); }
    public void addCandidate(UUID datasetId, UUID candidateId, HttpServletRequest request) { TrustedIdentity identity = identity(request); datasets.addCandidate(identity.tenantId(), datasetId, candidateId); }
    public PublishedDatasetVersion publish(UUID datasetId, boolean publiclyShared, HttpServletRequest request) {
        TrustedIdentity identity = identity(request);
        DatasetShareAuthorization authorization = publiclyShared ? identity.publicationAuthorization() : null;
        return datasets.publish(identity.tenantId(), datasetId, publiclyShared, authorization);
    }
    private static TrustedIdentity identity(HttpServletRequest request) {
        if (!Boolean.TRUE.equals(request.getAttribute(VERIFIED_ATTRIBUTE))) throw new SecurityException("service authentication is required");
        String tenant = required(request, "X-Tenant-Id"), subject = required(request, "X-Subject-Id");
        String permissionHeader = request.getHeader("X-User-Permissions");
        Set<String> permissions = Arrays.stream((permissionHeader == null ? "" : permissionHeader).split(",")).filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
        return new TrustedIdentity(tenant, subject, permissions);
    }
    private static String required(HttpServletRequest request, String header) { String value = request.getHeader(header); if (value == null || value.isBlank()) throw new SecurityException("missing trusted identity header"); return value; }
    private record TrustedIdentity(String tenantId, String subjectId, Set<String> permissions) {
        DatasetShareAuthorization publicationAuthorization() { if (!permissions.contains("EVALUATION_DATASET_PUBLIC_PUBLISH")) throw new SecurityException("public publication is not authorized"); return DatasetShareAuthorization.active(subjectId, Instant.now()); }
    }
}
