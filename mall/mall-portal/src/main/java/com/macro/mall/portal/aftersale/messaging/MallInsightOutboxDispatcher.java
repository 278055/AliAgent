package com.macro.mall.portal.aftersale.messaging;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class MallInsightOutboxDispatcher {
    private final JdbcTemplate jdbc; private final RabbitTemplate rabbit;
    public MallInsightOutboxDispatcher(JdbcTemplate jdbc, RabbitTemplate rabbit) { this.jdbc = jdbc; this.rabbit = rabbit; }
    @Scheduled(fixedDelayString = "${mall.insight.outbox-dispatch-delay:5000}")
    public void dispatch() {
        List<Row> rows = jdbc.query("SELECT event_id,order_id,tenant_id,trace_id,amount,order_occurred_at,occurred_at FROM mall_insight_outbox WHERE status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP(6) ORDER BY occurred_at LIMIT 100", (rs, index) -> new Row(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getTimestamp(6), rs.getTimestamp(7)));
        for (Row row : rows) try {
            Map<String, Object> event = P9OrderPaidEventMapper.map(row.eventId, row.tenantId, row.traceId, row.orderId, row.amount, row.orderOccurredAt.toInstant().toString(), row.occurredAt.toInstant().toString());
            rabbit.convertAndSend("insight.events.v1", event);
            jdbc.update("UPDATE mall_insight_outbox SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP(6),last_error=NULL WHERE event_id=? AND status='PENDING'", row.eventId);
        } catch (RuntimeException exception) {
            jdbc.update("UPDATE mall_insight_outbox SET attempts=attempts+1,next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 MINUTE),last_error=? WHERE event_id=? AND status='PENDING'", exception.getClass().getSimpleName(), row.eventId);
        }
    }
    private static final class Row {
        private final String eventId; private final long orderId; private final String tenantId; private final String traceId; private final BigDecimal amount; private final Timestamp orderOccurredAt; private final Timestamp occurredAt;
        private Row(String eventId, long orderId, String tenantId, String traceId, BigDecimal amount, Timestamp orderOccurredAt, Timestamp occurredAt) { this.eventId = eventId; this.orderId = orderId; this.tenantId = tenantId; this.traceId = traceId; this.amount = amount; this.orderOccurredAt = orderOccurredAt; this.occurredAt = occurredAt; }
    }
}
