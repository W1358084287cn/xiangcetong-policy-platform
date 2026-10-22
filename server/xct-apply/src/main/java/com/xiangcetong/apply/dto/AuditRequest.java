package com.xiangcetong.apply.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 初审/终审请求：PASS 或 REJECT；村管初审时可附带拟定发放金额/时间。
 */
public class AuditRequest {

    private String action;
    private String opinion;
    /** 拟定发放金额（村管初审时选填） */
    private BigDecimal disburseAmount;
    /** 拟定发放时间（村管初审时选填） */
    private LocalDateTime disburseAt;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public BigDecimal getDisburseAmount() {
        return disburseAmount;
    }

    public void setDisburseAmount(BigDecimal disburseAmount) {
        this.disburseAmount = disburseAmount;
    }

    public LocalDateTime getDisburseAt() {
        return disburseAt;
    }

    public void setDisburseAt(LocalDateTime disburseAt) {
        this.disburseAt = disburseAt;
    }
}