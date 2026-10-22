package com.xiangcetong.user.controller;

import com.xiangcetong.common.ApiResponse;
import com.xiangcetong.user.dto.ProfileUpdateRequest;
import com.xiangcetong.user.service.VillagerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 村民个人信息接口（我的收藏已归属 policy 模块，见 VillagerFavoriteController，
 * 避免 user 与 policy 模块循环依赖）。
 */
@RestController
@RequestMapping("/api/villager")
public class VillagerController {

    private final VillagerService villagerService;

    public VillagerController(VillagerService villagerService) {
        this.villagerService = villagerService;
    }

    /**
     * 读取当前登录村民的个人资料（银行卡号会脱敏展示）。
     */
    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> getProfile() {
        return ApiResponse.ok(villagerService.getProfile());
    }

    /**
     * 更新村民资料（县乡村、种养、开户行、银行卡、面积等）；
     * 村名变更时会同步账号上的 village_code。
     */
    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody ProfileUpdateRequest request) {
        return ApiResponse.ok(villagerService.updateProfile(request));
    }
}
