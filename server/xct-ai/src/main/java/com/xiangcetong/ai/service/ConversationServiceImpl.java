package com.xiangcetong.ai.service;

import com.xiangcetong.ai.dto.ConversationVO;
import com.xiangcetong.ai.entity.AiChatLog;
import com.xiangcetong.ai.entity.AiConversation;
import com.xiangcetong.ai.mapper.AiChatLogMapper;
import com.xiangcetong.ai.mapper.AiConversationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话会话 CRUD 实现。
 */
@Service
public class ConversationServiceImpl implements ConversationService {

    private final AiConversationMapper convMapper;
    private final AiChatLogMapper chatLogMapper;

    public ConversationServiceImpl(AiConversationMapper convMapper, AiChatLogMapper chatLogMapper) {
        this.convMapper = convMapper;
        this.chatLogMapper = chatLogMapper;
    }

    @Override
    public ConversationVO create(Long userId) {
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setTitle("新对话");
        convMapper.insert(conv);
        return toVO(conv);
    }

    @Override
    public List<ConversationVO> listByUser(Long userId) {
        return convMapper.listByUserId(userId).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public ConversationDetail detail(Long conversationId, Long userId, int page, int size) {
        AiConversation conv = convMapper.selectByIdAndUserId(conversationId, userId);
        if (conv == null) {
            throw new IllegalArgumentException("对话不存在");
        }
        int offset = (page - 1) * size;
        List<AiChatLog> messages = chatLogMapper.listByConversationId(conversationId, offset, size);
        long total = chatLogMapper.countByConversationId(conversationId);
        return new ConversationDetail(toVO(conv), messages, total);
    }

    @Override
    public void rename(Long conversationId, Long userId, String title) {
        AiConversation conv = convMapper.selectByIdAndUserId(conversationId, userId);
        if (conv == null) {
            throw new IllegalArgumentException("对话不存在");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (title.length() > 100) {
            title = title.substring(0, 100);
        }
        convMapper.updateTitle(conversationId, title);
    }

    @Override
    public void togglePinned(Long conversationId, Long userId) {
        AiConversation conv = convMapper.selectByIdAndUserId(conversationId, userId);
        if (conv == null) {
            throw new IllegalArgumentException("对话不存在");
        }
        int newPinned = (conv.getPinned() != null && conv.getPinned() == 1) ? 0 : 1;
        convMapper.togglePinned(conversationId, newPinned);
    }

    @Override
    @Transactional
    public void delete(Long conversationId, Long userId) {
        AiConversation conv = convMapper.selectByIdAndUserId(conversationId, userId);
        if (conv == null) {
            throw new IllegalArgumentException("对话不存在");
        }
        chatLogMapper.deleteByConversationId(conversationId);
        convMapper.deleteByIdAndUserId(conversationId, userId);
    }

    private ConversationVO toVO(AiConversation conv) {
        ConversationVO vo = new ConversationVO();
        vo.setId(conv.getId());
        vo.setTitle(conv.getTitle());
        vo.setPinned(conv.getPinned());
        vo.setMsgCount(conv.getMsgCount());
        vo.setLastAction(conv.getLastAction());
        vo.setCreatedAt(conv.getCreatedAt());
        vo.setUpdatedAt(conv.getUpdatedAt());
        return vo;
    }
}