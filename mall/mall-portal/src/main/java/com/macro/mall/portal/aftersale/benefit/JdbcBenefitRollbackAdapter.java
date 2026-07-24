package com.macro.mall.portal.aftersale.benefit;

import com.macro.mall.portal.aftersale.api.BenefitRollbackCommand;
import com.macro.mall.portal.aftersale.api.BenefitRollbackPort;
import com.macro.mall.portal.aftersale.api.StepResult;
import com.macro.mall.portal.aftersale.api.StockRollbackItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 权益写入加入 P6-A 已开启的本地事务，步骤幂等由原子执行端口持有。 */
@Component
public final class JdbcBenefitRollbackAdapter implements BenefitRollbackPort {
    private final JdbcTemplate jdbc;

    public JdbcBenefitRollbackAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public StepResult restoreStock(BenefitRollbackCommand command) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (StockRollbackItem item : command.stockItems()) {
            quantities.merge(item.productSkuId(), item.quantity(), Integer::sum);
        }
        if (quantities.isEmpty()) return success("stock rollback not required");
        String placeholders = quantities.keySet().stream().map(id -> "?").collect(Collectors.joining(","));
        Object[] ids = quantities.keySet().toArray();
        List<Long> locked = jdbc.queryForList("SELECT id FROM pms_sku_stock WHERE id IN (" + placeholders + ") FOR UPDATE", Long.class, ids);
        if (locked.size() != quantities.size()) return failure("STOCK_FACT_MISMATCH");

        String cases = quantities.keySet().stream().map(id -> "WHEN ? THEN ?").collect(Collectors.joining(" "));
        List<Object> arguments = new ArrayList<>();
        for (Map.Entry<Long, Integer> quantity : quantities.entrySet()) {
            arguments.add(quantity.getKey());
            arguments.add(quantity.getValue());
        }
        arguments.addAll(quantities.keySet());
        int updated = jdbc.update("UPDATE pms_sku_stock SET stock=stock+CASE id " + cases + " ELSE 0 END WHERE id IN (" + placeholders + ")", arguments.toArray());
        return updated == quantities.size() ? success("stock rollback completed") : failure("STOCK_UPDATE_MISMATCH");
    }

    @Override
    public StepResult restoreCoupon(BenefitRollbackCommand command) {
        if (command.couponHistoryId() == null) return success("coupon rollback not required");
        int updated = jdbc.update("UPDATE sms_coupon_history SET use_status=0,use_time=NULL,order_id=NULL,order_sn=NULL WHERE id=? AND member_id=? AND order_id=?",
                command.couponHistoryId(), command.memberId(), command.orderId());
        return updated == 1 ? success("coupon rollback completed") : failure("COUPON_FACT_MISMATCH");
    }

    @Override
    public StepResult restorePoints(BenefitRollbackCommand command) {
        if (command.usedIntegration() == null || command.usedIntegration() <= 0) return success("points rollback not required");
        int updated = jdbc.update("UPDATE ums_member SET integration=COALESCE(integration,0)+? WHERE id=?", command.usedIntegration(), command.memberId());
        return updated == 1 ? success("points rollback completed") : failure("POINTS_FACT_MISMATCH");
    }

    private StepResult success(String detail) { return new StepResult("SUCCEEDED", detail); }
    private StepResult failure(String detail) { return new StepResult("FAILED", detail); }
}
