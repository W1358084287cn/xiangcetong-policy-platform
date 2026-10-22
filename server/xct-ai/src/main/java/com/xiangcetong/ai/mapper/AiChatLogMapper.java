package com.xiangcetong.ai.mapper;

import com.xiangcetong.ai.dto.AiChatUserVO;
import com.xiangcetong.ai.entity.AiChatLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 对话日志 Mapper。
 */
@Mapper
public interface AiChatLogMapper {

    int insert(AiChatLog log);

    /** 已废弃——改用分组查询 {@link #listDistinctUsers} + {@link #listByUserId} */
    @Deprecated
    List<AiChatLog> list(@Param("offset") int offset, @Param("limit") int limit);

    /** 已废弃 */
    @Deprecated
    long count();

    /**
     * 按用户分组查询—管理端列表页（不暴露具体聊天内容）。
     * @param phone 手机号模糊搜索，传空字符串或不传则查全部
     */
    List<AiChatUserVO> listDistinctUsers(@Param("phone") String phone,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /** 去重统计有对话记录的用户数（支持手机号搜索） */
    long countDistinctUsers(@Param("phone") String phone);

    /** 查询某个用户的具体对话记录 */
    List<AiChatLog> listByUserId(@Param("userId") Long userId,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    /** 某个用户的对话总数 */
    long countByUserId(@Param("userId") Long userId);

    /** 回填历史记录中缺失的手机号（从 sys_user 表取，仅执行一次） */
    int backfillPhone();

    /** 回填后更新 username（与 sys_user.name 同步） */
    int backfillUsername();

    /** 查询某会话下的消息列表（分页，按时间正序） */
    List<AiChatLog> listByConversationId(@Param("conversationId") Long conversationId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /** 某会话下消息总数 */
    long countByConversationId(@Param("conversationId") Long conversationId);

    /** 删除某会话下所有消息 */
    int deleteByConversationId(@Param("conversationId") Long conversationId);
}