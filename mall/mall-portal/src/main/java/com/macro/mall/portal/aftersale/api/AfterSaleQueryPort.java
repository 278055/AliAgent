package com.macro.mall.portal.aftersale.api;

import java.util.Optional;

public interface AfterSaleQueryPort {
    Optional<AfterSaleView> find(TrustedAfterSaleContext context, Long caseId);
}
