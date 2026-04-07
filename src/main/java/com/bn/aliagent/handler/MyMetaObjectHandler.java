package com.bn.aliagent.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器
 *
 * 在执行 INSERT 操作时自动填充 createdAt / updatedAt
 * 在执行 UPDATE 操作时自动更新 updatedAt
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * INSERT 操作时的自动填充逻辑
     *
     * @param metaObject MyBatis-Plus 传入的实体元对象，包含字段映射信息
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 如果实体中 createdAt 字段为空，则填充为当前时间
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        // 如果实体中 updatedAt 字段为空，则填充为当前时间
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * UPDATE 操作时的自动填充逻辑
     *
     * @param metaObject MyBatis-Plus 传入的实体元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 每次更新时将 updatedAt 刷新为当前时间
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
