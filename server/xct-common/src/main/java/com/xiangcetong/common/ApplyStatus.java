package com.xiangcetong.common;

import java.util.List;

/**
 * 补贴申报状态机常量（与 docs/04 状态机一致）。
 * 采用 String 常量而非枚举，兼容数据库 varchar 存储与 Mapper XML 直接比较，
 * 避免改动实体字段类型与 MyBatis 映射。
 */
public final class ApplyStatus {

    /** 草稿（仅村民可见，不进审批流） */
    public static final String DRAFT = "DRAFT";
    /** 待村级初审 */
    public static final String PENDING_VILLAGE = "PENDING_VILLAGE";
    /** 待镇级终审 */
    public static final String PENDING_TOWN = "PENDING_TOWN";
    /** 待发放（镇终审通过后） */
    public static final String PENDING_DISBURSE = "PENDING_DISBURSE";
    /** 已发放 */
    public static final String DISBURSED = "DISBURSED";
    /** 已通过（历史数据兼容） */
    public static final String APPROVED = "APPROVED";
    /** 已驳回 */
    public static final String REJECTED = "REJECTED";

    /** 视为「进行中或已通过」的状态（不可重复申报 / 再开草稿），供 Mapper 参数化查询 */
    public static final List<String> ACTIVE_STATUSES = List.of(
            PENDING_VILLAGE, PENDING_TOWN, PENDING_DISBURSE, DISBURSED, APPROVED);

    private ApplyStatus() {
    }
}
