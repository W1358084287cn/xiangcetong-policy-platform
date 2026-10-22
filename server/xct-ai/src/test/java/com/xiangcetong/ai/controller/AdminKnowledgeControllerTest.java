package com.xiangcetong.ai.controller;

import com.xiangcetong.ai.service.KnowledgeService;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 管理端知识库控制器单元测试。
 */
@DisplayName("管理端知识库控制器")
class AdminKnowledgeControllerTest {

    private KnowledgeService knowledgeService;
    private JdbcTemplate jdbcTemplate;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        knowledgeService = mock(KnowledgeService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminKnowledgeController(knowledgeService, jdbcTemplate)).build();

        UserContext.LoginUser login = new UserContext.LoginUser();
        login.setUserId(1L);
        login.setRole("SYS_ADMIN");
        login.setPhone("13800000001");
        login.setName("管理员");
        UserContext.set(login);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Nested
    @DisplayName("GET /api/admin/knowledge 文档列表")
    class ListDocuments {

        @Test
        @DisplayName("分页查询文档列表")
        void shouldReturnPaginatedList() throws Exception {
            Map<String, Object> doc = Map.of("id", 1, "title", "惠农政策文件");
            PageResult<Map<String, Object>> pageResult = new PageResult<>(
                    List.of(doc), 1L, 1, 20);

            when(knowledgeService.list(1, 20)).thenReturn(pageResult);

            mockMvc.perform(get("/api/admin/knowledge")
                            .param("page", "1")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.total").value(1));
        }
    }

    @Nested
    @DisplayName("DELETE /api/admin/knowledge/{id} 删除文档")
    class DeleteDocument {

        @Test
        @DisplayName("删除文档成功")
        void shouldDeleteDocument() throws Exception {
            when(knowledgeService.delete(1L)).thenReturn(Map.of("deleted", true));

            mockMvc.perform(delete("/api/admin/knowledge/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            verify(knowledgeService).delete(1L);
        }
    }

    @Nested
    @DisplayName("GET /api/admin/knowledge/{id}/chunks 分块详情")
    class ChunkDetail {

        @Test
        @DisplayName("查询文档分块详情")
        void shouldReturnChunkDetail() throws Exception {
            Map<String, Object> chunk = Map.of("id", 1, "chunkIndex", 0, "chunkText", "惠农政策内容...");
            when(knowledgeService.chunkDetail(1L)).thenReturn(List.of(chunk));

            mockMvc.perform(get("/api/admin/knowledge/1/chunks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data[0].chunkIndex").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/admin/knowledge/{id}/reprocess 重新处理")
    class Reprocess {

        @Test
        @DisplayName("重新处理文档")
        void shouldReprocessDocument() throws Exception {
            when(knowledgeService.reprocess(1L)).thenReturn(Map.of("status", "PROCESSING"));

            mockMvc.perform(post("/api/admin/knowledge/1/reprocess"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            verify(knowledgeService).reprocess(1L);
        }
    }

    @Nested
    @DisplayName("POST /api/admin/knowledge/import-policies 导入政策")
    class ImportPolicies {

        @Test
        @DisplayName("从数据库导入政策到知识库")
        void shouldImportPolicies() throws Exception {
            when(knowledgeService.importPoliciesFromDb())
                    .thenReturn(Map.of("policyCount", 5, "chunkCount", 20));

            mockMvc.perform(post("/api/admin/knowledge/import-policies"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.policyCount").value(5))
                    .andExpect(jsonPath("$.data.chunkCount").value(20));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/knowledge/chunks-status 分块状态")
    class ChunksStatus {

        @Test
        @DisplayName("查询分块向量化状态")
        void shouldReturnChunksStatus() throws Exception {
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                    .thenReturn(10, 8);
            when(jdbcTemplate.queryForList(anyString()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/admin/knowledge/chunks-status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }
}