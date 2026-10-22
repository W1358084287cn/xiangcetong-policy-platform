package com.xiangcetong.apply.controller;

import com.xiangcetong.apply.dto.ApplyDraftRequest;
import com.xiangcetong.apply.dto.ApplySubmitRequest;
import com.xiangcetong.apply.service.ApplyService;
import com.xiangcetong.common.ApiResponse;
import com.xiangcetong.common.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 村民端补贴申请接口（直提 + 草稿保存/提交）。
 */
@RestController
@RequestMapping("/api/apply")
public class ApplyController {

    private final ApplyService applyService;

    public ApplyController(ApplyService applyService) {
        this.applyService = applyService;
    }

    /**
     * 一次性直接提交申请（兼容旧流程，不经草稿）。
     * 材料齐全后状态直接变为待村级初审。
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> submit(@RequestBody ApplySubmitRequest request) {
        return ApiResponse.ok(applyService.submit(request));
    }

    /** 保存/更新草稿（同用户同政策一份 DRAFT） */
    @PostMapping("/draft")
    public ApiResponse<Map<String, Object>> saveDraft(@RequestBody ApplyDraftRequest request) {
        return ApiResponse.ok(applyService.saveDraft(request));
    }

    /**
     * 查询某政策下当前村民的草稿；
     * 无草稿时仍可能带回 suggestedPrefill，方便表单预填。
     */
    @GetMapping("/draft")
    public ApiResponse<Map<String, Object>> getDraft(@RequestParam Long policyId) {
        return ApiResponse.ok(applyService.getDraft(policyId));
    }

    /** 草稿提交审核：DRAFT → PENDING_VILLAGE */
    @PostMapping("/{id}/submit-draft")
    public ApiResponse<Map<String, Object>> submitDraft(
            @PathVariable Long id,
            @RequestBody(required = false) ApplyDraftRequest body) {
        return ApiResponse.ok(applyService.submitDraft(id, body));
    }

    /**
     * 我的申请列表（含草稿与各审批状态），支持按 status 过滤分页。
     */
    @GetMapping("/mine")
    public ApiResponse<PageResult<Map<String, Object>>> mine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.ok(applyService.mine(status, pageNum, pageSize));
    }

    /**
     * 村民查看自己某条申请详情；非本人不可见。
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(applyService.detailForVillager(id));
    }
}
