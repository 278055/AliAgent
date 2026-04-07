package com.bn.aliagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AliAgent 应用启动类
 *
 * 整合 Spring Boot + Spring AI Alibaba（通义千问） + MyBatis-Plus + PostgreSQL
 * 支持 AI 智能对话、会话管理及后续的 RAG 向量检索增强能力
 */
@SpringBootApplication
public class AliAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AliAgentApplication.class, args);
    }

}
