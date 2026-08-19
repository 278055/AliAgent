package com.bn.aliagent.insight.adapter;

import com.bn.aliagent.insight.intake.InsightEventEnvelope;
import com.bn.aliagent.insight.intake.InsightIntakeService;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** RabbitMQ 仅负责传输，事实写入仍由 Intake 的租户校验和 Inbox 幂等控制。 */
@Component
public final class InsightRabbitEventConsumer {
    private final BiFunction<InsightEventEnvelope, String, ?> intake;

    @Autowired
    public InsightRabbitEventConsumer(InsightIntakeService intake) {
        this(intake::accept);
    }

    InsightRabbitEventConsumer(BiFunction<InsightEventEnvelope, String, ?> intake) {
        this.intake = Objects.requireNonNull(intake);
    }

    @RabbitListener(queues = "${insight.messaging.events-queue:insight.events.v1}")
    public void consume(InsightEventEnvelope event) {
        if (event == null || event.tenantId() == null || event.tenantId().isBlank()) {
            throw new SecurityException("P9 事件缺少可信租户");
        }
        intake.apply(event, event.tenantId());
    }

    /**
     * P9 上游 Outbox 只发布契约信封的 JSON 对象；不信任消息头声明的 Java 类型，
     * 因此在消费者边界显式重建白名单字段的不可变信封。
     */
    @SuppressWarnings("unchecked")
    public void consume(Map<String, Object> raw) {
        if (raw == null) throw new IllegalArgumentException("P9 事件不能为空");
        Object payload = raw.get("payload");
        if (!(payload instanceof Map<?, ?>)) throw new IllegalArgumentException("P9 事件 payload 格式非法");
        consume(new InsightEventEnvelope(
                UUID.fromString(required(raw, "eventId")),
                required(raw, "eventType"),
                Integer.parseInt(required(raw, "eventVersion")),
                Instant.parse(required(raw, "occurredAt")),
                required(raw, "tenantId"),
                required(raw, "traceId"),
                required(raw, "producer"),
                (Map<String, Object>) payload));
    }

    private static String required(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException("P9 事件缺少 " + field);
        return String.valueOf(value);
    }
}
