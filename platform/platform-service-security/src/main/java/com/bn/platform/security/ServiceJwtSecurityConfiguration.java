package com.bn.platform.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceJwtSecurityConfiguration {
    @Bean
    ServiceJwtAuthenticationFilter serviceJwtAuthenticationFilter(
            @Value("${SERVICE_JWT_SECRET:test-service-jwt-secret-must-be-at-least-32-bytes}") String secret,
            @Value("${spring.application.name}") String serviceName) {
        return new ServiceJwtAuthenticationFilter(new ServiceJwtSupport(secret), serviceName);
    }
}
