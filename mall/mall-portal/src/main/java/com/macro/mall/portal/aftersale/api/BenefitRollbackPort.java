package com.macro.mall.portal.aftersale.api;

public interface BenefitRollbackPort {
    StepResult restoreStock(BenefitRollbackCommand command);
    StepResult restoreCoupon(BenefitRollbackCommand command);
    StepResult restorePoints(BenefitRollbackCommand command);
}
