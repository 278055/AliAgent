package com.bn.aliagent.insight;

import com.bn.platform.security.ServiceJwtSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(ServiceJwtSecurityConfiguration.class)
public class InsightServiceApplication {
    public static void main(String[] args) { SpringApplication.run(InsightServiceApplication.class, args); }
}
