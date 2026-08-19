package com.macro.mall.portal.internal.read;

/** 洞察订单核验只允许已通过内部服务认证的主管身份调用。 */
public final class InsightEvidenceAuthorization {
    public void requireSupervisor(UserSnapshot snapshot) {
        if (snapshot == null || snapshot.getSubjectType() != SubjectType.STAFF || !snapshot.getRoles().contains("SUPERVISOR")) {
            throw new InternalAccessDeniedException("only supervisors can verify insight evidence");
        }
    }
}
