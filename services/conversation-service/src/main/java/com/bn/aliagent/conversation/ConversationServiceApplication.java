package com.bn.aliagent.conversation;

import com.bn.platform.security.ServiceJwtSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableRabbit
@EnableScheduling
@Import(ServiceJwtSecurityConfiguration.class)
public class ConversationServiceApplication {
    public static void main(String[] args) { SpringApplication.run(ConversationServiceApplication.class, args); }
}
