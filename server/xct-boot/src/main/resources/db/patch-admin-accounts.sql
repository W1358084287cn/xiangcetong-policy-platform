-- 管理端账号扩展补丁（幂等，可重复执行，禁止删库重建）
-- 作用：给 sys_user 增加 username 列，并让三个管理账号（村管/镇管/系统）统一
-- 以 username='admin' + password='123456' 登录；登录接口按 username+role 定位账号。

SET NAMES utf8mb4;

-- 1) 若 sys_user 不存在 username 列则新增（information_schema 判断，避免重复执行报错）
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND COLUMN_NAME = 'username'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN username VARCHAR(64) DEFAULT NULL COMMENT ''登录账号（管理端统一为 admin）''',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 三个管理账号统一账号与初始密码（123456 的 BCrypt 哈希）
--    phone 定位：13800000011=村管 / 13800000021=镇管 / 13800000099=系统
UPDATE sys_user
SET username = 'admin',
    password_hash = '$2b$10$1yBJIQBKfO4ryCvcbzN40eRNH27C8daiQFGXbYRIySmAOSIEBtdqy'
WHERE phone IN ('13800000011', '13800000021', '13800000099');
