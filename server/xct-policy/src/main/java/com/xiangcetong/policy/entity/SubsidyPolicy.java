package com.xiangcetong.policy.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对应表 subsidy_policy（含结构化匹配字段与材料/咨询扩展列）。
 */
public class SubsidyPolicy {

    private Long id;
    private String title;
    private String content;
    private String conditions;
    private String standard;
    private String consultPhone;
    private String status;
    /** 政策类别：GRAIN/MACHINERY/LIVESTOCK/GENERAL/OTHER */
    private String category;
    /** JSON 数组字符串，如 ["RICE","TEA"] */
    private String targetPlantTypes;
    private BigDecimal minArea;
    private Integer requireBankCard;
    /** JSON：{"counties":[],"towns":[],"villages":[]} */
    private String regionCodes;
    /** JSON 数组字符串：适用经营主体；空=不限 */
    private String targetSubjectTypes;
    /** 是否要求退役军人家庭 0/1 */
    private Integer requireVeteran;
    /** 是否要求脱贫监测户 0/1 */
    private Integer requireMonitor;
    /** 最低存栏；空=不限 */
    private Integer minLivestock;
    /** 是否要求粪污设施 0/1 */
    private Integer requireManureFacility;
    /** 是否要求购机/报废意向 0/1 */
    private Integer requireMachineryIntent;
    /** 是否要求已参保粮食保险 0/1 */
    private Integer requireGrainInsurance;
    /** 是否要求土地承包证 0/1 */
    private Integer requireLandCert;
    private LocalDate validFrom;
    private LocalDate validTo;
    /** 来源单位（展示用） */
    private String sourceOrg;
    /** 补贴额度说明文案 */
    private String subsidyAmountText;
    /** 申报截止说明文案 */
    private String deadlineText;
    /** 咨询方式：JSON 对象或纯文本 */
    private String consultInfo;
    /** 申领条件条目列表 JSON 数组 */
    private String applyConditions;
    /** 申报材料表单 Schema JSON */
    private String materialSchema;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 非表字段：管理端更新时是否覆盖结构化匹配列（允许置空） */
    private Boolean matchFieldsTouched;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }
    public String getStandard() { return standard; }
    public void setStandard(String standard) { this.standard = standard; }
    public String getConsultPhone() { return consultPhone; }
    public void setConsultPhone(String consultPhone) { this.consultPhone = consultPhone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTargetPlantTypes() { return targetPlantTypes; }
    public void setTargetPlantTypes(String targetPlantTypes) { this.targetPlantTypes = targetPlantTypes; }
    public BigDecimal getMinArea() { return minArea; }
    public void setMinArea(BigDecimal minArea) { this.minArea = minArea; }
    public Integer getRequireBankCard() { return requireBankCard; }
    public void setRequireBankCard(Integer requireBankCard) { this.requireBankCard = requireBankCard; }
    public String getRegionCodes() { return regionCodes; }
    public void setRegionCodes(String regionCodes) { this.regionCodes = regionCodes; }
    public String getTargetSubjectTypes() { return targetSubjectTypes; }
    public void setTargetSubjectTypes(String targetSubjectTypes) { this.targetSubjectTypes = targetSubjectTypes; }
    public Integer getRequireVeteran() { return requireVeteran; }
    public void setRequireVeteran(Integer requireVeteran) { this.requireVeteran = requireVeteran; }
    public Integer getRequireMonitor() { return requireMonitor; }
    public void setRequireMonitor(Integer requireMonitor) { this.requireMonitor = requireMonitor; }
    public Integer getMinLivestock() { return minLivestock; }
    public void setMinLivestock(Integer minLivestock) { this.minLivestock = minLivestock; }
    public Integer getRequireManureFacility() { return requireManureFacility; }
    public void setRequireManureFacility(Integer requireManureFacility) { this.requireManureFacility = requireManureFacility; }
    public Integer getRequireMachineryIntent() { return requireMachineryIntent; }
    public void setRequireMachineryIntent(Integer requireMachineryIntent) { this.requireMachineryIntent = requireMachineryIntent; }
    public Integer getRequireGrainInsurance() { return requireGrainInsurance; }
    public void setRequireGrainInsurance(Integer requireGrainInsurance) { this.requireGrainInsurance = requireGrainInsurance; }
    public Integer getRequireLandCert() { return requireLandCert; }
    public void setRequireLandCert(Integer requireLandCert) { this.requireLandCert = requireLandCert; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public String getSourceOrg() { return sourceOrg; }
    public void setSourceOrg(String sourceOrg) { this.sourceOrg = sourceOrg; }
    public String getSubsidyAmountText() { return subsidyAmountText; }
    public void setSubsidyAmountText(String subsidyAmountText) { this.subsidyAmountText = subsidyAmountText; }
    public String getDeadlineText() { return deadlineText; }
    public void setDeadlineText(String deadlineText) { this.deadlineText = deadlineText; }
    public String getConsultInfo() { return consultInfo; }
    public void setConsultInfo(String consultInfo) { this.consultInfo = consultInfo; }
    public String getApplyConditions() { return applyConditions; }
    public void setApplyConditions(String applyConditions) { this.applyConditions = applyConditions; }
    public String getMaterialSchema() { return materialSchema; }
    public void setMaterialSchema(String materialSchema) { this.materialSchema = materialSchema; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getMatchFieldsTouched() { return matchFieldsTouched; }
    public void setMatchFieldsTouched(Boolean matchFieldsTouched) { this.matchFieldsTouched = matchFieldsTouched; }
}
