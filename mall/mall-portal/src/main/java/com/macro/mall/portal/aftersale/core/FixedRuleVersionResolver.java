package com.macro.mall.portal.aftersale.core;

import org.springframework.stereotype.Component;

/** P6 冻结期的规则版本入口；申请创建后仅持久化该结果，重试绝不重新解析。 */
@Component
public final class FixedRuleVersionResolver implements RuleVersionResolver {
    @Override public String resolve(AfterSaleCommand command) { return "p6-aftersale-rules-v1"; }
}
