package com.xiangcetong.user.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对应表 villager_info：村民扩展资料（含精细画像字段，均可空）。
 */
public class VillagerInfo {

    private Long id;
    private Long userId;
    /** 身份证号，实名核验及申报材料预填用 */
    private String idNumber;
    /** 身份证正面图片 URL，申报材料预填复用 */
    private String idFront;
    /** 身份证反面图片 URL，申报材料预填复用 */
    private String idBack;
    private String countyName;
    private String townName;
    private String villageName;
    private String plantType;
    private String bankCard;
    /** 开户行名称，申报材料预填用 */
    private String bankName;
    /** 种养面积（亩），用于政策匹配面积维度 */
    private BigDecimal plantArea;

    /** 经营主体：FARMER/FAMILY_FARM/COOP/ENTERPRISE */
    private String subjectType;
    /** 家庭劳动力人数 */
    private Integer laborCount;
    /** 从业年限 */
    private Integer farmYears;
    /** 是否退役军人家庭 0/1 */
    private Integer isVeteranFamily;
    /** 是否脱贫监测户 0/1 */
    private Integer isMonitorHousehold;
    /** 兼营种养类型 */
    private String secondaryPlantType;
    /** 水浇地面积（亩） */
    private BigDecimal irrigatedArea;
    /** 是否有土地承包经营权证 0/1 */
    private Integer hasLandCert;
    /** 养殖细分：生猪/肉牛等 */
    private String livestockType;
    /** 存栏数量 */
    private Integer livestockCount;
    /** 是否有粪污处理设施 0/1 */
    private Integer hasManureFacility;
    /** 是否有购机或报废更新意向 0/1 */
    private Integer needMachinery;
    /** 意向农机类型 */
    private String machineryType;
    /** 是否已参保粮食保险 0/1 */
    private Integer hasGrainInsurance;

    private LocalDateTime updatedAt;

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

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public String getIdFront() {
        return idFront;
    }

    public void setIdFront(String idFront) {
        this.idFront = idFront;
    }

    public String getIdBack() {
        return idBack;
    }

    public void setIdBack(String idBack) {
        this.idBack = idBack;
    }

    public String getCountyName() {
        return countyName;
    }

    public void setCountyName(String countyName) {
        this.countyName = countyName;
    }

    public String getTownName() {
        return townName;
    }

    public void setTownName(String townName) {
        this.townName = townName;
    }

    public String getVillageName() {
        return villageName;
    }

    public void setVillageName(String villageName) {
        this.villageName = villageName;
    }

    public String getPlantType() {
        return plantType;
    }

    public void setPlantType(String plantType) {
        this.plantType = plantType;
    }

    public String getBankCard() {
        return bankCard;
    }

    public void setBankCard(String bankCard) {
        this.bankCard = bankCard;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public BigDecimal getPlantArea() {
        return plantArea;
    }

    public void setPlantArea(BigDecimal plantArea) {
        this.plantArea = plantArea;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public Integer getLaborCount() {
        return laborCount;
    }

    public void setLaborCount(Integer laborCount) {
        this.laborCount = laborCount;
    }

    public Integer getFarmYears() {
        return farmYears;
    }

    public void setFarmYears(Integer farmYears) {
        this.farmYears = farmYears;
    }

    public Integer getIsVeteranFamily() {
        return isVeteranFamily;
    }

    public void setIsVeteranFamily(Integer isVeteranFamily) {
        this.isVeteranFamily = isVeteranFamily;
    }

    public Integer getIsMonitorHousehold() {
        return isMonitorHousehold;
    }

    public void setIsMonitorHousehold(Integer isMonitorHousehold) {
        this.isMonitorHousehold = isMonitorHousehold;
    }

    public String getSecondaryPlantType() {
        return secondaryPlantType;
    }

    public void setSecondaryPlantType(String secondaryPlantType) {
        this.secondaryPlantType = secondaryPlantType;
    }

    public BigDecimal getIrrigatedArea() {
        return irrigatedArea;
    }

    public void setIrrigatedArea(BigDecimal irrigatedArea) {
        this.irrigatedArea = irrigatedArea;
    }

    public Integer getHasLandCert() {
        return hasLandCert;
    }

    public void setHasLandCert(Integer hasLandCert) {
        this.hasLandCert = hasLandCert;
    }

    public String getLivestockType() {
        return livestockType;
    }

    public void setLivestockType(String livestockType) {
        this.livestockType = livestockType;
    }

    public Integer getLivestockCount() {
        return livestockCount;
    }

    public void setLivestockCount(Integer livestockCount) {
        this.livestockCount = livestockCount;
    }

    public Integer getHasManureFacility() {
        return hasManureFacility;
    }

    public void setHasManureFacility(Integer hasManureFacility) {
        this.hasManureFacility = hasManureFacility;
    }

    public Integer getNeedMachinery() {
        return needMachinery;
    }

    public void setNeedMachinery(Integer needMachinery) {
        this.needMachinery = needMachinery;
    }

    public String getMachineryType() {
        return machineryType;
    }

    public void setMachineryType(String machineryType) {
        this.machineryType = machineryType;
    }

    public Integer getHasGrainInsurance() {
        return hasGrainInsurance;
    }

    public void setHasGrainInsurance(Integer hasGrainInsurance) {
        this.hasGrainInsurance = hasGrainInsurance;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}