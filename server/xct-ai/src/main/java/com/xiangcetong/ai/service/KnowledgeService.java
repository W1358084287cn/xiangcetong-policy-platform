package com.xiangcetong.ai.service;

import com.xiangcetong.ai.entity.KnowledgeDocument;
import com.xiangcetong.ai.entity.KnowledgeChunk;
import com.xiangcetong.ai.mapper.KnowledgeDocumentMapper;
import com.xiangcetong.ai.mapper.KnowledgeChunkMapper;
import com.xiangcetong.ai.support.DocumentParser;
import com.xiangcetong.ai.support.TextChunker;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.Role;
import com.xiangcetong.common.UserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库业务实现：文档上传/解析/分块/向量化/检索/删除/政策导入。
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private static final Path UPLOAD_DIR = Paths.get("uploads/knowledge");
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB

    private final KnowledgeDocumentMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeService(KnowledgeDocumentMapper docMapper,
                            KnowledgeChunkMapper chunkMapper,
                            EmbeddingService embeddingService,
                            ObjectMapper objectMapper,
                            JdbcTemplate jdbcTemplate) {
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 上传文档并异步处理（解析+分块+向量化）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> upload(MultipartFile file) {
        UserContext.LoginUser admin = UserContext.requireRole(Role.SYS_ADMIN);
        if (file == null || file.isEmpty()) {
            throw BizException.badParam("请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BizException.badParam("文件大小不能超过 20MB");
        }

        String originalName = file.getOriginalFilename();
        String fileType = detectFileType(originalName);
        String savedName = UUID.randomUUID().toString().substring(0, 8) + "_" + originalName;

        try {
            Files.createDirectories(UPLOAD_DIR);
            Path destPath = UPLOAD_DIR.resolve(savedName);
            file.transferTo(destPath);

            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setTitle(originalName.replaceFirst("\\.[^.]+$", ""));
            doc.setFileName(originalName);
            doc.setFilePath(destPath.toString());
            doc.setFileType(fileType);
            doc.setFileSize(file.getSize());
            doc.setChunkCount(0);
            doc.setStatus("PROCESSING");
            doc.setCreatedBy(admin.getUserId());
            docMapper.insert(doc);

            log.info("知识文档已上传: id={}, name={}", doc.getId(), originalName);

            // 同步处理（文件不大的话可以同步，大的话改异步线程池）
            processDocument(doc, destPath);

            Map<String, Object> result = new HashMap<>();
            result.put("id", doc.getId());
            result.put("title", doc.getTitle());
            result.put("status", doc.getStatus());
            result.put("chunkCount", doc.getChunkCount());
            return result;
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            log.error("文档上传失败", e);
            throw BizException.of(ErrorCodes.SERVER_ERROR, "文档上传失败: " + e.getMessage());
        }
    }

    /**
     * 处理文档：提取文本 → 分块 → 向量化 → 存储。
     */
    private void processDocument(KnowledgeDocument doc, Path filePath) {
        try {
            // 1. 提取文本
            String text = DocumentParser.extractText(filePath, doc.getFileType());
            if (text.isBlank()) {
                docMapper.updateStatus(doc.getId(), "FAILED", "文档内容为空", 0);
                return;
            }

            // 2. 分块
            List<String> chunks = TextChunker.chunk(text);
            if (chunks.isEmpty()) {
                docMapper.updateStatus(doc.getId(), "FAILED", "分块后无有效内容", 0);
                return;
            }

            // 3. 批量向量化（失败则降级为纯关键词检索）
            List<float[]> vectors;
            boolean embeddingOk = true;
            String embeddingHint = null;
            try {
                vectors = embeddingService.embedBatch(chunks);
            } catch (Exception e) {
                log.warn("向量化失败，降级为关键词检索: id={}, err={}", doc.getId(), e.getMessage());
                vectors = null;
                embeddingOk = false;
                embeddingHint = "向量化失败，使用关键词检索";
            }

            // 4. 批量插入
            List<KnowledgeChunk> chunkEntities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk ck = new KnowledgeChunk();
                ck.setDocumentId(doc.getId());
                ck.setChunkIndex(i);
                ck.setChunkText(chunks.get(i));
                if (embeddingOk && vectors != null) {
                    ck.setEmbedding(vectorToJson(vectors.get(i)));
                }
                ck.setTokenCount(estimateTokens(chunks.get(i)));
                chunkEntities.add(ck);
            }
            chunkMapper.batchInsert(chunkEntities);

            // 5. 更新文档状态
            docMapper.updateStatus(doc.getId(), "READY", embeddingHint, chunks.size());
            doc.setStatus("READY");
            doc.setChunkCount(chunks.size());
            log.info("文档处理完成: id={}, 分块数={}, 向量化={}", doc.getId(), chunks.size(), embeddingOk ? "成功" : "降级关键词");
        } catch (BizException e) {
            docMapper.updateStatus(doc.getId(), "FAILED", e.getMessage(), 0);
            throw e;
        } catch (Exception e) {
            log.error("文档处理失败: id={}", doc.getId(), e);
            docMapper.updateStatus(doc.getId(), "FAILED", e.getMessage(), 0);
        }
    }

    /**
     * 管理端分页查询知识库文档。
     */
    public PageResult<Map<String, Object>> list(int page, int size) {
        UserContext.requireRole(Role.SYS_ADMIN);
        int pn = PageResult.normalizePageNum(page);
        int ps = PageResult.normalizePageSize(size);
        long total = docMapper.count();
        List<KnowledgeDocument> rows = docMapper.list(PageResult.offset(pn, ps), ps);
        List<Map<String, Object>> list = rows.stream().map(this::toVo).collect(Collectors.toList());
        return new PageResult<>(list, total, pn, ps);
    }

    /**
     * 查看文档的分块详情。
     */
    public List<Map<String, Object>> chunkDetail(Long docId) {
        UserContext.requireRole(Role.SYS_ADMIN);
        List<KnowledgeChunk> chunks = chunkMapper.findByDocumentId(docId);
        return chunks.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("chunkIndex", c.getChunkIndex());
            m.put("chunkText", c.getChunkText());
            m.put("tokenCount", c.getTokenCount());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 删除文档及其所有分块。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> delete(Long id) {
        UserContext.requireRole(Role.SYS_ADMIN);
        KnowledgeDocument doc = docMapper.findById(id);
        if (doc == null) {
            throw BizException.of(ErrorCodes.NOT_FOUND, "文档不存在");
        }
        // 先删分块（外键级联也会删，这里显式操作）
        chunkMapper.deleteByDocumentId(id);
        docMapper.deleteById(id);
        // 尝试删除文件
        try {
            Files.deleteIfExists(Paths.get(doc.getFilePath()));
        } catch (IOException e) {
            log.warn("删除文档文件失败: {}", doc.getFilePath(), e);
        }
        log.info("知识文档已删除: id={}, title={}", id, doc.getTitle());
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return result;
    }

    /**
     * 重新处理文档。
     */
    public Map<String, Object> reprocess(Long id) {
        UserContext.requireRole(Role.SYS_ADMIN);
        KnowledgeDocument doc = docMapper.findById(id);
        if (doc == null) {
            throw BizException.of(ErrorCodes.NOT_FOUND, "文档不存在");
        }
        // 清空旧分块
        chunkMapper.deleteByDocumentId(id);
        docMapper.updateStatus(id, "PROCESSING", null, 0);
        // 重新处理
        processDocument(doc, Paths.get(doc.getFilePath()));
        Map<String, Object> result = new HashMap<>();
        result.put("id", doc.getId());
        result.put("status", doc.getStatus());
        result.put("chunkCount", doc.getChunkCount());
        return result;
    }

    /**
     * 检测文件类型。
     */
    private String detectFileType(String fileName) {
        if (fileName == null) {
            return "TEXT";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".docx")) return "WORD";
        if (lower.endsWith(".doc")) return "WORD";
        if (lower.endsWith(".md")) return "MARKDOWN";
        if (lower.endsWith(".markdown")) return "MARKDOWN";
        return "TEXT";
    }

    /**
     * 向量数组转 JSON 字符串。
     */
    private String vectorToJson(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("向量序列化失败", e);
        }
    }

    /**
     * 粗估 Token 数（中文约 1.5 字符/Token）。
     */
    private int estimateTokens(String text) {
        if (text == null) return 0;
        return Math.max(1, (int) (text.length() / 1.5));
    }

    /**
     * 将一条数据库政策同步到知识库（新增或更新）。
     * 由 PolicyServiceImpl 调用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncSinglePolicy(Long policyId, String title, String content, String conditions,
                                  String standard, String consultPhone, String category) {
        // 格式化政策文本（与 importPoliciesFromDb 保持一致：纯文本字段名，不用 markdown）
        StringBuilder sb = new StringBuilder();
        sb.append("政策名称：").append(title != null ? title : "政策").append("\n");
        if (standard != null && !standard.isBlank()) {
            sb.append("补贴标准：").append(standard).append("\n");
        }
        if (content != null && !content.isBlank()) {
            sb.append("政策内容：").append(content).append("\n");
        }
        if (conditions != null && !conditions.isBlank()) {
            sb.append("申请条件：").append(conditions).append("\n");
        }
        if (consultPhone != null && !consultPhone.isBlank()) {
            sb.append("咨询电话：").append(consultPhone).append("\n");
        }
        if (category != null && !category.isBlank()) {
            sb.append("政策类别：").append(category).append("\n");
        }
        String policyText = sb.toString().trim();

        // 查找或创建知识文档（统一管理"数据库政策"文档）
        KnowledgeDocument doc = docMapper.findByTitle("数据库政策同步");
        if (doc == null) {
            doc = new KnowledgeDocument();
            doc.setTitle("数据库政策同步");
            doc.setFileName("policy_sync");
            doc.setFilePath("policy_sync");
            doc.setFileType("MARKDOWN");
            doc.setFileSize(0L);
            doc.setChunkCount(0);
            doc.setStatus("PROCESSING");
            doc.setCreatedBy(1L); // 系统
            docMapper.insert(doc);
        }

        // 删除该政策的旧分块
        List<KnowledgeChunk> allChunks = chunkMapper.findByDocumentId(doc.getId());
        int removedCount = 0;
        if (allChunks != null) {
            // 分块首行格式为「政策名称：XXX」，据此定位同一政策的旧分块
            String key = "政策名称：" + (title != null ? title : "政策");
            for (KnowledgeChunk ck : allChunks) {
                if (ck.getChunkText() != null && ck.getChunkText().startsWith(key)) {
                    chunkMapper.deleteById(ck.getId());
                    removedCount++;
                }
            }
        }

        // 分块
        List<String> chunks = TextChunker.chunk(policyText);
        if (chunks.isEmpty()) {
            return;
        }

        // 向量化
        List<float[]> vectors = null;
        try {
            vectors = embeddingService.embedBatch(chunks);
        } catch (Exception e) {
            log.warn("政策同步向量化失败，使用关键词检索: policyId={}", policyId);
        }

        // 插入分块（追加在已有分块后面）
        int baseIndex = (allChunks != null ? allChunks.size() : 0) - removedCount;
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk ck = new KnowledgeChunk();
            ck.setDocumentId(doc.getId());
            ck.setChunkIndex(baseIndex + i);
            ck.setChunkText(chunks.get(i));
            if (vectors != null && i < vectors.size()) {
                ck.setEmbedding(vectorToJson(vectors.get(i)));
            }
            ck.setTokenCount(estimateTokens(chunks.get(i)));
            chunkMapper.insert(ck);
        }

        // 更新文档分块数
        int newCount = baseIndex + chunks.size();
        docMapper.updateStatus(doc.getId(), "READY", vectors == null ? "向量化失败，使用关键词检索" : null, newCount);
        log.info("政策已同步至知识库: policyId={}, title={}", policyId, title);
    }

    /**
     * 从数据库政策表全量重建知识库。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rebuildFromPolicies(List<Map<String, Object>> policies) {
        UserContext.LoginUser admin = UserContext.requireRole(Role.SYS_ADMIN);

        // 删除旧的"数据库政策同步"文档及其分块
        KnowledgeDocument oldDoc = docMapper.findByTitle("数据库政策同步");
        if (oldDoc != null) {
            chunkMapper.deleteByDocumentId(oldDoc.getId());
            docMapper.deleteById(oldDoc.getId());
        }

        // 建新文档
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle("数据库政策同步");
        doc.setFileName("policy_sync");
        doc.setFilePath("policy_sync");
        doc.setFileType("MARKDOWN");
        doc.setFileSize(0L);
        doc.setChunkCount(0);
        doc.setStatus("PROCESSING");
        doc.setCreatedBy(admin.getUserId());
        docMapper.insert(doc);

        int totalChunks = 0;
        int policyCount = 0;
        boolean embeddingOk = true;

        for (Map<String, Object> p : policies) {
            String title = (String) p.getOrDefault("title", "");
            String content = (String) p.getOrDefault("content", "");
            String conditions = (String) p.getOrDefault("conditions", "");
            String standard = (String) p.getOrDefault("standard", "");
            String consultPhone = (String) p.getOrDefault("consultPhone", "");
            String category = (String) p.getOrDefault("category", "");

            StringBuilder sb = new StringBuilder();
            sb.append("### ").append(title).append("\n\n");
            if (standard != null && !standard.isBlank()) sb.append("**补贴标准**: ").append(standard).append("\n\n");
            if (content != null && !content.isBlank()) sb.append("**政策内容**: ").append(content).append("\n\n");
            if (conditions != null && !conditions.isBlank()) sb.append("**申请条件**: ").append(conditions).append("\n\n");
            if (consultPhone != null && !consultPhone.isBlank()) sb.append("**咨询电话**: ").append(consultPhone).append("\n\n");
            if (category != null && !category.isBlank()) sb.append("**类别**: ").append(category).append("\n");

            String policyText = sb.toString().trim();
            List<String> chunks = TextChunker.chunk(policyText);

            List<float[]> vectors = null;
            try {
                vectors = embeddingService.embedBatch(chunks);
            } catch (Exception e) {
                log.warn("批量同步向量化失败，降级: {}", e.getMessage());
                embeddingOk = false;
            }

            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk ck = new KnowledgeChunk();
                ck.setDocumentId(doc.getId());
                ck.setChunkIndex(totalChunks);
                ck.setChunkText(chunks.get(i));
                if (vectors != null && i < vectors.size()) {
                    ck.setEmbedding(vectorToJson(vectors.get(i)));
                }
                ck.setTokenCount(estimateTokens(chunks.get(i)));
                chunkMapper.insert(ck);
                totalChunks++;
            }
            policyCount++;
        }

        docMapper.updateStatus(doc.getId(), "READY",
                embeddingOk ? null : "部分向量化失败，使用关键词检索", totalChunks);
        log.info("知识库全量重建完成: 政策数={}, 分块数={}, 向量化={}", policyCount, totalChunks, embeddingOk ? "成功" : "降级");

        Map<String, Object> result = new HashMap<>();
        result.put("policyCount", policyCount);
        result.put("chunkCount", totalChunks);
        result.put("embeddingOk", embeddingOk);
        return result;
    }

    /**
     * 从 subsidy_policy 表读取所有已发布政策并导入知识库。
     * 不需要外部传入数据，直接查数据库。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importPoliciesFromDb() {
        UserContext.requireRole(Role.SYS_ADMIN);
        log.info("开始从 subsidy_policy 表导入政策到知识库...");

        List<Map<String, Object>> policies = jdbcTemplate.queryForList(
                "SELECT id, title, content, conditions, standard, consult_phone AS consultPhone, " +
                "category, source_org AS sourceOrg, subsidy_amount_text AS subsidyAmountText, " +
                "deadline_text AS deadlineText, consult_info AS consultInfo, apply_conditions AS applyConditions " +
                "FROM subsidy_policy");

        if (policies.isEmpty()) {
            log.info("subsidy_policy 表中暂无政策数据");
            return Map.of("policyCount", 0, "chunkCount", 0, "message", "暂无政策数据可导入");
        }

        // 删除旧的"数据库政策同步"文档及其分块
        KnowledgeDocument oldDoc = docMapper.findByTitle("数据库政策同步");
        if (oldDoc != null) {
            chunkMapper.deleteByDocumentId(oldDoc.getId());
            docMapper.deleteById(oldDoc.getId());
        }
        // 清理可能存在的空向量残留分块（历史 bug 导致）
        jdbcTemplate.update("DELETE FROM knowledge_chunk WHERE embedding IS NULL OR embedding = ''");

        // 建新文档
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle("数据库政策同步");
        doc.setFileName("policy_sync");
        doc.setFilePath("policy_sync");
        doc.setFileType("MARKDOWN");
        doc.setFileSize(0L);
        doc.setChunkCount(0);
        doc.setStatus("PROCESSING");
        doc.setCreatedBy(UserContext.get().getUserId());
        docMapper.insert(doc);

        int totalChunks = 0;
        int policyCount = 0;
        boolean embeddingOk = true;

        for (Map<String, Object> p : policies) {
            Long id = (Long) p.get("id");
            String title = (String) p.getOrDefault("title", "未知政策");
            String content = (String) p.getOrDefault("content", "");
            String conditions = (String) p.getOrDefault("conditions", "");
            String standard = (String) p.getOrDefault("standard", "");
            String consultPhone = (String) p.getOrDefault("consultPhone", "");
            String category = (String) p.getOrDefault("category", "");
            String sourceOrg = (String) p.getOrDefault("sourceOrg", "");
            String subsidyAmountText = (String) p.getOrDefault("subsidyAmountText", "");
            String deadlineText = (String) p.getOrDefault("deadlineText", "");
            String consultInfo = (String) p.getOrDefault("consultInfo", "");
            String applyConditions = (String) p.getOrDefault("applyConditions", "");

            StringBuilder sb = new StringBuilder();
            // 2026-10-18：入库文本改用「纯文本字段名」，不再用 markdown（### / **）。
            // 原格式会让大模型把「### 政策名称」「**政策内容**」原样抄进回答，
            // 用户看到的是一段带井号和星号的原始数据，而不是一段话。
            sb.append("政策名称：").append(title).append("\n");
            if (category != null && !category.isBlank()) sb.append("政策类别：").append(category).append("\n");
            if (sourceOrg != null && !sourceOrg.isBlank()) sb.append("发布单位：").append(sourceOrg).append("\n");
            if (standard != null && !standard.isBlank()) sb.append("补贴标准：").append(standard).append("\n");
            if (subsidyAmountText != null && !subsidyAmountText.isBlank())
                sb.append("补贴金额：").append(subsidyAmountText).append("\n");
            if (content != null && !content.isBlank()) sb.append("政策内容：").append(content).append("\n");
            if (conditions != null && !conditions.isBlank()) sb.append("申请条件：").append(conditions).append("\n");
            if (applyConditions != null && !applyConditions.isBlank())
                sb.append("申领条件：").append(applyConditions).append("\n");
            if (deadlineText != null && !deadlineText.isBlank()) sb.append("申报时限：").append(deadlineText).append("\n");
            if (consultPhone != null && !consultPhone.isBlank()) sb.append("咨询电话：").append(consultPhone).append("\n");
            if (consultInfo != null && !consultInfo.isBlank()) sb.append("咨询方式：").append(consultInfo).append("\n");

            String policyText = sb.toString().trim();
            List<String> chunks = TextChunker.chunk(policyText);
            if (chunks.isEmpty()) continue;

            List<float[]> vectors = null;
            log.info("开始向量化: policy={}, chunks数={}", title, chunks.size());
            try {
                vectors = embeddingService.embedBatch(chunks);
                log.info("向量化完成: policy={}, vectors={}, dim={}",
                        title,
                        vectors != null ? vectors.size() : -1,
                        vectors != null && !vectors.isEmpty() && vectors.get(0) != null
                                ? vectors.get(0).length : -1);
            } catch (Exception e) {
                log.warn("政策 [{}] 向量化失败，降级关键词检索: {}", title, e.getMessage());
                embeddingOk = false;
            }

            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk ck = new KnowledgeChunk();
                ck.setDocumentId(doc.getId());
                ck.setChunkIndex(totalChunks);
                ck.setChunkText(chunks.get(i));
                if (vectors != null && i < vectors.size()) {
                    String embJson = vectorToJson(vectors.get(i));
                    if (embJson == null || embJson.isBlank()) {
                        log.error("向量序列化结果为空! policy={}, chunkIdx={}", title, i);
                        embeddingOk = false;
                    } else {
                        ck.setEmbedding(embJson);
                        if (policyCount == 0 && i == 0) {
                            log.info("第一条向量样本: length={}, 前80字符={}", embJson.length(),
                                    embJson.substring(0, Math.min(80, embJson.length())));
                        }
                    }
                }
                ck.setTokenCount(estimateTokens(chunks.get(i)));
                chunkMapper.insert(ck);
                totalChunks++;
            }
            policyCount++;
            log.info("已导入政策 [{}/{}]: {}", policyCount, policies.size(), title);
        }

        docMapper.updateStatus(doc.getId(), "READY",
                embeddingOk ? null : "部分向量化失败，使用关键词检索", totalChunks);
        log.info("政策导入完成: 政策数={}, 分块数={}, 向量化={}", policyCount, totalChunks,
                embeddingOk ? "全部成功" : "部分降级");

        Map<String, Object> result = new HashMap<>();
        result.put("policyCount", policyCount);
        result.put("chunkCount", totalChunks);
        result.put("embeddingOk", embeddingOk);
        return result;
    }

    private Map<String, Object> toVo(KnowledgeDocument doc) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", doc.getId());
        m.put("title", doc.getTitle());
        m.put("fileName", doc.getFileName());
        m.put("fileType", doc.getFileType());
        m.put("fileSize", doc.getFileSize());
        m.put("chunkCount", doc.getChunkCount());
        m.put("status", doc.getStatus());
        m.put("errorMsg", doc.getErrorMsg());
        m.put("createdBy", doc.getCreatedBy());
        m.put("createdAt", doc.getCreatedAt());
        m.put("updatedAt", doc.getUpdatedAt());
        return m;
    }
}