package com.xiangcetong.notice.controller;

import com.xiangcetong.notice.service.NoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NoticeController 单测（MockMvc standalone + mock Service，不连库）。
 * 覆盖：H5 滚动字幕 active 接口透传。
 */
class NoticeControllerTest {

    private NoticeService noticeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        noticeService = mock(NoticeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NoticeController(noticeService)).build();
    }

    @Test
    void activeReturnsOnlineNoticeList() throws Exception {
        when(noticeService.active()).thenReturn(List.of(
                Map.of("id", 1, "title", "惠农补贴申领通知", "content", "请于本月底前提交材料"),
                Map.of("id", 2, "title", "系统维护公告", "content", "周日 0-6 点停机")));

        mockMvc.perform(get("/api/notice/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].title").value("惠农补贴申领通知"))
                .andExpect(jsonPath("$.data[1].title").value("系统维护公告"));
    }

    @Test
    void activeReturnsEmptyListWhenNoOnlineNotice() throws Exception {
        when(noticeService.active()).thenReturn(List.of());

        mockMvc.perform(get("/api/notice/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
