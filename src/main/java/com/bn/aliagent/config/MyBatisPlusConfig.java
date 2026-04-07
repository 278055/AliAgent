package com.bn.aliagent.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置类
 *
 * 指定 Mapper 接口所在的包路径，让 Spring 自动扫描并注册到 MyBatis 容器中
 */
@Configuration
@MapperScan("com.bn.aliagent.mapper")
public class MyBatisPlusConfig {
}
