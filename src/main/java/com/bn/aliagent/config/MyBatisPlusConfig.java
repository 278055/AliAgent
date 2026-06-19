package com.bn.aliagent.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.bn.aliagent.mapper")
public class MyBatisPlusConfig {
    // @TableLogic 注解在实体类中已足够，MyBatis-Plus 会自动识别并处理逻辑删除
    // 无需额外配置 LogicDeleteInnerInterceptor
}