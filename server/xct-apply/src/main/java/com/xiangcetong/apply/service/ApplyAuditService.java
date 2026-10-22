package com.xiangcetong.apply.service;

import com.xiangcetong.apply.dto.AuditRequest;
import com.xiangcetong.apply.dto.DisburseRequest;
import com.xiangcetong.common.PageResult;

import java.util.Map;

/**
 * 管理端申请列表、村级初审 / 镇级终审 / 发放状态机与统计。
 * 村民端提交逻辑在 ApplyService，两者互不依赖。
 */
public interface ApplyAuditService {

    /** 管理端申请列表（按角色与 tab 过滤，铁律镇管不可见未初审单据） */
    PageResult<Map<String, Object>> adminList(String status, String tab, String villageCode,
                                              Integer pageNum, Integer pageSize);

    /** 村级初审：仅 VILLAGE_ADMIN，且本村 + PENDING_VILLAGE */
    Map<String, Object> villageAudit(Long id, AuditRequest req);

    /** 镇级终审：仅 TOWN_ADMIN */
    Map<String, Object> townAudit(Long id, AuditRequest req);

    /** 镇级发放：仅 TOWN_ADMIN，本镇 + PENDING_DISBURSE / APPROVED(历史) 才可发放 */
    Map<String, Object> disburse(Long id, DisburseRequest req);

    /** 管理端统计：待审批数、今日审批数、本月审批数（仅村管/镇管） */
    Map<String, Object> stats();
}
