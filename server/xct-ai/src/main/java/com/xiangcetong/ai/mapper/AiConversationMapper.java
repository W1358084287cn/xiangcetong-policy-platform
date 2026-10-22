package com.xiangcetong.ai.mapper;

import com.xiangcetong.ai.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 管家对话会话 Mapper（注解式 SQL，省 XML）。
 */
@Mapper
public interface AiConversationMapper {

    /** 建表 DDL（仅首次执行） */
    int createTableIfNotExists();

    /** 插入新对话 */
    int insert(AiConversation conv);

    /** 按用户 ID 查对话列表（置顶优先，更新时间倒序） */
    List<AiConversation> listByUserId(@Param("userId") Long userId);

    /** 根据 ID 查单条（带用户校验） */
    AiConversation selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** 更新标题 */
    int updateTitle(@Param("id") Long id, @Param("title") String title);

    /** 切换置顶状态 */
    int togglePinned(@Param("id") Long id, @Param("pinned") Integer pinned);

    /** 累加消息计数 */
    int incrementMsgCount(@Param("id") Long id);

    /** 更新最近动作类型 */
    int updateLastAction(@Param("id") Long id, @Param("lastAction") String lastAction);

    /** 删除对话（带用户校验保证安全） */
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** 级联删除该对话下所有消息 */
    int deleteMessagesByConversationId(@Param("conversationId") Long conversationId);

    /** 更新对话的 updated_at（接触更新时间） */
    int touch(@Param("id") Long id);

    /** 保存申报草稿数据（JSON） */
    int saveDraftData(@Param("id") Long id, @Param("draftData") String draftData);

    /** 清除申报草稿数据 */
    int clearDraftData(@Param("id") Long id);

    /** 查询草稿数据（不带用户校验，Agent 内部使用） */
    String selectDraftData(@Param("id") Long id);
}