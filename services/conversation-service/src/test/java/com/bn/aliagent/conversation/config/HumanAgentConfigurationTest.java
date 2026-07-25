package com.bn.aliagent.conversation.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConverter;

class HumanAgentConfigurationTest {
    @Test
    void 副驾跨服务事件使用Json而非Java对象序列化() {
        MessageConverter converter = new HumanAgentConfiguration().p7RabbitMessageConverter();

        Message message = converter.toMessage(Map.of("roles", java.util.List.of("STAFF")), new org.springframework.amqp.core.MessageProperties());

        assertTrue(message.getMessageProperties().getContentType().contains("json"));
        assertEquals(Map.of("roles", java.util.List.of("STAFF")), converter.fromMessage(message));
    }
}
