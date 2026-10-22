package com.xiangcetong.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TownAdminFixRunner 单元测试：启动时自动修复镇管数据和清理僵尸申请。
 */
class TownAdminFixRunnerTest {

    private JdbcTemplate jdbcTemplate;
    private TownAdminFixRunner runner;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        runner = new TownAdminFixRunner(jdbcTemplate);
    }

    @Test
    void runDeletesDuplicateTownAdmin() throws Exception {
        runner.run();

        verify(jdbcTemplate, times(1)).update(
                eq("DELETE FROM sys_user WHERE phone = '13800000031' AND role = 'TOWN_ADMIN'"));
    }

    @Test
    void runUpdatesQianQiTownCode() throws Exception {
        when(jdbcTemplate.update(
                eq("UPDATE sys_user SET town_code = '正定镇' WHERE phone = '13800000021' AND role = 'TOWN_ADMIN'")))
                .thenReturn(1);

        runner.run();

        verify(jdbcTemplate).update(
                eq("UPDATE sys_user SET town_code = '正定镇' WHERE phone = '13800000021' AND role = 'TOWN_ADMIN'"));
    }

    @Test
    void runCleansOrphanedApplications() throws Exception {
        when(jdbcTemplate.update(
                "DELETE FROM subsidy_apply " +
                "WHERE policy_id IS NOT NULL " +
                "  AND policy_id NOT IN (SELECT id FROM subsidy_policy)"))
                .thenReturn(5);

        runner.run();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        // verify that the cleanup SQL was executed
        verify(jdbcTemplate, times(3)).update(sqlCaptor.capture());
        // 第三条应该是清理僵尸申请的 SQL
        String cleanupSql = sqlCaptor.getAllValues().get(2);
        assertTrue(cleanupSql.contains("DELETE FROM subsidy_apply"), "应执行僵尸申请清理 SQL");
        assertTrue(cleanupSql.contains("NOT IN (SELECT id FROM subsidy_policy)"), "应过滤不存在的政策ID");
    }

    @Test
    void runExceptionDoesNotCrash() throws Exception {
        when(jdbcTemplate.update(anyString())).thenThrow(new RuntimeException("DB 挂了"));

        runner.run();
        // 吞掉异常不崩启动
    }

    @Test
    void runNoOrphans_noCleanupLogButStillRunsOtherFixes() throws Exception {
        when(jdbcTemplate.update(
                "DELETE FROM subsidy_apply " +
                "WHERE policy_id IS NOT NULL " +
                "  AND policy_id NOT IN (SELECT id FROM subsidy_policy)"))
                .thenReturn(0);

        runner.run();

        // 三条 update 都应该执行（删除周八、修正钱七、清理僵尸）
        verify(jdbcTemplate, times(3)).update(anyString());
    }

    @Test
    void runDoesNotBreakWhenDuplicateAlreadyDeleted() throws Exception {
        // 周八已不存在，DELETE 影响 0 行 → 不报错
        when(jdbcTemplate.update(anyString())).thenReturn(0);

        runner.run();

        assertEquals(3, verifyUpdateCallCount(), "应始终执行 3 条修复 SQL");
    }

    private int verifyUpdateCallCount() {
        // 用 ArgumentCaptor 统计调用次数
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(3)).update(captor.capture());
        return captor.getAllValues().size();
    }
}