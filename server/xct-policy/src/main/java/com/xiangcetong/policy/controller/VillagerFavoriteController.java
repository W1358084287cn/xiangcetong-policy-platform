package com.xiangcetong.policy.controller;

import com.xiangcetong.common.ApiResponse;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.policy.service.PolicyFavoriteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 村民收藏列表接口。
 * 收藏是政策域能力，故归 policy 模块；路径保持 /api/villager/favorites 不变，前端零改动。
 */
@RestController
@RequestMapping("/api/villager")
public class VillagerFavoriteController {

    private final PolicyFavoriteService policyFavoriteService;

    public VillagerFavoriteController(PolicyFavoriteService policyFavoriteService) {
        this.policyFavoriteService = policyFavoriteService;
    }

    /** 我的收藏政策（仅 ONLINE） */
    @GetMapping("/favorites")
    public ApiResponse<PageResult<Map<String, Object>>> favorites(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.ok(policyFavoriteService.listFavorites(pageNum, pageSize));
    }
}
