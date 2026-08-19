package com.macro.mall.insight;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** mall-admin 与 portal 共用订单库，直接读取 portal 写入的可信订单归属绑定。 */
@Component
public final class JdbcTrustedOrderTenantResolver implements TrustedOrderTenantResolver {
    private final JdbcTemplate jdbc;

    public JdbcTrustedOrderTenantResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String requireTenantId(Long orderId) {
        List<String> tenantIds = jdbc.query("SELECT tenant_id FROM order_tenant_binding WHERE order_id=?",
                (resultSet, row) -> resultSet.getString(1), orderId);
        if (tenantIds.size() != 1 || tenantIds.get(0) == null || tenantIds.get(0).trim().isEmpty()) {
            throw new SecurityException("订单不存在可信租户归属，拒绝产生洞察事件");
        }
        return tenantIds.get(0);
    }
}
