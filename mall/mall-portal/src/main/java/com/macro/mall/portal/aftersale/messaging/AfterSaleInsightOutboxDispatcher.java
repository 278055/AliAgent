package com.macro.mall.portal.aftersale.messaging;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 仅将已确认的退款成功事实投递到 P9，其他售后命令不作为洞察事实。 */
@Component
public final class AfterSaleInsightOutboxDispatcher {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;

    public AfterSaleInsightOutboxDispatcher(JdbcTemplate jdbc, RabbitTemplate rabbit) { this.jdbc = jdbc; this.rabbit = rabbit; }

    @Scheduled(fixedDelayString = "${mall.insight.outbox-dispatch-delay:5000}")
    public void dispatch() {
        List<Row> rows = jdbc.query("SELECT o.event_id,o.aggregate_id,o.tenant_id,o.trace_id,o.occurred_at,c.requested_amount FROM after_sale_outbox o JOIN after_sale_case c ON c.id=o.aggregate_id AND c.tenant_id=o.tenant_id WHERE o.status='PENDING' AND o.event_type='RefundSucceeded' AND o.next_attempt_at<=CURRENT_TIMESTAMP(6) ORDER BY o.occurred_at LIMIT 100", (rs, index) -> new Row(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5), rs.getBigDecimal(6)));
        for (Row row : rows) {
            try {
                Map<String, Object> event = P9RefundEventMapper.map(row.eventId, row.tenantId, row.traceId, row.caseId, row.amount, row.occurredAt.toInstant().toString());
                rabbit.convertAndSend("insight.events.v1", event);
                jdbc.update("UPDATE after_sale_outbox SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP(6),last_error=NULL WHERE event_id=? AND status='PENDING'", row.eventId);
            } catch (Exception exception) {
                jdbc.update("UPDATE after_sale_outbox SET attempts=attempts+1,next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 MINUTE),last_error=? WHERE event_id=? AND status='PENDING'", safeMessage(exception), row.eventId);
            }
        }
    }

    private static String safeMessage(Exception exception) { String value = exception.getClass().getSimpleName(); return value.length() > 256 ? value.substring(0, 256) : value; }
    private static final class Row {
        private final String eventId; private final long caseId; private final String tenantId; private final String traceId; private final Timestamp occurredAt; private final BigDecimal amount;
        private Row(String eventId, long caseId, String tenantId, String traceId, Timestamp occurredAt, BigDecimal amount) { this.eventId = eventId; this.caseId = caseId; this.tenantId = tenantId; this.traceId = traceId; this.occurredAt = occurredAt; this.amount = amount; }
    }
}
