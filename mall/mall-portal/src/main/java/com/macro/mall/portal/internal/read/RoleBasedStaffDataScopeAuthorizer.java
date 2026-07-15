package com.macro.mall.portal.internal.read;

import org.springframework.stereotype.Component;

@Component
public class RoleBasedStaffDataScopeAuthorizer implements StaffDataScopeAuthorizer {
    private static final String ORDER_READ_ALL_ROLE = "MALL_ORDER_READ_ALL";

    @Override
    public void checkOrderRead(UserSnapshot snapshot) {
        if (snapshot.getSubjectType() != SubjectType.STAFF || !snapshot.getRoles().contains(ORDER_READ_ALL_ROLE)) {
            throw new InternalAccessDeniedException("staff data scope does not allow order access");
        }
    }
}
