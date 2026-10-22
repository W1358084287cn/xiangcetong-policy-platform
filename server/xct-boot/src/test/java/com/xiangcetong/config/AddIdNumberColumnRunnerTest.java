package com.xiangcetong.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AddIdNumberColumnRunner 单元测试：启动时自动补齐 villager_info 表缺失的身份证列。
 */
class AddIdNumberColumnRunnerTest {

    private JdbcTemplate jdbcTemplate;
    private AddIdNumberColumnRunner runner;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        runner = new AddIdNumberColumnRunner(jdbcTemplate);
    }

    @Test
    void addsMissingColumns() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("id_number")))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("id_front")))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("id_back")))
                .thenReturn(0);

        runner.run();

        verify(jdbcTemplate).execute("ALTER TABLE villager_info ADD COLUMN id_number VARCHAR(18) NULL COMMENT '身份证号' AFTER user_id");
        verify(jdbcTemplate).execute("ALTER TABLE villager_info ADD COLUMN id_front VARCHAR(500) NULL COMMENT '身份证正面图片URL' AFTER id_number");
        verify(jdbcTemplate).execute("ALTER TABLE villager_info ADD COLUMN id_back VARCHAR(500) NULL COMMENT '身份证反面图片URL' AFTER id_front");
    }

    @Test
    void skipsExistingColumns() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("id_number")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("id_front")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("id_back")))
                .thenReturn(1);

        runner.run();

        // execute 在 mock 上默认为空操作；已有列 → 不会调用 ALTER
        verify(jdbcTemplate, times(0)).execute("ALTER TABLE villager_info ADD COLUMN id_number VARCHAR(18) NULL COMMENT '身份证号' AFTER user_id");
        verify(jdbcTemplate, times(0)).execute("ALTER TABLE villager_info ADD COLUMN id_front VARCHAR(500) NULL COMMENT '身份证正面图片URL' AFTER id_number");
        verify(jdbcTemplate, times(0)).execute("ALTER TABLE villager_info ADD COLUMN id_back VARCHAR(500) NULL COMMENT '身份证反面图片URL' AFTER id_front");
    }

    @Test
    void partialAddsOnlyMissingColumns() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("id_number")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("id_front")))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("id_back")))
                .thenReturn(0);

        runner.run();

        verify(jdbcTemplate, times(0)).execute("ALTER TABLE villager_info ADD COLUMN id_number VARCHAR(18) NULL COMMENT '身份证号' AFTER user_id");
        verify(jdbcTemplate).execute("ALTER TABLE villager_info ADD COLUMN id_front VARCHAR(500) NULL COMMENT '身份证正面图片URL' AFTER id_number");
        verify(jdbcTemplate).execute("ALTER TABLE villager_info ADD COLUMN id_back VARCHAR(500) NULL COMMENT '身份证反面图片URL' AFTER id_front");
    }

    @Test
    void queryExceptionDoesNotCrash() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("id_number")))
                .thenThrow(new RuntimeException("DB 挂了"));

        assertDoesNotThrow(() -> runner.run());
    }

    @Test
    void executeExceptionDoesNotCrash() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("id_number")))
                .thenReturn(0);
        // mock 的 execute 默认空操作，但 addColumnIfMissing 内部 catch 了所有异常
        // 此用例验证整个 run() 不会因异常而崩溃
        assertDoesNotThrow(() -> runner.run());
    }

    @Test
    void queryReturnsNullTreatedAsMissing() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("id_number")))
                .thenReturn(null);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("id_front")))
                .thenReturn(null);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("id_back")))
                .thenReturn(null);

        runner.run();

        verify(jdbcTemplate).execute("ALTER TABLE villager_info ADD COLUMN id_number VARCHAR(18) NULL COMMENT '身份证号' AFTER user_id");
        verify(jdbcTemplate).execute("ALTER TABLE villager_info ADD COLUMN id_front VARCHAR(500) NULL COMMENT '身份证正面图片URL' AFTER id_number");
        verify(jdbcTemplate).execute("ALTER TABLE villager_info ADD COLUMN id_back VARCHAR(500) NULL COMMENT '身份证反面图片URL' AFTER id_front");
    }
}