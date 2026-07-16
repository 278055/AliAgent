package com.bn.aliagent.knowledge.ingestion;

import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("database")
public class IngestionWorker {
    private final IngestionProcessor processor;

    public IngestionWorker(IngestionProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = IngestionInfrastructureConfiguration.QUEUE)
    public void consume(IngestionTaskMessage message) {
        try (MdcScope ignored = new MdcScope(message)) {
            processor.process(message);
        }
    }

    private static final class MdcScope implements AutoCloseable {
        private MdcScope(IngestionTaskMessage message) {
            MDC.put("eventId", message.eventId());
            MDC.put("traceId", message.traceId());
            MDC.put("tenantId", message.tenantId());
        }

        @Override
        public void close() {
            MDC.remove("eventId");
            MDC.remove("traceId");
            MDC.remove("tenantId");
        }
    }
}
