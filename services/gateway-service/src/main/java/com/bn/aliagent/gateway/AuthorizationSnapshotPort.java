package com.bn.aliagent.gateway;

interface AuthorizationSnapshotPort {
    String issue(TrustedIdentity identity, String traceId, String requestId);
}
