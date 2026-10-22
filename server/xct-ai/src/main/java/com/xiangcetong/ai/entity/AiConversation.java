package com.xiangcetong.ai.entity;

import java.time.LocalDateTime;

/**
 * AI 管家对话会话实体。
 */
public class AiConversation {

    private Long id;
    private Long userId;
    private String title;
    private Integer pinned;
    private Integer msgCount;
    private String lastAction;
    private String draftData;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getPinned() { return pinned; }
    public void setPinned(Integer pinned) { this.pinned = pinned; }
    public Integer getMsgCount() { return msgCount; }
    public void setMsgCount(Integer msgCount) { this.msgCount = msgCount; }
    public String getLastAction() { return lastAction; }
    public void setLastAction(String lastAction) { this.lastAction = lastAction; }
    public String getDraftData() { return draftData; }
    public void setDraftData(String draftData) { this.draftData = draftData; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}