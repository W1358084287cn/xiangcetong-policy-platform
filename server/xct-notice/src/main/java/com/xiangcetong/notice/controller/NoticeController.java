package com.xiangcetong.notice.controller;

import com.xiangcetong.common.ApiResponse;
import com.xiangcetong.notice.service.NoticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 通知公告 H5 接口。
 * 登录拦截由全局 AuthInterceptor 保证（/api/notice/active 走默认须登录）；
 * 任何已登录角色均可访问。
 */
@RestController
@RequestMapping("/api/notice")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** 首页滚动字幕：返回 [{id,title,content}] */
    @GetMapping("/active")
    public ApiResponse<List<Map<String, Object>>> active() {
        return ApiResponse.ok(noticeService.active());
    }
}
