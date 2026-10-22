package com.xiangcetong.user.controller;

import com.xiangcetong.common.ApiResponse;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.user.dto.CreateUserRequest;
import com.xiangcetong.user.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端用户管理（SYS_ADMIN）— 列表查看、新增、删除。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.ok(adminUserService.list(role, phone, pageNum, pageSize));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(adminUserService.create(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminUserService.delete(id);
        return ApiResponse.ok(null);
    }
}