package com.xiangcetong.apply.mapper;

import com.xiangcetong.apply.entity.SubsidyApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 补贴申请数据访问：提交 / 审批 / 发放 / 统计。
 */
@Mapper
public interface SubsidyApplyMapper {

    int insert(SubsidyApply apply);

    SubsidyApply findById(@Param("id") Long id);

    SubsidyApply findDraftByUserPolicy(@Param("userId") Long userId, @Param("policyId") Long policyId);

    long countActiveByUserPolicy(@Param("userId") Long userId, @Param("policyId") Long policyId,
                                 @Param("activeStatuses") List<String> activeStatuses);

    int updateDraft(SubsidyApply apply);

    int updateSubmitDraft(SubsidyApply apply);

    long countMine(@Param("userId") Long userId, @Param("status") String status);

    List<SubsidyApply> listMine(@Param("userId") Long userId,
                                @Param("status") String status,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    /** 管理端列表计数：status 为单值过滤、statusIn 为 IN 列表过滤（二者其一） */
    long countAdmin(@Param("status") String status,
                    @Param("statusIn") List<String> statusIn,
                    @Param("villageCode") String villageCode,
                    @Param("townCode") String townCode);

    List<SubsidyApply> listAdmin(@Param("status") String status,
                                 @Param("statusIn") List<String> statusIn,
                                 @Param("villageCode") String villageCode,
                                 @Param("townCode") String townCode,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    int updateFirstAudit(SubsidyApply apply);

    int updateFinalAudit(SubsidyApply apply);

    /** 镇级发放：写金额 / 发放人 / 发放时间并置为已发放 */
    int updateDisburse(SubsidyApply apply);

    /** 村级统计：待初审数 + 今日 / 本月初审数 */
    Map<String, Long> countVillageStats(@Param("villageCode") String villageCode,
                                        @Param("todayStart") LocalDateTime todayStart,
                                        @Param("tomorrowStart") LocalDateTime tomorrowStart,
                                        @Param("monthStart") LocalDateTime monthStart,
                                        @Param("nextMonthStart") LocalDateTime nextMonthStart);

    /** 镇级统计：待终审数 + 今日 / 本月终审数 */
    Map<String, Long> countTownStats(@Param("townCode") String townCode,
                                     @Param("todayStart") LocalDateTime todayStart,
                                     @Param("tomorrowStart") LocalDateTime tomorrowStart,
                                     @Param("monthStart") LocalDateTime monthStart,
                                     @Param("nextMonthStart") LocalDateTime nextMonthStart);
}
