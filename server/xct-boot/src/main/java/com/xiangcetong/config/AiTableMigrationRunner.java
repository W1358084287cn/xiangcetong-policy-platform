package com.xiangcetong.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时自动创建 AI 模块所需数据库表（幂等，重复执行不报错）。
 * 解决环境没有 mysql CLI 无法手动执行 patch-ai.sql 的问题。
 */
@Component
public class AiTableMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AiTableMigrationRunner.class);

    private static final String CREATE_AI_CONFIG = """
        CREATE TABLE IF NOT EXISTS ai_config (
            id              BIGINT AUTO_INCREMENT PRIMARY KEY,
            api_url         VARCHAR(255)  NOT NULL DEFAULT 'https://api.deepseek.com/v1',
            api_key         VARCHAR(256)  NOT NULL DEFAULT '',
            model_name      VARCHAR(64)   NOT NULL DEFAULT 'deepseek-chat',
            embedding_url   VARCHAR(255)  NOT NULL DEFAULT 'https://api.siliconflow.cn/v1',
            embedding_key   VARCHAR(256)  NOT NULL DEFAULT '',
            embedding_model VARCHAR(64)   NOT NULL DEFAULT 'BAAI/bge-large-zh-v1.5',
            max_tokens      INT           NOT NULL DEFAULT 2048,
            temperature     DECIMAL(3,2)  NOT NULL DEFAULT 0.7,
            platform_url    VARCHAR(512)  NOT NULL DEFAULT 'https://platform.deepseek.com',
            updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        ) COMMENT 'AI 配置（全局唯一）'
        """;

    private static final String CREATE_KNOWLEDGE_DOC = """
        CREATE TABLE IF NOT EXISTS knowledge_document (
            id              BIGINT AUTO_INCREMENT PRIMARY KEY,
            title           VARCHAR(200)  NOT NULL,
            file_name       VARCHAR(255)  NOT NULL,
            file_path       VARCHAR(500)  NOT NULL,
            file_type       VARCHAR(20)   NOT NULL,
            file_size       BIGINT        NOT NULL DEFAULT 0,
            chunk_count     INT           NOT NULL DEFAULT 0,
            status          VARCHAR(20)   NOT NULL DEFAULT 'PROCESSING',
            error_msg       VARCHAR(500),
            created_by      BIGINT        NOT NULL,
            created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            INDEX idx_kd_status (status)
        ) COMMENT '知识库文档'
        """;

    private static final String CREATE_KNOWLEDGE_CHUNK = """
        CREATE TABLE IF NOT EXISTS knowledge_chunk (
            id              BIGINT AUTO_INCREMENT PRIMARY KEY,
            document_id     BIGINT        NOT NULL,
            chunk_index     INT           NOT NULL,
            chunk_text      TEXT          NOT NULL,
            embedding       JSON          NOT NULL,
            token_count     INT           NOT NULL DEFAULT 0,
            created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_kc_document (document_id)
        ) COMMENT '知识库分块与向量'
        """;

    private static final String CREATE_AI_CHAT_HISTORY = """
        CREATE TABLE IF NOT EXISTS ai_chat_history (
            id              BIGINT AUTO_INCREMENT PRIMARY KEY,
            user_id         BIGINT        NOT NULL,
            session_id      VARCHAR(64)   NOT NULL,
            role            VARCHAR(20)   NOT NULL,
            content         TEXT          NOT NULL,
            created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_ch_user_session (user_id, session_id)
        ) COMMENT 'AI 对话历史'
        """;

    private static final String INSERT_DEFAULT_CONFIG = """
        INSERT IGNORE INTO ai_config (id, api_url, api_key, model_name, embedding_url, embedding_key, embedding_model, platform_url)
        VALUES (1, 'https://api.deepseek.com/v1', '', 'deepseek-chat', 'https://api.siliconflow.cn/v1', '', 'BAAI/bge-large-zh-v1.5', 'https://platform.deepseek.com')
        """;

    /** 8.1 新增 — AI Token 用量日志表 */
    private static final String CREATE_AI_USAGE_LOG = """
        CREATE TABLE IF NOT EXISTS ai_usage_log (
            id              BIGINT AUTO_INCREMENT PRIMARY KEY,
            user_id         BIGINT        NOT NULL DEFAULT 0,
            username        VARCHAR(64)   NOT NULL DEFAULT '匿名用户',
            model           VARCHAR(64)   NOT NULL,
            prompt_tokens   INT           NOT NULL DEFAULT 0,
            completion_tokens INT         NOT NULL DEFAULT 0,
            total_tokens    INT           NOT NULL DEFAULT 0,
            cost            DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
            created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_ul_created (created_at),
            INDEX idx_ul_user_created (user_id, created_at)
        ) COMMENT 'AI Token 用量日志'
        """;

    /** 8.1 新增 — AI 对话日志表 */
    private static final String CREATE_AI_CHAT_LOG = """
        CREATE TABLE IF NOT EXISTS ai_chat_log (
            id              BIGINT AUTO_INCREMENT PRIMARY KEY,
            user_id         BIGINT        NOT NULL,
            username        VARCHAR(64)   NOT NULL,
            phone           VARCHAR(20)   NOT NULL DEFAULT '',
            question        TEXT          NOT NULL,
            answer          TEXT          NOT NULL,
            from_knowledge  TINYINT       NOT NULL DEFAULT 0,
            model           VARCHAR(64)   NOT NULL,
            prompt_tokens   INT           NOT NULL DEFAULT 0,
            completion_tokens INT         NOT NULL DEFAULT 0,
            created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_cl_created (created_at),
            INDEX idx_cl_user (user_id),
            INDEX idx_cl_user_created (user_id, created_at),
            INDEX idx_cl_phone (phone)
        ) COMMENT 'AI 对话日志'
        """;

    /** 8.1 新增 — 知识库政策关联表 */
    private static final String CREATE_KNOWLEDGE_POLICY_REF = """
        CREATE TABLE IF NOT EXISTS knowledge_policy_ref (
            id              BIGINT AUTO_INCREMENT PRIMARY KEY,
            document_id     BIGINT        NOT NULL,
            policy_id       BIGINT        NOT NULL,
            created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_kpr_doc (document_id),
            INDEX idx_kpr_policy (policy_id)
        ) COMMENT '知识库与政策关联'
        """;

    /** 向量化可能失败，embedding 列改为可空，同时扩大 error_msg */
    private static final String ALTER_EMBEDDING_NULLABLE = """
        ALTER TABLE knowledge_chunk MODIFY embedding JSON NULL
        """;

    private static final String ALTER_ERROR_MSG_SIZE = """
        ALTER TABLE knowledge_document MODIFY error_msg TEXT
        """;

    /** 添加 platform_url 列（如果不存在） */
    private static final String ADD_PLATFORM_URL = """
        ALTER TABLE ai_config ADD COLUMN platform_url VARCHAR(512) NOT NULL DEFAULT 'https://platform.deepseek.com'
        """;

    /** 添加 phone 列到 ai_chat_log（如果表已存在但缺少此列，幂等：存在则跳过） */
    private static final String ADD_PHONE_TO_CHAT_LOG = """
        ALTER TABLE ai_chat_log ADD COLUMN phone VARCHAR(20) NOT NULL DEFAULT ''
        """;

    /** 添加 phone 索引到 ai_chat_log */
    private static final String ADD_PHONE_INDEX = """
        ALTER TABLE ai_chat_log ADD INDEX idx_cl_phone (phone)
        """;

    // ===== 8.2 AI 管家：对话表 + 消息表扩展列 =====

    private static final String CREATE_AI_CONVERSATION = """
        CREATE TABLE IF NOT EXISTS ai_conversation (
            id          BIGINT AUTO_INCREMENT PRIMARY KEY,
            user_id     BIGINT       NOT NULL,
            title       VARCHAR(100) NOT NULL DEFAULT '新对话',
            pinned      TINYINT      NOT NULL DEFAULT 0,
            msg_count   INT          NOT NULL DEFAULT 0,
            last_action VARCHAR(60)  DEFAULT NULL,
            created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            INDEX idx_aconv_user (user_id),
            INDEX idx_aconv_user_pinned (user_id, pinned)
        ) COMMENT 'AI 管家对话会话'
        """;

    private static final String ADD_CONVERSATION_ID = """
        ALTER TABLE ai_chat_log ADD COLUMN conversation_id BIGINT DEFAULT NULL
        """;

    private static final String ADD_AGENT_ACTION = """
        ALTER TABLE ai_chat_log ADD COLUMN agent_action VARCHAR(60) DEFAULT NULL
        """;

    private static final String ADD_CARD_DATA = """
        ALTER TABLE ai_chat_log ADD COLUMN card_data JSON DEFAULT NULL
        """;

    private static final String ADD_CONV_TIME_INDEX = """
        ALTER TABLE ai_chat_log ADD INDEX idx_conv_time (conversation_id, created_at)
        """;

    private final JdbcTemplate jdbcTemplate;

    public AiTableMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("开始 AI 模块数据库迁移...");
        try {
            // 建表（幂等）
            jdbcTemplate.execute(CREATE_AI_CONFIG);
            jdbcTemplate.execute(CREATE_KNOWLEDGE_DOC);
            jdbcTemplate.execute(CREATE_KNOWLEDGE_CHUNK);
            jdbcTemplate.execute(CREATE_AI_CHAT_HISTORY);
            jdbcTemplate.execute(CREATE_AI_USAGE_LOG);
            jdbcTemplate.execute(CREATE_AI_CHAT_LOG);
            jdbcTemplate.execute(CREATE_KNOWLEDGE_POLICY_REF);
            log.info("AI 模块 4+3 张表已就绪");

            // 兼容已有表结构的修复（幂等）
            try {
                jdbcTemplate.execute(ALTER_EMBEDDING_NULLABLE);
                log.info("knowledge_chunk.embedding 已改为可空");
            } catch (Exception e) {
                log.debug("ALTER embedding 跳过: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute(ALTER_ERROR_MSG_SIZE);
                log.info("knowledge_document.error_msg 已改为 TEXT");
            } catch (Exception e) {
                log.debug("ALTER error_msg 跳过: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute(ADD_PLATFORM_URL);
                log.info("ai_config.platform_url 已添加（幂等）");
            } catch (Exception e) {
                log.debug("ADD platform_url 跳过: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute(ADD_PHONE_TO_CHAT_LOG);
                log.info("ai_chat_log.phone 已添加（幂等）");
            } catch (Exception e) {
                log.debug("ADD phone 跳过: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute(ADD_PHONE_INDEX);
                log.info("ai_chat_log.idx_cl_phone 已添加（幂等）");
            } catch (Exception e) {
                log.debug("ADD phone index 跳过: {}", e.getMessage());
            }

            // ===== AI 管家 8.2：对话会话表 + 消息表扩展列 =====
            jdbcTemplate.execute(CREATE_AI_CONVERSATION);
            log.info("ai_conversation 表已就绪");
            try { jdbcTemplate.execute(ADD_CONVERSATION_ID); log.info("ai_chat_log.conversation_id 已添加"); } catch (Exception e) { log.debug("ADD conversation_id 跳过: {}", e.getMessage()); }
            try { jdbcTemplate.execute(ADD_AGENT_ACTION); log.info("ai_chat_log.agent_action 已添加"); } catch (Exception e) { log.debug("ADD agent_action 跳过: {}", e.getMessage()); }
            try { jdbcTemplate.execute(ADD_CARD_DATA); log.info("ai_chat_log.card_data 已添加"); } catch (Exception e) { log.debug("ADD card_data 跳过: {}", e.getMessage()); }
            try { jdbcTemplate.execute(ADD_CONV_TIME_INDEX); log.info("ai_chat_log.idx_conv_time 已添加"); } catch (Exception e) { log.debug("ADD conv_time index 跳过: {}", e.getMessage()); }

            // 预置默认配置
            int inserted = jdbcTemplate.update(INSERT_DEFAULT_CONFIG);
            if (inserted > 0) {
                log.info("已预置默认 AI 配置（DeepSeek URL 已填，请前往管理端配置 API Key）");
            } else {
                log.info("AI 配置已存在，跳过预置");
            }

            // 验证表是否创建成功
            List<String> tables = jdbcTemplate.queryForList(
                    "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() " +
                    "AND TABLE_NAME IN ('ai_config','knowledge_document','knowledge_chunk','ai_chat_history','ai_usage_log','ai_chat_log','knowledge_policy_ref')",
                    String.class);
            log.info("AI 模块迁移完成，已就绪 {} 张表: {}", tables.size(), tables);
        } catch (Exception e) {
            log.error("AI 模块数据库迁移失败: {}", e.getMessage(), e);
        }
    }
}