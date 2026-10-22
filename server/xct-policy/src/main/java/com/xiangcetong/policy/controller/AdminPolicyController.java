package com.xiangcetong.policy.controller;

import com.xiangcetong.common.ApiResponse;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.policy.dto.PolicySaveRequest;
import com.xiangcetong.policy.service.PolicyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端政策 CRUD（SYS_ADMIN）。
 */
@RestController
@RequestMapping("/api/admin/policy")
public class AdminPolicyController {

    private final PolicyService policyService;

    public AdminPolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.ok(policyService.adminList(status, pageNum, pageSize));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody PolicySaveRequest request) {
        return ApiResponse.ok(policyService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(policyService.adminDetail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @RequestBody PolicySaveRequest request) {
        return ApiResponse.ok(policyService.update(id, request));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> status(@PathVariable Long id,
                                                   @RequestBody Map<String, String> body) {
        return ApiResponse.ok(policyService.updateStatus(id, body == null ? null : body.get("status")));
    }

    /** 从政策表全量重建 AI 知识库 */
    @PostMapping("/rebuild-knowledge")
    public ApiResponse<Map<String, Object>> rebuildKnowledge() {
        return ApiResponse.ok(policyService.rebuildKnowledge());
    }
}