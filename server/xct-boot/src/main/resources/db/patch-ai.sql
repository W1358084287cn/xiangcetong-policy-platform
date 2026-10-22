-- =========================================================
-- 乡策通 AI 知识库模块 DDL（幂等，可重复执行）
-- 用途：AI 配置、知识文档、文档分块向量、对话历史
-- =========================================================

-- AI 配置表（全局唯一一条，系统管理员维护）
CREATE TABLE IF NOT EXISTS ai_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_url         VARCHAR(255)  NOT NULL DEFAULT 'https://api.deepseek.com/v1'  COMMENT '大模型 API 地址',
    api_key         VARCHAR(256)  NOT NULL DEFAULT ''                             COMMENT '大模型 API Key',
    model_name      VARCHAR(64)   NOT NULL DEFAULT 'deepseek-chat'                COMMENT '模型名称',
    embedding_url   VARCHAR(255)  NOT NULL DEFAULT 'https://api.siliconflow.cn/v1' COMMENT '向量化 API 地址',
    embedding_key   VARCHAR(256)  NOT NULL DEFAULT ''                             COMMENT '向量化 API Key',
    embedding_model VARCHAR(64)   NOT NULL DEFAULT 'BAAI/bge-large-zh-v1.5'      COMMENT '向量化模型名称',
    max_tokens      INT           NOT NULL DEFAULT 2048                           COMMENT '单次回复最大 Token',
    temperature     DECIMAL(3,2)  NOT NULL DEFAULT 0.7                           COMMENT '温度参数 0~2',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT 'AI 配置（全局唯一）';

-- 知识文档表
CREATE TABLE IF NOT EXISTS knowledge_document (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200)  NOT NULL                                       COMMENT '文档标题',
    file_name       VARCHAR(255)  NOT NULL                                       COMMENT '原始文件名',
    file_path       VARCHAR(500)  NOT NULL                                       COMMENT '服务器存储相对路径',
    file_type       VARCHAR(20)   NOT NULL                                       COMMENT '文件类型 PDF/WORD/MARKDOWN/TEXT',
    file_size       BIGINT        NOT NULL DEFAULT 0                             COMMENT '文件大小（字节）',
    chunk_count     INT           NOT NULL DEFAULT 0                             COMMENT '分块数量',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PROCESSING'                  COMMENT 'PROCESSING/READY/FAILED',
    error_msg       VARCHAR(500)                                                COMMENT '处理失败原因',
    created_by      BIGINT        NOT NULL                                       COMMENT '上传人用户 ID',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status)
) COMMENT '知识库文档';

-- 文档分块与向量表
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id     BIGINT        NOT NULL                                       COMMENT '所属文档 ID',
    chunk_index     INT           NOT NULL                                       COMMENT '分块序号（从 0 开始）',
    chunk_text      TEXT          NOT NULL                                       COMMENT '分块原文',
    embedding       JSON          NOT NULL                                       COMMENT '向量（浮点数组 JSON）',
    token_count     INT           NOT NULL DEFAULT 0                             COMMENT 'Token 估算数',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_document (document_id),
    FOREIGN KEY (document_id) REFERENCES knowledge_document(id) ON DELETE CASCADE
) COMMENT '知识库分块与向量';

-- AI 对话历史表
CREATE TABLE IF NOT EXISTS ai_chat_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        NOT NULL                                       COMMENT '用户 ID',
    session_id      VARCHAR(64)   NOT NULL                                       COMMENT '会话 ID（UUID）',
    role            VARCHAR(20)   NOT NULL                                       COMMENT '角色 USER/AI',
    content         TEXT          NOT NULL                                       COMMENT '消息内容',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_session (user_id, session_id)
) COMMENT 'AI 对话历史';

-- 预置默认 AI 配置（如果表为空）
INSERT IGNORE INTO ai_config (id, api_url, api_key, model_name, embedding_url, embedding_key, embedding_model)
VALUES (1, 'https://api.deepseek.com/v1', '', 'deepseek-chat', 'https://api.siliconflow.cn/v1', '', 'BAAI/bge-large-zh-v1.5');

