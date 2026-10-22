package com.xiangcetong.policy.controller;

import com.xiangcetong.common.ApiResponse;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.policy.service.PolicyFavoriteService;
import com.xiangcetong.policy.service.PolicyMatchService;
import com.xiangcetong.policy.service.PolicyService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 政策接口：公开列表 + 村民匹配（需登录）+ 详情 + 收藏。
 */
@RestController
@RequestMapping("/api/policy")
public class PolicyController {

    private final PolicyService policyService;
    private final PolicyMatchService policyMatchService;
    private final PolicyFavoriteService policyFavoriteService;

    public PolicyController(PolicyService policyService,
                            PolicyMatchService policyMatchService,
                            PolicyFavoriteService policyFavoriteService) {
        this.policyService = policyService;
        this.policyMatchService = policyMatchService;
        this.policyFavoriteService = policyFavoriteService;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.ok(policyService.publicList(status, pageNum, pageSize));
    }

    /**
     * 村民政策匹配（WRS 加权评分）。
     * 必须写在 /{id} 前面，避免被当成 id=match。
     */
    @GetMapping("/match")
    public ApiResponse<PageResult<Map<String, Object>>> match(
            @RequestParam(required = false) String plantType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String townName,
            @RequestParam(required = false) Boolean includeNone,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.ok(policyMatchService.match(
                plantType, category, townName, includeNone, pageNum, pageSize));
    }

    /** 模糊搜索：关键词匹配标题或正文，仅上架政策 */
    @GetMapping("/search")
    public ApiResponse<PageResult<Map<String, Object>>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.ok(policyService.search(keyword, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(policyService.publicDetail(id));
    }

    /** 收藏：路径为 /{id}/favorite，挂在详情旁 */
    @PostMapping("/{id}/favorite")
    public ApiResponse<Map<String, Object>> addFavorite(@PathVariable Long id) {
        return ApiResponse.ok(policyFavoriteService.addFavorite(id));
    }

    /** 取消收藏（幂等） */
    @DeleteMapping("/{id}/favorite")
    public ApiResponse<Map<String, Object>> removeFavorite(@PathVariable Long id) {
        return ApiResponse.ok(policyFavoriteService.removeFavorite(id));
    }
}