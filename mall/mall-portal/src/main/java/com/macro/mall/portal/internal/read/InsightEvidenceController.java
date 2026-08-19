package com.macro.mall.portal.internal.read;

import com.macro.mall.portal.aftersale.core.OrderTenantResolver;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅返回当前核验所需的状态，不暴露完整订单，也不向 insight_db 写入任何数据。 */
@RestController
@RequestMapping("/internal/v1/insights/evidence")
public final class InsightEvidenceController {
    private final OrderTenantResolver tenants;
    private final InsightEvidenceAuthorization authorization = new InsightEvidenceAuthorization();

    public InsightEvidenceController(OrderTenantResolver tenants) { this.tenants = tenants; }

    @GetMapping("/{evidenceRef}")
    public Map<String, String> verify(@PathVariable String evidenceRef, @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestAttribute("com.macro.mall.portal.internal.read.UserSnapshot") UserSnapshot snapshot) {
        authorization.requireSupervisor(snapshot);
        long orderId = orderId(evidenceRef);
        if (!tenantId.equals(snapshot.getTenantId()) || !tenantId.equals(tenants.resolve(orderId).tenantId())) {
            throw new InternalAccessDeniedException("evidence does not belong to tenant");
        }
        return Map.of("tenantId", tenantId, "evidenceRef", evidenceRef, "status", "VERIFIED", "reference", "order-verified");
    }

    private static long orderId(String evidenceRef) {
        if (evidenceRef == null || !evidenceRef.startsWith("order-")) throw new IllegalArgumentException("invalid evidence reference");
        try { return Long.parseLong(evidenceRef.substring("order-".length())); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("invalid evidence reference", exception); }
    }
}
