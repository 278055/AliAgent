package com.bn.aliagent.orchestration;

import com.bn.platform.security.ServiceJwtSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@SpringBootApplication
@EnableRabbit
@Import(ServiceJwtSecurityConfiguration.class)
public class AiOrchestrationServiceApplication {
    public static void main(String[] args) { SpringApplication.run(AiOrchestrationServiceApplication.class, args); }
}
