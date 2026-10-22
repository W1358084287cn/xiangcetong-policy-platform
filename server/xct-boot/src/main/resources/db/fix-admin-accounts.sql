-- 修复管理端账号：设置 username='admin' + 密码 123456
SET NAMES utf8mb4;

-- 直接更新：username + password
UPDATE sys_user
SET username = 'admin',
    password_hash = '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW'
WHERE role IN ('VILLAGE_ADMIN', 'TOWN_ADMIN', 'SYS_ADMIN');