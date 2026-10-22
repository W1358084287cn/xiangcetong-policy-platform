package com.xiangcetong.ai.dto;

import java.time.LocalDateTime;

/**
 * AI 对话用户分组视图（管理端列表展示用，不暴露具体聊天内容）。
 * 按 user_id 分组聚合：哪个用户、聊了多少次、最近一条什么时候。
 */
public class AiChatUserVO {

    private Long userId;
    private String phone;
    private String name;
    private int chatCount;
    private LocalDateTime lastChatTime;
    private long totalTokens;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getChatCount() { return chatCount; }
    public void setChatCount(int chatCount) { this.chatCount = chatCount; }
    public LocalDateTime getLastChatTime() { return lastChatTime; }
    public void setLastChatTime(LocalDateTime lastChatTime) { this.lastChatTime = lastChatTime; }
    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }
}