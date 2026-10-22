package com.xiangcetong.ai.entity;

import java.time.LocalDateTime;

/**
 * AI 对话日志实体（记录谁问了什么，什么时间）。
 */
public class AiChatLog {

    private Long id;
    private Long userId;
    private String username;
    private String phone;
    private String question;
    private String answer;
    private Integer fromKnowledge;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    /** 所属对话会话ID（NULL=旧悬浮球聊天） */
    private Long conversationId;
    /** Agent 执行动作类型 */
    private String agentAction;
    /** 结构化卡片数据 JSON */
    private String cardData;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public Integer getFromKnowledge() { return fromKnowledge; }
    public void setFromKnowledge(Integer fromKnowledge) { this.fromKnowledge = fromKnowledge; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getAgentAction() { return agentAction; }
    public void setAgentAction(String agentAction) { this.agentAction = agentAction; }
    public String getCardData() { return cardData; }
    public void setCardData(String cardData) { this.cardData = cardData; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}