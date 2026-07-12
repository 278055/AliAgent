package com.bn.aliagent.evaluation;

import com.bn.platform.security.ServiceJwtSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(ServiceJwtSecurityConfiguration.class)
public class EvaluationServiceApplication {
    public static void main(String[] args) { SpringApplication.run(EvaluationServiceApplication.class, args); }
}