-- =========================================================
-- 8.0 增量：DeepSeek 开发平台链接 + 余额查询
-- =========================================================
-- 如果 platform_url 列不存在则添加（兼容 MySQL 5.7+/8.0）
DROP PROCEDURE IF EXISTS add_platform_url_col;
DELIMITER //
CREATE PROCEDURE add_platform_url_col()
BEGIN
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'xiangcetong'
                     AND TABLE_NAME = 'ai_config'
                     AND COLUMN_NAME = 'platform_url') THEN
        ALTER TABLE ai_config
            ADD COLUMN platform_url VARCHAR(512) NOT NULL DEFAULT 'https://platform.deepseek.com'
            COMMENT 'DeepSeek 开发平台地址（管理端可配置跳转）';
    END IF;
END //
DELIMITER ;
CALL add_platform_url_col();
DROP PROCEDURE IF EXISTS add_platform_url_col;

-- =========================================================
-- 8.1 增量：AI 调用日志（消耗统计）+ 对话日志 + 知识库政策关联
-- =========================================================

-- AI Token 用量日志表
CREATE TABLE IF NOT EXISTS ai_usage_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        NOT NULL DEFAULT 0              COMMENT '用户 ID（0=匿名）',
    username        VARCHAR(64)   NOT NULL DEFAULT '匿名用户'       COMMENT '用户名',
    model           VARCHAR(64)   NOT NULL                        COMMENT '模型名称',
    prompt_tokens   INT           NOT NULL DEFAULT 0              COMMENT '输入 Token 数',
    completion_tokens INT         NOT NULL DEFAULT 0              COMMENT '输出 Token 数',
    total_tokens    INT           NOT NULL DEFAULT 0              COMMENT '总 Token 数',
    cost            DECIMAL(10,4) NOT NULL DEFAULT 0.0000         COMMENT '预估费用（元）',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_created (created_at),
    INDEX idx_user_created (user_id, created_at)
) COMMENT 'AI Token 用量日志';

-- AI 对话日志表（记录谁问了什么）
CREATE TABLE IF NOT EXISTS ai_chat_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        NOT NULL                        COMMENT '用户 ID',
    username        VARCHAR(64)   NOT NULL                        COMMENT '用户名',
    question        TEXT          NOT NULL                        COMMENT '用户问题',
    answer          TEXT          NOT NULL                        COMMENT 'AI 回答',
    from_knowledge  TINYINT       NOT NULL DEFAULT 0              COMMENT '是否命中知识库 0/1',
    model           VARCHAR(64)   NOT NULL                        COMMENT '使用的模型',
    prompt_tokens   INT           NOT NULL DEFAULT 0              COMMENT '输入 Token 数',
    completion_tokens INT         NOT NULL DEFAULT 0              COMMENT '输出 Token 数',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_created (created_at),
    INDEX idx_user (user_id),
    INDEX idx_user_created (user_id, created_at)
) COMMENT 'AI 对话日志';

-- 知识库政策来源标记（同步自 subsidy_policy 表）
CREATE TABLE IF NOT EXISTS knowledge_policy_ref (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id     BIGINT        NOT NULL                        COMMENT 'knowledge_document.id',
    policy_id       BIGINT        NOT NULL                        COMMENT 'subsidy_policy.id',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc (document_id),
    INDEX idx_policy (policy_id)
) COMMENT '知识库与政策关联';

-- =========================================================
-- 8.x 增量：对话日志新增手机号（优化按用户分组查询，支持手机号搜索）
-- =========================================================
DROP PROCEDURE IF EXISTS add_phone_to_chat_log;
DELIMITER //
CREATE PROCEDURE add_phone_to_chat_log()
BEGIN
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'xiangcetong'
                     AND TABLE_NAME = 'ai_chat_log'
                     AND COLUMN_NAME = 'phone') THEN
        ALTER TABLE ai_chat_log
            ADD COLUMN phone VARCHAR(20) NOT NULL DEFAULT '' COMMENT '用户手机号',
            ADD INDEX idx_phone (phone);
    END IF;
END //
DELIMITER ;
CALL add_phone_to_chat_log();
DROP PROCEDURE IF EXISTS add_phone_to_chat_log;