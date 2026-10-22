package com.xiangcetong.policy.entity;

import java.time.LocalDateTime;

/**
 * 对应表 policy_favorite：村民收藏政策。
 */
public class PolicyFavorite {

    private Long id;
    private Long userId;
    private Long policyId;
    private LocalDateTime createdAt;

    /** 联表：政策标题 */
    private String title;
    /** 联表：补贴标准 */
    private String standard;
    /** 联表：政策状态 */
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Long policyId) {
        this.policyId = policyId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStandard() {
        return standard;
    }

    public void setStandard(String standard) {
        this.standard = standard;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
