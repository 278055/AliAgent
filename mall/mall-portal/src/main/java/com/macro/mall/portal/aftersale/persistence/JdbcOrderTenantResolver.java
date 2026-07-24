package com.macro.mall.portal.aftersale.persistence;

import com.macro.mall.portal.aftersale.core.OrderTenantResolver;
import com.macro.mall.portal.aftersale.core.ResolvedOrderTenant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;

/** 唯一从订单租户绑定表解析售后订单租户，拒绝任何非唯一结果。 */
@Component
public final class JdbcOrderTenantResolver implements OrderTenantResolver {
    private static final String SQL = "SELECT order_id, tenant_id, source, bound_at FROM order_tenant_binding WHERE order_id=?";
    private final JdbcTemplate jdbc;

    public JdbcOrderTenantResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public ResolvedOrderTenant resolve(Long orderId) {
        List<ResolvedOrderTenant> values = jdbc.query(SQL, (result, row) -> {
            Timestamp boundAt = result.getTimestamp("bound_at");
            return new ResolvedOrderTenant(result.getLong("order_id"), result.getString("tenant_id"), result.getString("source"), boundAt.toInstant());
        }, orderId);
        if (values.size() != 1 || !orderId.equals(values.get(0).orderId())) throw new SecurityException("ORDER_TENANT_MISMATCH");
        return values.get(0);
    }
}
