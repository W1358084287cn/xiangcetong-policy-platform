package com.xiangcetong.ai.dto;

import java.util.Map;

/**
 * Agent 对话响应。
 */
public class AgentResponse {

    /** 对话ID */
    private Long conversationId;
    /** 消息ID */
    private Long messageId;
    /** 执行的动作类型 */
    private String agentAction;
    /** AI 文本回复 */
    private String answer;
    /** 结构化卡片数据 */
    private Map<String, Object> data;
    /** Token 消耗 */
    private int totalTokens;

    public static AgentResponse of(Long conversationId, Long messageId, AgentAction action,
                                   String answer, Map<String, Object> data, int totalTokens) {
        AgentResponse r = new AgentResponse();
        r.conversationId = conversationId;
        r.messageId = messageId;
        r.agentAction = action.name();
        r.answer = answer;
        r.data = data;
        r.totalTokens = ***;
        return r;
    }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public String getAgentAction() { return agentAction; }
    public void setAgentAction(String agentAction) { this.agentAction = agentAction; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
}