package com.xiangcetong.ai.controller;

import com.xiangcetong.ai.service.KnowledgeService;
import com.xiangcetong.common.ApiResponse;
import com.xiangcetong.common.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端知识库接口（仅 SYS_ADMIN）。
 */
@RestController
@RequestMapping("/api/admin/knowledge")
public class AdminKnowledgeController {

    private final KnowledgeService knowledgeService;
    private final JdbcTemplate jdbcTemplate;

    public AdminKnowledgeController(KnowledgeService knowledgeService,
                                     JdbcTemplate jdbcTemplate) {
        this.knowledgeService = knowledgeService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 上传文档 */
    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(knowledgeService.upload(file));
    }

    /** 知识库文档列表 */
    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(knowledgeService.list(page, size));
    }

    /** 查看文档分块详情 */
    @GetMapping("/{id}/chunks")
    public ApiResponse<List<Map<String, Object>>> chunkDetail(@PathVariable Long id) {
        return ApiResponse.ok(knowledgeService.chunkDetail(id));
    }

    /** 删除文档 */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        return ApiResponse.ok(knowledgeService.delete(id));
    }

    /** 重新处理文档 */
    @PostMapping("/{id}/reprocess")
    public ApiResponse<Map<String, Object>> reprocess(@PathVariable Long id) {
        return ApiResponse.ok(knowledgeService.reprocess(id));
    }

    /** 从 subsidy_policy 表导入所有政策到知识库 */
    @PostMapping("/import-policies")
    public ApiResponse<Map<String, Object>> importPolicies() {
        return ApiResponse.ok(knowledgeService.importPoliciesFromDb());
    }

    /** 调试：查看知识库分块向量化状态 */
    @GetMapping("/chunks-status")
    public ApiResponse<Map<String, Object>> chunksStatus() {
        int total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_chunk", Integer.class);
        int withEmb = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_chunk WHERE embedding IS NOT NULL AND embedding != ''", Integer.class);
        int withoutEmb = total - withEmb;
        List<Map<String, Object>> samples = jdbcTemplate.queryForList(
                "SELECT id, document_id, chunk_index, " +
                "CASE WHEN embedding IS NULL THEN 'NULL' WHEN embedding = '' THEN 'EMPTY' ELSE CONCAT('OK(len=', LENGTH(embedding), ')') END AS emb_status, " +
                "LEFT(chunk_text, 60) AS text_preview " +
                "FROM knowledge_chunk ORDER BY id LIMIT 20");
        Map<String, Object> result = new HashMap<>();
        result.put("totalChunks", total);
        result.put("withEmbedding", withEmb);
        result.put("withoutEmbedding", withoutEmb);
        result.put("samples", samples);
        return ApiResponse.ok(result);
    }
}