package com.bn.aliagent.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class WebConfiguration {
    @Bean RequestContextLoggingFilter requestContextLoggingFilter() { return new RequestContextLoggingFilter(); }
}
