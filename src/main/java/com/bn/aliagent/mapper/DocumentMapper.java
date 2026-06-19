package com.bn.aliagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bn.aliagent.entity.Document;
import org.apache.ibatis.annotations.Mapper;

/**
 * Document（知识库文档）表的数据访问层
 *
 * <p>继承 MyBatis-Plus 的 BaseMapper，获得通用的 CRUD 能力。</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}
