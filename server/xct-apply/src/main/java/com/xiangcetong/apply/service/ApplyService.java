package com.xiangcetong.apply.service;

import com.xiangcetong.apply.dto.ApplyDraftRequest;
import com.xiangcetong.apply.dto.ApplySubmitRequest;
import com.xiangcetong.common.PageResult;

import java.util.Map;

/**
 * 村民端补贴申请：直提、草稿保存 / 提交、我的列表与详情。
 * 管理端审批状态机与统计在 ApplyAuditService，两者互不依赖。
 */
public interface ApplyService {

    /** 村民提交申报 */
    Map<String, Object> submit(ApplySubmitRequest req);

    /** 保存/更新草稿（同一用户同一政策仅一份 DRAFT） */
    Map<String, Object> saveDraft(ApplyDraftRequest req);

    /** 查询某政策草稿；无则 null */
    Map<String, Object> getDraft(Long policyId);

    /** 提交草稿：校验材料齐全后 DRAFT → PENDING_VILLAGE */
    Map<String, Object> submitDraft(Long id, ApplyDraftRequest body);

    /** 我的申请列表 */
    PageResult<Map<String, Object>> mine(String status, Integer pageNum, Integer pageSize);

    /** 村民查看本人申请详情 */
    Map<String, Object> detailForVillager(Long id);
}
