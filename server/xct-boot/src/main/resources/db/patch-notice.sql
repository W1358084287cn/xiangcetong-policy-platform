-- 乡策通：通知公告表与演示数据（可重复执行，禁止删库）
-- 管理员后台发布通知，村民 H5 首页滚动字幕展示

SET NAMES utf8mb4;

-- 通知公告表：status 仅 ONLINE 上线 / OFFLINE 下线
CREATE TABLE IF NOT EXISTS notice (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128) NOT NULL COMMENT '通知标题',
    content VARCHAR(2048) DEFAULT NULL COMMENT '通知正文',
    status VARCHAR(16) NOT NULL DEFAULT 'ONLINE' COMMENT 'ONLINE 上线 / OFFLINE 下线',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序值，越大越靠前',
    created_by BIGINT DEFAULT NULL COMMENT '发布人（sys_user.id）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 演示通知 ①：按 title 判重，幂等插入
INSERT INTO notice (title, content, status, sort)
SELECT '2026年耕地地力保护补贴申报开始，请符合条件的农户尽快在线申报',
       '2026年耕地地力保护补贴申报通道已开启，请符合条件的农户登录乡策通平台，按要求在线提交申报材料。',
       'ONLINE',
       100
WHERE NOT EXISTS (
    SELECT 1 FROM notice WHERE title = '2026年耕地地力保护补贴申报开始，请符合条件的农户尽快在线申报'
);

-- 演示通知 ②：按 title 判重，幂等插入
INSERT INTO notice (title, content, status, sort)
SELECT '乡策通平台上线通知：政策匹配、在线申报、进度查询一站式办理',
       '乡策通助农扶持政策管理平台已上线，提供政策智能匹配、在线申报、进度查询等一站式服务，欢迎使用。',
       'ONLINE',
       90
WHERE NOT EXISTS (
    SELECT 1 FROM notice WHERE title = '乡策通平台上线通知：政策匹配、在线申报、进度查询一站式办理'
);
