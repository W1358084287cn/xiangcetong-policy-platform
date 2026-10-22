package com.xiangcetong.policy.mapper;

import com.xiangcetong.policy.entity.SubsidyPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface SubsidyPolicyMapper {

    SubsidyPolicy findById(@Param("id") Long id);

    long count(@Param("status") String status);

    List<SubsidyPolicy> list(@Param("status") String status,
                             @Param("offset") int offset,
                             @Param("limit") int limit);

    /** 上架且有效期内的候选（不按村民条件过滤，供 includeNone 兜底） */
    List<SubsidyPolicy> listMatchCandidates(@Param("category") String category);

    /**
     * 按村民画像用多条件 WHERE 筛选「适合」的政策。
     * 政策某条件为空/未开启 → 视为不限；有值则必须与村民字段满足。
     */
    List<SubsidyPolicy> listMatchByProfile(
            @Param("category") String category,
            @Param("plantCode") String plantCode,
            @Param("plantFilter") String plantFilter,
            @Param("countyName") String countyName,
            @Param("townName") String townName,
            @Param("villageName") String villageName,
            @Param("townFilter") String townFilter,
            @Param("plantArea") BigDecimal plantArea,
            @Param("hasBankCard") int hasBankCard,
            @Param("subjectType") String subjectType,
            @Param("isVeteranFamily") int isVeteranFamily,
            @Param("isMonitorHousehold") int isMonitorHousehold,
            @Param("livestockCount") Integer livestockCount,
            @Param("hasManureFacility") int hasManureFacility,
            @Param("needMachinery") int needMachinery,
            @Param("hasGrainInsurance") int hasGrainInsurance,
            @Param("hasLandCert") int hasLandCert
    );

    /** 判断单条政策是否被当前村民条件命中（详情页 myMatch） */
    int countMatchOne(
            @Param("policyId") Long policyId,
            @Param("plantCode") String plantCode,
            @Param("countyName") String countyName,
            @Param("townName") String townName,
            @Param("villageName") String villageName,
            @Param("plantArea") BigDecimal plantArea,
            @Param("hasBankCard") int hasBankCard,
            @Param("subjectType") String subjectType,
            @Param("isVeteranFamily") int isVeteranFamily,
            @Param("isMonitorHousehold") int isMonitorHousehold,
            @Param("livestockCount") Integer livestockCount,
            @Param("hasManureFacility") int hasManureFacility,
            @Param("needMachinery") int needMachinery,
            @Param("hasGrainInsurance") int hasGrainInsurance,
            @Param("hasLandCert") int hasLandCert
    );

    int insert(SubsidyPolicy policy);

    int update(SubsidyPolicy policy);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 模糊搜索：关键词匹配标题或正文 */
    long countSearch(@Param("keyword") String keyword);

    List<SubsidyPolicy> search(@Param("keyword") String keyword,
                               @Param("offset") int offset,
                               @Param("limit") int limit);
}