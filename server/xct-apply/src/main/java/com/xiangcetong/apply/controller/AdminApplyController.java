package com.xiangcetong.apply.controller;

import com.xiangcetong.apply.dto.AuditRequest;
import com.xiangcetong.apply.dto.DisburseRequest;
import com.xiangcetong.apply.service.ApplyAuditService;
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
 * 管理端申请列表与两级审批。
 * SYS_ADMIN 调审批接口会在 Service 层返回 403。
 */
@RestController
@RequestMapping("/api/admin/apply")
public class AdminApplyController {

    private final ApplyAuditService auditService;

    public AdminApplyController(ApplyAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String villageCode,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.ok(auditService.adminList(status, tab, villageCode, pageNum, pageSize));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(auditService.stats());
    }

    @PostMapping("/{id}/disburse")
    public ApiResponse<Map<String, Object>> disburse(@PathVariable Long id,
                                                     @RequestBody DisburseRequest request) {
        return ApiResponse.ok(auditService.disburse(id, request));
    }

    @PostMapping("/{id}/village-audit")
    public ApiResponse<Map<String, Object>> villageAudit(@PathVariable Long id,
                                                         @RequestBody AuditRequest request) {
        return ApiResponse.ok(auditService.villageAudit(id, request));
    }

    @PostMapping("/{id}/town-audit")
    public ApiResponse<Map<String, Object>> townAudit(@PathVariable Long id,
                                                      @RequestBody AuditRequest request) {
        return ApiResponse.ok(auditService.townAudit(id, request));
    }
}
