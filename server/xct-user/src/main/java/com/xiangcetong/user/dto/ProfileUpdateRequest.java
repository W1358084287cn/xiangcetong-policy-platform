package com.xiangcetong.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * 村民个人信息更新（字段均可选，至少传一个；Integer 传 0 也算有效更新）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileUpdateRequest {

    /** 姓名（写 sys_user.name） */
    private String name;
    /** 手机号（写 sys_user.phone，须唯一） */
    private String phone;
    /** 身份证号（写 villager_info.id_number） */
    private String idNumber;
    /** 身份证正面图片 URL（可选，填了申报时复用） */
    private String idFront;
    /** 身份证反面图片 URL（可选，填了申报时复用） */
    private String idBack;

    private String countyName;
    private String townName;
    private String villageName;
    private String plantType;
    private String bankCard;
    private String bankName;
    private BigDecimal plantArea;

    private String subjectType;
    private Integer laborCount;
    private Integer farmYears;
    private Integer isVeteranFamily;
    private Integer isMonitorHousehold;
    private String secondaryPlantType;
    private BigDecimal irrigatedArea;
    private Integer hasLandCert;
    private String livestockType;
    private Integer livestockCount;
    private Integer hasManureFacility;
    private Integer needMachinery;
    private String machineryType;
    private Integer hasGrainInsurance;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    public String getIdFront() { return idFront; }
    public void setIdFront(String idFront) { this.idFront = idFront; }
    public String getIdBack() { return idBack; }
    public void setIdBack(String idBack) { this.idBack = idBack; }

    public String getCountyName() { return countyName; }
    public void setCountyName(String countyName) { this.countyName = countyName; }
    public String getTownName() { return townName; }
    public void setTownName(String townName) { this.townName = townName; }
    public String getVillageName() { return villageName; }
    public void setVillageName(String villageName) { this.villageName = villageName; }
    public String getPlantType() { return plantType; }
    public void setPlantType(String plantType) { this.plantType = plantType; }
    public String getBankCard() { return bankCard; }
    public void setBankCard(String bankCard) { this.bankCard = bankCard; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public BigDecimal getPlantArea() { return plantArea; }
    public void setPlantArea(BigDecimal plantArea) { this.plantArea = plantArea; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public Integer getLaborCount() { return laborCount; }
    public void setLaborCount(Integer laborCount) { this.laborCount = laborCount; }
    public Integer getFarmYears() { return farmYears; }
    public void setFarmYears(Integer farmYears) { this.farmYears = farmYears; }
    public Integer getIsVeteranFamily() { return isVeteranFamily; }
    public void setIsVeteranFamily(Integer isVeteranFamily) { this.isVeteranFamily = isVeteranFamily; }
    public Integer getIsMonitorHousehold() { return isMonitorHousehold; }
    public void setIsMonitorHousehold(Integer isMonitorHousehold) { this.isMonitorHousehold = isMonitorHousehold; }
    public String getSecondaryPlantType() { return secondaryPlantType; }
    public void setSecondaryPlantType(String secondaryPlantType) { this.secondaryPlantType = secondaryPlantType; }
    public BigDecimal getIrrigatedArea() { return irrigatedArea; }
    public void setIrrigatedArea(BigDecimal irrigatedArea) { this.irrigatedArea = irrigatedArea; }
    public Integer getHasLandCert() { return hasLandCert; }
    public void setHasLandCert(Integer hasLandCert) { this.hasLandCert = hasLandCert; }
    public String getLivestockType() { return livestockType; }
    public void setLivestockType(String livestockType) { this.livestockType = livestockType; }
    public Integer getLivestockCount() { return livestockCount; }
    public void setLivestockCount(Integer livestockCount) { this.livestockCount = livestockCount; }
    public Integer getHasManureFacility() { return hasManureFacility; }
    public void setHasManureFacility(Integer hasManureFacility) { this.hasManureFacility = hasManureFacility; }
    public Integer getNeedMachinery() { return needMachinery; }
    public void setNeedMachinery(Integer needMachinery) { this.needMachinery = needMachinery; }
    public String getMachineryType() { return machineryType; }
    public void setMachineryType(String machineryType) { this.machineryType = machineryType; }
    public Integer getHasGrainInsurance() { return hasGrainInsurance; }
    public void setHasGrainInsurance(Integer hasGrainInsurance) { this.hasGrainInsurance = hasGrainInsurance; }

    /** 是否至少一个字段有值（含 Integer 的 0） */
    public boolean hasAnyField() {
        return name != null || phone != null || idNumber != null || idFront != null || idBack != null
                || countyName != null || townName != null || villageName != null
                || plantType != null || bankCard != null || bankName != null || plantArea != null
                || subjectType != null || laborCount != null || farmYears != null
                || isVeteranFamily != null || isMonitorHousehold != null
                || secondaryPlantType != null || irrigatedArea != null || hasLandCert != null
                || livestockType != null || livestockCount != null
                || hasManureFacility != null || needMachinery != null
                || machineryType != null || hasGrainInsurance != null;
    }
}