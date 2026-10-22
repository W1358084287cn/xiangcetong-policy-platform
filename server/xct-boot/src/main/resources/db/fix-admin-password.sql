-- 修复管理端 admin 账号密码（123456）
-- 此 BCrypt 哈希由 Spring BCryptPasswordEncoder 对明文 "123456" 生成
SET NAMES utf8mb4;

UPDATE sys_user
SET password_hash = '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW'
WHERE username = 'admin'
  AND role IN ('VILLAGE_ADMIN', 'TOWN_ADMIN', 'SYS_ADMIN');