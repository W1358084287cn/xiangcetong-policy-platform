-- 申请发放扩展补丁（幂等，可重复执行，禁止删库重建）
-- 作用：给 subsidy_apply 增加发放金额 / 发放人 / 发放时间三列，
--       支撑「待发放 PENDING_DISBURSE → 已发放 DISBURSED」状态机。
-- 兼容旧数据：三列均为 NULL，历史 APPROVED 单据不受影响。

SET NAMES utf8mb4;

-- 1) disburse_amount DECIMAL(12,2)
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'subsidy_apply'
      AND COLUMN_NAME = 'disburse_amount'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE subsidy_apply ADD COLUMN disburse_amount DECIMAL(12,2) DEFAULT NULL COMMENT ''发放金额''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) disburse_by BIGINT
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'subsidy_apply'
      AND COLUMN_NAME = 'disburse_by'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE subsidy_apply ADD COLUMN disburse_by BIGINT DEFAULT NULL COMMENT ''发放人（sys_user.id）''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) disburse_at DATETIME
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'subsidy_apply'
      AND COLUMN_NAME = 'disburse_at'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE subsidy_apply ADD COLUMN disburse_at DATETIME DEFAULT NULL COMMENT ''发放时间''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
