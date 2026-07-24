package com.bn.aliagent.conversation.config;

import com.bn.aliagent.conversation.core.OrderOwnershipVerifier;
import com.bn.aliagent.conversation.core.TrustedMallOrderOwnershipVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("database")
public class ConversationBusinessContextConfiguration {
    @Bean
    OrderOwnershipVerifier orderOwnershipVerifier(@Value("${conversation.mall.base-url}") String baseUrl,
                                                   @Value("${conversation.mall.service-jwt}") String serviceJwt) {
        return new TrustedMallOrderOwnershipVerifier(baseUrl, serviceJwt, 3000);
    }
}
