package com.bn.aliagent.orchestration.governance;

import java.util.UUID;

/** 独立验证 P8 签发的证明；依赖不可用时必须拒绝发布。 */
public interface GateDecisionPort {
    void requirePass(String tenantId, VersionType type, UUID versionId, String manifestDigest, String policyVersion, String proof);
}
