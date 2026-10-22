package com.xiangcetong.apply.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 镇级发放请求：发放金额 + 选填发放时间。
 */
public class DisburseRequest {

    private BigDecimal amount;
    /** 发放时间（选填，若未填则取当前时间） */
    private LocalDateTime disburseAt;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getDisburseAt() {
        return disburseAt;
    }

    public void setDisburseAt(LocalDateTime disburseAt) {
        this.disburseAt = disburseAt;
    }
}