package com.macro.mall.portal.internal.read;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class InternalReadFilterConfiguration {
    @Bean
    @ConditionalOnMissingBean(ServiceIdentityVerifier.class)
    public ServiceIdentityVerifier serviceIdentityVerifier() {
        return new RejectingServiceIdentityVerifier();
    }

    @Bean
    public InternalReadAuthenticationFilter internalReadAuthenticationFilter(
            HeaderUserSnapshotResolver userSnapshotResolver, ServiceIdentityVerifier serviceIdentityVerifier) {
        return new InternalReadAuthenticationFilter(userSnapshotResolver, serviceIdentityVerifier);
    }

    @Bean
    public FilterRegistrationBean<InternalReadAuthenticationFilter> internalReadAuthenticationFilterRegistration(
            InternalReadAuthenticationFilter filter) {
        FilterRegistrationBean<InternalReadAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/internal/mall/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
