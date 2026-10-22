package com.xiangcetong.notice.controller;

import com.xiangcetong.common.ApiResponse;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.notice.dto.NoticeSaveRequest;
import com.xiangcetong.notice.service.NoticeService;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 管理端通知公告 CRUD（SYS_ADMIN）。
 * 角色校验在 NoticeService 内通过 UserContext.requireRole 实现，
 * 与现有 /api/admin/** 控制器保持一致。
 */
@RestController
@RequestMapping("/api/admin/notices")
public class AdminNoticeController {

    private final NoticeService noticeService;

    public AdminNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(noticeService.adminList(page, size));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody NoticeSaveRequest request) {
        return ApiResponse.ok(noticeService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @RequestBody NoticeSaveRequest request) {
        return ApiResponse.ok(noticeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        return ApiResponse.ok(noticeService.delete(id));
    }
}
