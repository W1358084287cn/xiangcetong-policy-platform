package com.xiangcetong.apply.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对应表 subsidy_apply。
 */
public class SubsidyApply {

    private Long id;
    private Long userId;
    private Long policyId;
    private String materialUrls;
    /** 结构化材料填写 JSON */
    private String materialData;
    private String status;
    private String villageCode;
    private String townCode;
    private Long firstBy;
    private String firstOpinion;
    private LocalDateTime firstAt;
    private Long finalBy;
    private String finalOpinion;
    private LocalDateTime finalAt;
    /** 发放金额 */
    private BigDecimal disburseAmount;
    /** 发放人（sys_user.id） */
    private Long disburseBy;
    /** 发放时间 */
    private LocalDateTime disburseAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 联表字段 */
    private String policyTitle;
    /** 政策类别（联表 subsidy_policy.category） */
    private String category;
    private String applicantName;
    private String applicantPhone;

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

    public String getMaterialUrls() {
        return materialUrls;
    }

    public void setMaterialUrls(String materialUrls) {
        this.materialUrls = materialUrls;
    }

    public String getMaterialData() {
        return materialData;
    }

    public void setMaterialData(String materialData) {
        this.materialData = materialData;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVillageCode() {
        return villageCode;
    }

    public void setVillageCode(String villageCode) {
        this.villageCode = villageCode;
    }

    public String getTownCode() {
        return townCode;
    }

    public void setTownCode(String townCode) {
        this.townCode = townCode;
    }

    public Long getFirstBy() {
        return firstBy;
    }

    public void setFirstBy(Long firstBy) {
        this.firstBy = firstBy;
    }

    public String getFirstOpinion() {
        return firstOpinion;
    }

    public void setFirstOpinion(String firstOpinion) {
        this.firstOpinion = firstOpinion;
    }

    public LocalDateTime getFirstAt() {
        return firstAt;
    }

    public void setFirstAt(LocalDateTime firstAt) {
        this.firstAt = firstAt;
    }

    public Long getFinalBy() {
        return finalBy;
    }

    public void setFinalBy(Long finalBy) {
        this.finalBy = finalBy;
    }

    public String getFinalOpinion() {
        return finalOpinion;
    }

    public void setFinalOpinion(String finalOpinion) {
        this.finalOpinion = finalOpinion;
    }

    public LocalDateTime getFinalAt() {
        return finalAt;
    }

    public void setFinalAt(LocalDateTime finalAt) {
        this.finalAt = finalAt;
    }

    public BigDecimal getDisburseAmount() {
        return disburseAmount;
    }

    public void setDisburseAmount(BigDecimal disburseAmount) {
        this.disburseAmount = disburseAmount;
    }

    public Long getDisburseBy() {
        return disburseBy;
    }

    public void setDisburseBy(Long disburseBy) {
        this.disburseBy = disburseBy;
    }

    public LocalDateTime getDisburseAt() {
        return disburseAt;
    }

    public void setDisburseAt(LocalDateTime disburseAt) {
        this.disburseAt = disburseAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPolicyTitle() {
        return policyTitle;
    }

    public void setPolicyTitle(String policyTitle) {
        this.policyTitle = policyTitle;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }

    public String getApplicantPhone() {
        return applicantPhone;
    }

    public void setApplicantPhone(String applicantPhone) {
        this.applicantPhone = applicantPhone;
    }
}