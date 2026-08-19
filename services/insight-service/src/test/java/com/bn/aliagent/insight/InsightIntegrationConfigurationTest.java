package com.bn.aliagent.insight;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

class InsightIntegrationConfigurationTest {
    @Test
    void registersJsonConverterForP9EventEnvelopes() {
        assertInstanceOf(Jackson2JsonMessageConverter.class,
                new InsightIntegrationConfiguration().insightMessageConverter());
    }
}
