package com.xiangcetong.ai.service;

import com.xiangcetong.ai.entity.KnowledgeChunk;
import com.xiangcetong.ai.entity.KnowledgeDocument;
import com.xiangcetong.ai.mapper.KnowledgeChunkMapper;
import com.xiangcetong.ai.mapper.KnowledgeDocumentMapper;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 知识库业务服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("知识库服务")
class KnowledgeServiceTest {

    @Mock
    private KnowledgeDocumentMapper docMapper;

    @Mock
    private KnowledgeChunkMapper chunkMapper;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        knowledgeService = new KnowledgeService(
                docMapper, chunkMapper, embeddingService, objectMapper, jdbcTemplate);

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
    @DisplayName("list 分页查询")
    class ListDocuments {

        @Test
        @DisplayName("分页查询返回文档列表")
        void shouldReturnPagedDocuments() {
            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setId(1L);
            doc.setTitle("惠农政策文档");
            doc.setFileName("policy.md");
            doc.setFileType("MARKDOWN");
            doc.setFileSize(1024L);
            doc.setChunkCount(5);
            doc.setStatus("READY");
            doc.setCreatedAt(java.time.LocalDateTime.now());

            when(docMapper.count()).thenReturn(1L);
            when(docMapper.list(anyInt(), anyInt())).thenReturn(List.of(doc));

            PageResult<Map<String, Object>> result = knowledgeService.list(1, 10);

            assertNotNull(result);
            assertEquals(1, result.getTotal());
            assertEquals(1, result.getList().size());
            assertEquals("惠农政策文档", result.getList().get(0).get("title"));
        }

        @Test
        @DisplayName("空列表返回空分页")
        void shouldReturnEmptyPage() {
            when(docMapper.count()).thenReturn(0L);
            when(docMapper.list(anyInt(), anyInt())).thenReturn(Collections.emptyList());

            PageResult<Map<String, Object>> result = knowledgeService.list(1, 10);

            assertNotNull(result);
            assertEquals(0, result.getTotal());
            assertTrue(result.getList().isEmpty());
        }

        @Test
        @DisplayName("非管理员角色抛异常")
        void shouldThrowForNonAdmin() {
            UserContext.clear();
            UserContext.LoginUser villager = new UserContext.LoginUser();
            villager.setUserId(2L);
            villager.setRole("VILLAGER");
            UserContext.set(villager);

            assertThrows(BizException.class, () -> knowledgeService.list(1, 10));
        }
    }

    @Nested
    @DisplayName("delete 删除文档")
    class DeleteDocument {

        @Test
        @DisplayName("成功删除文档及分块")
        void shouldDeleteDocumentAndChunks() {
            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setId(1L);
            doc.setTitle("测试文档");
            doc.setFilePath("uploads/knowledge/test.md");

            when(docMapper.findById(1L)).thenReturn(doc);

            Map<String, Object> result = knowledgeService.delete(1L);

            assertNotNull(result);
            assertEquals(1L, result.get("id"));
            verify(chunkMapper).deleteByDocumentId(1L);
            verify(docMapper).deleteById(1L);
        }

        @Test
        @DisplayName("文档不存在时抛异常")
        void shouldThrowWhenDocumentNotFound() {
            when(docMapper.findById(999L)).thenReturn(null);

            assertThrows(BizException.class, () -> knowledgeService.delete(999L));
        }
    }

    @Nested
    @DisplayName("chunkDetail 查看分块详情")
    class ChunkDetail {

        @Test
        @DisplayName("返回分块详情列表")
        void shouldReturnChunkDetails() {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setId(1L);
            chunk.setChunkIndex(0);
            chunk.setChunkText("测试分块文本");
            chunk.setTokenCount(10);

            when(chunkMapper.findByDocumentId(1L)).thenReturn(List.of(chunk));

            List<Map<String, Object>> result = knowledgeService.chunkDetail(1L);

            assertEquals(1, result.size());
            assertEquals("测试分块文本", result.get(0).get("chunkText"));
            assertEquals(10, result.get(0).get("tokenCount"));
        }

        @Test
        @DisplayName("无分块返回空列表")
        void shouldReturnEmptyWhenNoChunks() {
            when(chunkMapper.findByDocumentId(1L)).thenReturn(Collections.emptyList());

            List<Map<String, Object>> result = knowledgeService.chunkDetail(1L);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("reprocess 重新处理")
    class Reprocess {

        @Test
        @DisplayName("文档不存在时抛异常")
        void shouldThrowWhenDocumentNotFound() {
            when(docMapper.findById(999L)).thenReturn(null);

            assertThrows(BizException.class, () -> knowledgeService.reprocess(999L));
        }
    }

    @Nested
    @DisplayName("importPoliciesFromDb 从数据库导入")
    class ImportPolicies {

        @Test
        @DisplayName("无政策数据时返回空结果")
        void shouldReturnEmptyWhenNoPolicies() {
            when(jdbcTemplate.queryForList(anyString())).thenReturn(Collections.emptyList());

            Map<String, Object> result = knowledgeService.importPoliciesFromDb();

            assertEquals(0, result.get("policyCount"));
            assertNotNull(result.get("message"));
        }
    }
}