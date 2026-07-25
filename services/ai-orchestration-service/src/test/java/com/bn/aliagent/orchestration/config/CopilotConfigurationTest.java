package com.bn.aliagent.orchestration.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConverter;

class CopilotConfigurationTest {
    @Test
    void 副驾消费者将Json事件转换为Map() {
        MessageConverter converter = new CopilotConfiguration().p7RabbitMessageConverter();
        Map<String, Object> event = Map.of("eventVersion", 2, "tenantId", "tenant-a");

        Message message = converter.toMessage(event, new org.springframework.amqp.core.MessageProperties());

        assertEquals(event, converter.fromMessage(message));
    }
}
