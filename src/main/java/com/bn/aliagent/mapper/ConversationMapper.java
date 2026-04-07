package com.bn.aliagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bn.aliagent.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * Conversation（会话）表的数据访问层
 *
 * 继承 MyBatis-Plus 的 BaseMapper，获得通用的 CRUD 能力
 * 无需编写 XML 或注解 SQL，基本查询已全部自动提供
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
