package com.xiangcetong.ai.dto;

import java.time.LocalDateTime;

/**
 * 对话列表 VO。
 */
public class ConversationVO {

    private Long id;
    private String title;
    private Integer pinned;
    private Integer msgCount;
    private String lastAction;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getPinned() { return pinned; }
    public void setPinned(Integer pinned) { this.pinned = pinned; }
    public Integer getMsgCount() { return msgCount; }
    public void setMsgCount(Integer msgCount) { this.msgCount = msgCount; }
    public String getLastAction() { return lastAction; }
    public void setLastAction(String lastAction) { this.lastAction = lastAction; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}