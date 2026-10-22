package com.xiangcetong.ai.dto;

/**
 * Agent 对话请求。
 */
public class AgentRequest {

    /** 对话ID（null=自动创建新对话） */
    private Long conversationId;
    /** 用户消息 */
    private String message;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}