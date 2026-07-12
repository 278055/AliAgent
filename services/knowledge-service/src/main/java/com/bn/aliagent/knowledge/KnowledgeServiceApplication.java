package com.bn.aliagent.knowledge;

import com.bn.platform.security.ServiceJwtSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(ServiceJwtSecurityConfiguration.class)
public class KnowledgeServiceApplication {
    public static void main(String[] args) { SpringApplication.run(KnowledgeServiceApplication.class, args); }
}
