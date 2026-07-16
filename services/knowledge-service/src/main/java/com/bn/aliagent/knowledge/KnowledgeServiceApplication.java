package com.bn.aliagent.knowledge;

import com.bn.platform.security.ServiceJwtSecurityConfiguration;
import com.bn.platform.security.ServiceJwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@SpringBootApplication
@EnableRabbit
@Import({ServiceJwtSecurityConfiguration.class, KnowledgeServiceApplication.FilterConfiguration.class})
public class KnowledgeServiceApplication {
    public static void main(String[] args) { SpringApplication.run(KnowledgeServiceApplication.class, args); }

    static class FilterConfiguration {
        @Bean
        FilterRegistrationBean<ServiceJwtAuthenticationFilter> serviceJwtAuthenticationFilterRegistration(
                ServiceJwtAuthenticationFilter filter) {
            FilterRegistrationBean<ServiceJwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(1);
            return registration;
        }
    }
}
