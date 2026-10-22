package com.xiangcetong.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时自动修复脏数据（幂等，每次启动执行）：
 * 1. 删除多余的"周八"镇管账号，避免同用户名+同角色导致登录不确定
 * 2. 将"钱七"的 town_code 修正为"正定镇"，确保能看到测试数据
 * 3. 清理引用已删除政策的僵尸申请记录，避免村民端显示空标题
 */
@Component
public class TownAdminFixRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TownAdminFixRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public TownAdminFixRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            // 删除多余的周八
            jdbcTemplate.update(
                "DELETE FROM sys_user WHERE phone = '13800000031' AND role = 'TOWN_ADMIN'"
            );

            // 修正钱七的 town_code 为正定镇
            int updated = jdbcTemplate.update(
                "UPDATE sys_user SET town_code = '正定镇' WHERE phone = '13800000021' AND role = 'TOWN_ADMIN'"
            );
            log.info("已将镇管 钱七(13800000021) 的 town_code 修正为 正定镇，影响行数：{}", updated);

            // 清理引用已删除政策的僵尸申请（policy_id 在 subsidy_policy 中不存在）
            int cleaned = jdbcTemplate.update(
                "DELETE FROM subsidy_apply " +
                "WHERE policy_id IS NOT NULL " +
                "  AND policy_id NOT IN (SELECT id FROM subsidy_policy)"
            );
            if (cleaned > 0) {
                log.info("已清理 {} 条引用已删除政策的僵尸申请", cleaned);
            }
        } catch (Exception e) {
            log.error("数据修复失败：{}", e.getMessage(), e);
        }
    }
}