package com.bn.aliagent.rag.retriever;

/** 仅允许由网关注入并透传给知识服务的请求上下文。 */
public record TrustedKnowledgeContext(String tenantId, String subjectId, String subjectType, String userRoles,
        String userPermissions, String authorizationSnapshotId, String traceId, String serviceAuthorization) {
}
