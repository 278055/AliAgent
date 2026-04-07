package com.bn.aliagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bn.aliagent.entity.Conversation;
import com.bn.aliagent.mapper.ConversationMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话业务逻辑层
 *
 * 负责 conversation 表的增删改查操作
 */
@Service
public class ConversationService extends ServiceImpl<ConversationMapper, Conversation> {

    /**
     * 根据 ID 查询会话
     *
     * @param id 会话 ID
     * @return 对应的 Conversation 记录，若不存在则返回 null
     */
    public Conversation getById(String id) {
        return super.getById(id);
    }

    /**
     * 根据 ID 判断会话是否已存在
     *
     * @param id 会话 ID
     * @return 存在返回 true，不存在返回 false
     */
    public boolean existsById(String id) {
        return super.count(new QueryWrapper<Conversation>().eq("id", id)) > 0;
    }

    /**
     * 获取指定 ID 的会话，如果不存在则自动创建一个新的
     *
     * @param id 会话 ID
     * @return 存在的或新创建的 Conversation 对象
     */
    public Conversation getOrCreate(String id) {
        Conversation conversation = super.getById(id);
        if (conversation == null) {
            conversation = new Conversation();
            conversation.setId(id);
            conversation.setTitle("新对话");
            conversation.setPinned(0);
            super.save(conversation);
        }
        return conversation;
    }

    /**
     * 查询所有未删除的会话，置顶在前，其余按更新时间倒序排列
     * MP 会自动追加 WHERE deleted = 0，无需手动写
     *
     * @return 会话列表
     */
    public List<Conversation> listActive() {
        QueryWrapper<Conversation> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("pinned", "updated_at");
        return super.list(wrapper);
    }

    /**
     * 切换置顶状态
     *
     * @param id 会话 ID
     * @return 切换后的 pinned 状态
     */
    public boolean togglePinned(String id) {
        Conversation existing = super.getById(id);
        if (existing == null) return false;
        Conversation conv = new Conversation();
        conv.setId(id);
        conv.setPinned(existing.getPinned() == 1 ? 0 : 1);
        return super.updateById(conv);
    }

    /**
     * 软删除会话（MP @TableLogic 自动将 deleted 置为 1）
     *
     * @param id 会话 ID
     * @return 成功返回 true
     */
    public boolean deleteConversation(String id) {
        return super.removeById(id);
    }

    /**
     * 恢复已删除的会话（deleted 置回 0）
     *
     * @param id 会话 ID
     * @return 成功返回 true
     */
    public boolean restore(String id) {
        Conversation conv = new Conversation();
        conv.setId(id);
        conv.setDeleted(0);
        return super.updateById(conv);
    }

    /**
     * 更新会话标题
     *
     * @param id    会话 ID
     * @param title 新标题
     * @return 成功返回 true
     */
    public boolean updateTitle(String id, String title) {
        Conversation conv = new Conversation();
        conv.setId(id);
        conv.setTitle(title);
        return super.updateById(conv);
    }
}
