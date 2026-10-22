package com.xiangcetong.ai.service;

import com.xiangcetong.ai.dto.ConversationVO;
import com.xiangcetong.ai.entity.AiChatLog;

import java.util.List;

/**
 * 对话会话 CRUD 服务。
 */
public interface ConversationService {

    /** 创建新对话 */
    ConversationVO create(Long userId);

    /** 我的对话列表 */
    List<ConversationVO> listByUser(Long userId);

    /** 对话详情（含消息分页） */
    ConversationDetail detail(Long conversationId, Long userId, int page, int size);

    /** 重命名 */
    void rename(Long conversationId, Long userId, String title);

    /** 切换置顶 */
    void togglePinned(Long conversationId, Long userId);

    /** 删除对话（级联删除消息） */
    void delete(Long conversationId, Long userId);

    /** 对话详情结果 */
    record ConversationDetail(ConversationVO conversation, List<AiChatLog> messages, long total) {}
}