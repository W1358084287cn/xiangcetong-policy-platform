package com.xiangcetong.policy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 政策新增/编辑请求（含 v1/v2 结构化匹配字段与材料 Schema）。
 */
public class PolicySaveRequest {

    private String title;
    private String content;
    private String conditions;
    private String standard;
    private String consultPhone;
    private String status;

    /** 政策类别：GRAIN/MACHINERY/LIVESTOCK/GENERAL */
    private String category;
    /** 适用种养类型枚举数组 */
    private List<String> targetPlantTypes;
    private BigDecimal minArea;
    /** true/1 要求绑卡 */
    private Boolean requireBankCard;
    /** {"counties":[],"towns":[],"villages":[]} */
    private Map<String, List<String>> regionCodes;
    private LocalDate validFrom;
    private LocalDate validTo;

    /** 适用经营主体枚举数组；空=不限 */
    private List<String> targetSubjectTypes;
    /** 是否要求退役军人家庭 */
    private Boolean requireVeteran;
    /** 是否要求脱贫监测户 */
    private Boolean requireMonitor;
    /** 最低存栏；空=不限 */
    private Integer minLivestock;
    /** 是否要求粪污处理设施 */
    private Boolean requireManureFacility;
    /** 是否要求购机/报废意向 */
    private Boolean requireMachineryIntent;
    /** 是否要求已参保粮食保险 */
    private Boolean requireGrainInsurance;
    /** 是否要求土地承包证 */
    private Boolean requireLandCert;

    /**
     * 申报材料 Schema（对象或 null 清空）。
     * 结构见 docs/03：materials[] 或扁平 fields[]。
     */
    private Object materialSchema;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getConditions() {
        return conditions;
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    public String getStandard() {
        return standard;
    }

    public void setStandard(String standard) {
        this.standard = standard;
    }

    public String getConsultPhone() {
        return consultPhone;
    }

    public void setConsultPhone(String consultPhone) {
        this.consultPhone = consultPhone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getTargetPlantTypes() {
        return targetPlantTypes;
    }

    public void setTargetPlantTypes(List<String> targetPlantTypes) {
        this.targetPlantTypes = targetPlantTypes;
    }

    public BigDecimal getMinArea() {
        return minArea;
    }

    public void setMinArea(BigDecimal minArea) {
        this.minArea = minArea;
    }

    public Boolean getRequireBankCard() {
        return requireBankCard;
    }

    public void setRequireBankCard(Boolean requireBankCard) {
        this.requireBankCard = requireBankCard;
    }

    public Map<String, List<String>> getRegionCodes() {
        return regionCodes;
    }

    public void setRegionCodes(Map<String, List<String>> regionCodes) {
        this.regionCodes = regionCodes;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    public List<String> getTargetSubjectTypes() {
        return targetSubjectTypes;
    }

    public void setTargetSubjectTypes(List<String> targetSubjectTypes) {
        this.targetSubjectTypes = targetSubjectTypes;
    }

    public Boolean getRequireVeteran() {
        return requireVeteran;
    }

    public void setRequireVeteran(Boolean requireVeteran) {
        this.requireVeteran = requireVeteran;
    }

    public Boolean getRequireMonitor() {
        return requireMonitor;
    }

    public void setRequireMonitor(Boolean requireMonitor) {
        this.requireMonitor = requireMonitor;
    }

    public Integer getMinLivestock() {
        return minLivestock;
    }

    public void setMinLivestock(Integer minLivestock) {
        this.minLivestock = minLivestock;
    }

    public Boolean getRequireManureFacility() {
        return requireManureFacility;
    }

    public void setRequireManureFacility(Boolean requireManureFacility) {
        this.requireManureFacility = requireManureFacility;
    }

    public Boolean getRequireMachineryIntent() {
        return requireMachineryIntent;
    }

    public void setRequireMachineryIntent(Boolean requireMachineryIntent) {
        this.requireMachineryIntent = requireMachineryIntent;
    }

    public Boolean getRequireGrainInsurance() {
        return requireGrainInsurance;
    }

    public void setRequireGrainInsurance(Boolean requireGrainInsurance) {
        this.requireGrainInsurance = requireGrainInsurance;
    }

    public Boolean getRequireLandCert() {
        return requireLandCert;
    }

    public void setRequireLandCert(Boolean requireLandCert) {
        this.requireLandCert = requireLandCert;
    }

    public Object getMaterialSchema() {
        return materialSchema;
    }

    public void setMaterialSchema(Object materialSchema) {
        this.materialSchema = materialSchema;
    }
}
