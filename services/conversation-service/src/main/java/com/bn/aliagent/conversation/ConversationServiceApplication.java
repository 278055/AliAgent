package com.bn.aliagent.conversation;

import com.bn.platform.security.ServiceJwtSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(ServiceJwtSecurityConfiguration.class)
public class ConversationServiceApplication {
    public static void main(String[] args) { SpringApplication.run(ConversationServiceApplication.class, args); }
}
