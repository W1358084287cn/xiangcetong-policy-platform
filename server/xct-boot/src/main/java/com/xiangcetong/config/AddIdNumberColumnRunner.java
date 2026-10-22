package com.xiangcetong.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时自动补齐 villager_info 表缺失列（如不存在）。
 * 避免手动执行 ALTER TABLE，支持增量升级。
 */
@Component
public class AddIdNumberColumnRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AddIdNumberColumnRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public AddIdNumberColumnRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        addColumnIfMissing("id_number", "VARCHAR(18) NULL COMMENT '身份证号'", "AFTER user_id");
        addColumnIfMissing("id_front", "VARCHAR(500) NULL COMMENT '身份证正面图片URL'", "AFTER id_number");
        addColumnIfMissing("id_back", "VARCHAR(500) NULL COMMENT '身份证反面图片URL'", "AFTER id_front");
    }

    private void addColumnIfMissing(String colName, String colDef, String afterClause) {
        String fullCol = "villager_info." + colName;
        log.info("检查 {} 列是否存在...", fullCol);
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() " +
                "AND TABLE_NAME = 'villager_info' " +
                "AND COLUMN_NAME = ?",
                Integer.class, colName
            );
            if (cnt != null && cnt > 0) {
                log.info("{} 列已存在，跳过", fullCol);
                return;
            }
            String sql = "ALTER TABLE villager_info ADD COLUMN " + colName + " " + colDef + " " + afterClause;
            jdbcTemplate.execute(sql);
            log.info("已添加 {}", fullCol);
        } catch (Exception e) {
            log.error("添加 {} 列失败：{}", fullCol, e.getMessage(), e);
        }
    }
}