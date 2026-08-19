package com.bn.aliagent.insight.adapter;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 订单核验结果只返回给当前请求，不写入 insight_db。 */
public final class MallEvidenceVerificationClient {
    private final MallEvidencePort mall;

    public MallEvidenceVerificationClient(MallEvidencePort mall) { this.mall = Objects.requireNonNull(mall); }

    public VerificationResult verify(Request request, Supervisor supervisor) {
        if (request.radarId() == null || blank(request.reason()) || blank(request.evidenceRef()) || blank(request.serviceJwt())) {
            throw new IllegalArgumentException("雷达、原因、证据引用和服务 JWT 均为必填项");
        }
        if (!request.tenantId().equals(supervisor.tenantId()) || !supervisor.roles().contains("SUPERVISOR")) {
            throw new SecurityException("仅同租户主管可以核验订单");
        }
        VerificationResult result = mall.verify(request, supervisor);
        if (!request.tenantId().equals(result.tenantId()) || !request.evidenceRef().equals(result.evidenceRef())) {
            throw new SecurityException("mall 返回了不属于当前租户或证据的结果");
        }
        return result;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    @FunctionalInterface public interface MallEvidencePort {
        VerificationResult verify(Request request, Supervisor supervisor);
    }
    public record Request(UUID radarId, String tenantId, String evidenceRef, String reason, String serviceJwt) { }
    public record Supervisor(String subjectId, String tenantId, Set<String> roles) { public Supervisor { roles = Set.copyOf(roles); } }
    public record VerificationResult(String tenantId, String evidenceRef, String status, String reference) { }
}
