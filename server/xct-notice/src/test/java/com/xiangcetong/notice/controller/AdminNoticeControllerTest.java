package com.xiangcetong.notice.controller;

import com.xiangcetong.common.PageResult;
import com.xiangcetong.notice.dto.NoticeSaveRequest;
import com.xiangcetong.notice.service.NoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminNoticeController 单测（MockMvc standalone + mock Service，不连库）。
 * 覆盖：管理端 CRUD 四个接口的参数绑定与透传（角色校验在 Service 层，另有单测）。
 */
class AdminNoticeControllerTest {

    private NoticeService noticeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        noticeService = mock(NoticeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminNoticeController(noticeService)).build();
    }

    @Test
    void listPassesPageParams() throws Exception {
        when(noticeService.adminList(2, 5))
                .thenReturn(new PageResult<>(List.of(), 0L, 2, 5));

        mockMvc.perform(get("/api/admin/notices")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pageNum").value(2));

        verify(noticeService).adminList(2, 5);
    }

    @Test
    void createBindsNoticeBody() throws Exception {
        when(noticeService.create(any(NoticeSaveRequest.class)))
                .thenReturn(Map.of("id", 3));

        mockMvc.perform(post("/api/admin/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新公告\",\"content\":\"内容\",\"status\":\"ONLINE\",\"sort\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(3));

        ArgumentCaptor<NoticeSaveRequest> captor = ArgumentCaptor.forClass(NoticeSaveRequest.class);
        verify(noticeService).create(captor.capture());
        assertEquals("新公告", captor.getValue().getTitle());
        assertEquals("ONLINE", captor.getValue().getStatus());
    }

    @Test
    void updateBindsPathIdAndBody() throws Exception {
        when(noticeService.update(eq(7L), any(NoticeSaveRequest.class)))
                .thenReturn(Map.of("id", 7));

        mockMvc.perform(put("/api/admin/notices/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"改后的标题\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7));

        ArgumentCaptor<NoticeSaveRequest> captor = ArgumentCaptor.forClass(NoticeSaveRequest.class);
        verify(noticeService).update(eq(7L), captor.capture());
        assertEquals("改后的标题", captor.getValue().getTitle());
    }

    @Test
    void deletePassesPathId() throws Exception {
        when(noticeService.delete(7L)).thenReturn(Map.of("deleted", true));

        mockMvc.perform(delete("/api/admin/notices/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));

        verify(noticeService).delete(7L);
    }
}
