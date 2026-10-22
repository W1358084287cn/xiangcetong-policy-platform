package com.xiangcetong.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AiTableMigrationRunner 单元测试：启动时自动创建 AI 模块数据库表。
 */
@DisplayName("AI 表迁移启动器")
class AiTableMigrationRunnerTest {

    private JdbcTemplate jdbcTemplate;
    private AiTableMigrationRunner runner;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        runner = new AiTableMigrationRunner(jdbcTemplate);
    }

    @Nested
    @DisplayName("表创建")
    class TableCreation {

        @Test
        @DisplayName("成功创建所有 7 张表")
        void shouldCreateAllTables() {
            when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(Collections.emptyList());

            runner.run();

            verify(jdbcTemplate, atLeast(1)).execute(contains("CREATE TABLE IF NOT EXISTS ai_config"));
            verify(jdbcTemplate, atLeast(1)).execute(contains("CREATE TABLE IF NOT EXISTS knowledge_document"));
            verify(jdbcTemplate, atLeast(1)).execute(contains("CREATE TABLE IF NOT EXISTS knowledge_chunk"));
            verify(jdbcTemplate, atLeast(1)).execute(contains("CREATE TABLE IF NOT EXISTS ai_chat_history"));
            verify(jdbcTemplate, atLeast(1)).execute(contains("CREATE TABLE IF NOT EXISTS ai_usage_log"));
            verify(jdbcTemplate, atLeast(1)).execute(contains("CREATE TABLE IF NOT EXISTS ai_chat_log"));
            verify(jdbcTemplate, atLeast(1)).execute(contains("CREATE TABLE IF NOT EXISTS knowledge_policy_ref"));
        }

        @Test
        @DisplayName("表已存在时不报错（幂等）")
        void shouldNotFailWhenTablesExist() {
            when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of(
                    "ai_config", "knowledge_document", "knowledge_chunk",
                    "ai_chat_history", "ai_usage_log", "ai_chat_log", "knowledge_policy_ref"));

            assertDoesNotThrow(() -> runner.run());
        }
    }

    @Nested
    @DisplayName("ALTER 兼容修复")
    class AlterFixes {

        @Test
        @DisplayName("ALTER 执行失败不中断后续迁移")
        void shouldContinueWhenAlterFails() {
            when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(Collections.emptyList());
            doThrow(new RuntimeException("列已存在")).when(jdbcTemplate).execute(contains("MODIFY embedding JSON NULL"));

            assertDoesNotThrow(() -> runner.run());

            verify(jdbcTemplate, atLeast(1)).execute(contains("CREATE TABLE"));
        }

        @Test
        @DisplayName("尝试执行所有 ALTER 语句")
        void shouldAttemptAllAlters() {
            when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(Collections.emptyList());

            runner.run();

            verify(jdbcTemplate).execute(contains("MODIFY embedding JSON NULL"));
            verify(jdbcTemplate).execute(contains("MODIFY error_msg TEXT"));
            verify(jdbcTemplate).execute(contains("ADD COLUMN platform_url"));
            verify(jdbcTemplate).execute(contains("ADD COLUMN phone"));
            verify(jdbcTemplate).execute(contains("ADD INDEX idx_cl_phone"));
        }
    }

    @Nested
    @DisplayName("默认配置预置")
    class DefaultConfig {

        @Test
        @DisplayName("首次运行时插入默认配置")
        void shouldInsertDefaultConfigWhenNotExists() {
            when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(Collections.emptyList());
            when(jdbcTemplate.update(contains("INSERT IGNORE INTO ai_config"))).thenReturn(1);

            runner.run();

            verify(jdbcTemplate).update(contains("INSERT IGNORE INTO ai_config"));
        }

        @Test
        @DisplayName("配置已存在时跳过预置")
        void shouldSkipWhenConfigExists() {
            when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of(
                    "ai_config", "knowledge_document", "knowledge_chunk",
                    "ai_chat_history", "ai_usage_log", "ai_chat_log", "knowledge_policy_ref"));
            when(jdbcTemplate.update(contains("INSERT IGNORE INTO ai_config"))).thenReturn(0);

            runner.run();

            verify(jdbcTemplate).update(contains("INSERT IGNORE INTO ai_config"));
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandling {

        @Test
        @DisplayName("建表异常不崩溃")
        void shouldNotCrashOnCreateException() {
            doThrow(new RuntimeException("数据库连接失败"))
                    .when(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS ai_config"));

            assertDoesNotThrow(() -> runner.run());
        }

        @Test
        @DisplayName("验证查询异常不崩溃")
        void shouldNotCrashOnVerifyException() {
            doThrow(new RuntimeException("查询超时"))
                    .when(jdbcTemplate).queryForList(anyString(), eq(String.class));

            assertDoesNotThrow(() -> runner.run());
        }
    }

    @Nested
    @DisplayName("表验证")
    class TableVerification {

        @Test
        @DisplayName("验证所有表创建后输出表名列表")
        void shouldLogTableNamesAfterMigration() {
            List<String> tables = List.of(
                    "ai_config", "knowledge_document", "knowledge_chunk",
                    "ai_chat_history", "ai_usage_log", "ai_chat_log", "knowledge_policy_ref");
            when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(tables);

            assertDoesNotThrow(() -> runner.run());
        }

        @Test
        @DisplayName("部分表缺失时仍正常输出")
        void shouldHandlePartialTables() {
            when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("ai_config"));

            assertDoesNotThrow(() -> runner.run());
        }

        @Test
        @DisplayName("表列表为空时不崩溃")
        void shouldHandleEmptyTableList() {
            when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> runner.run());
        }
    }
}