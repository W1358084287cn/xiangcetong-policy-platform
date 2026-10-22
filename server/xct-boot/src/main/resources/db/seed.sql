-- 乡策通演示种子数据（可重复执行）
-- 密码明文 Demo@123，以下为 BCrypt 哈希

SET NAMES utf8mb4;

-- 先清掉演示账号相关申请/日志/村民信息，再删用户（避免外键或孤儿记录）
DELETE FROM apply_flow_log
WHERE apply_id IN (
    SELECT id FROM (
        SELECT a.id FROM subsidy_apply a
        INNER JOIN sys_user u ON u.id = a.user_id
        WHERE u.phone IN (
            '13800000001','13800000002','13800000011','13800000012','13800000021','13800000099'
        )
    ) t
);

DELETE FROM subsidy_apply
WHERE user_id IN (
    SELECT id FROM (
        SELECT id FROM sys_user WHERE phone IN (
            '13800000001','13800000002','13800000011','13800000012','13800000021','13800000099'
        )
    ) t
);

DELETE FROM operation_log
WHERE user_id IN (
    SELECT id FROM (
        SELECT id FROM sys_user WHERE phone IN (
            '13800000001','13800000002','13800000011','13800000012','13800000021','13800000099'
        )
    ) t
);

DELETE FROM villager_info
WHERE user_id IN (
    SELECT id FROM (
        SELECT id FROM sys_user WHERE phone IN (
            '13800000001','13800000002','13800000011','13800000012','13800000021','13800000099'
        )
    ) t
);

DELETE FROM sys_user WHERE phone IN (
    '13800000001','13800000002','13800000011','13800000012','13800000021','13800000099'
);

-- Demo@123 的 BCrypt
SET @pwd := '$2b$10$n8UqFY5czXyO6bnjs0MA0enis2XjkL9zVyRCOWicGsfZ.2xTX86oy';

INSERT INTO sys_user (phone, password_hash, name, role, county_code, town_code, village_code, created_at) VALUES
('13800000001', @pwd, '张三', 'VILLAGER', '示例县', '示例镇', '东村', NOW()),
('13800000002', @pwd, '李四', 'VILLAGER', '示例县', '示例镇', '西村', NOW()),
('13800000011', @pwd, '王五', 'VILLAGE_ADMIN', '示例县', '示例镇', '东村', NOW()),
('13800000012', @pwd, '赵六', 'VILLAGE_ADMIN', '示例县', '示例镇', '西村', NOW()),
('13800000021', @pwd, '钱七', 'TOWN_ADMIN', '示例县', '示例镇', NULL, NOW()),
('13800000099', @pwd, '系统管理员', 'SYS_ADMIN', '示例县', '示例镇', NULL, NOW());

INSERT INTO villager_info (user_id, county_name, town_name, village_name, plant_type, bank_card, bank_name, plant_area, updated_at)
SELECT id, '示例县', '示例镇', '东村', '水稻种植', '6222021234561234', '中国农业银行', 40.00, NOW()
FROM sys_user WHERE phone = '13800000001';

INSERT INTO villager_info (user_id, county_name, town_name, village_name, plant_type, bank_card, bank_name, plant_area, updated_at)
SELECT id, '示例县', '示例镇', '西村', '畜禽养殖', '6222029876544321', '中国农业银行', 20.00, NOW()
FROM sys_user WHERE phone = '13800000002';

-- 演示政策：按标题清理后再插，保证至少 2 条 ONLINE（带结构化匹配字段）
DELETE FROM policy_favorite
WHERE policy_id IN (
    SELECT id FROM (
        SELECT id FROM subsidy_policy WHERE title IN (
            '2026年种粮大户补贴',
            '2026年农机购置补贴',
            '义务兵家庭优待金',
            '（演示）已下架政策'
        )
    ) t
);

DELETE FROM subsidy_policy WHERE title IN (
    '2026年种粮大户补贴',
    '2026年农机购置补贴',
    '义务兵家庭优待金',
    '（演示）已下架政策'
);

INSERT INTO subsidy_policy (
    title, content, conditions, standard, consult_phone, status,
    category, target_plant_types, min_area, require_bank_card, region_codes,
    valid_from, valid_to, material_schema,
    created_at, updated_at
) VALUES
('2026年种粮大户补贴',
 '对达到一定种植规模的种粮大户给予亩均补贴，鼓励稳定粮食生产。',
 '本镇户籍；种植面积≥50亩；材料齐全。',
 '每亩200元',
 '0571-12345678',
 'ONLINE',
 'GRAIN',
 '["RICE"]',
 50.00,
 1,
 '{"counties":["示例县"],"towns":["示例镇"],"villages":[]}',
 '2023-01-01',
 '2026-12-31',
 '{"materials":[{"code":"id_card","title":"身份证明","desc":"请上传身份证正反面","fields":[{"key":"id_front","type":"photo","required":true,"label":"身份证正面"},{"key":"id_back","type":"photo","required":true,"label":"身份证反面"},{"key":"name","type":"text","required":true,"label":"姓名"},{"key":"id_number","type":"text","required":true,"label":"身份证号"}]},{"code":"land_proof","title":"土地证明","desc":"土地承包合同或村委会出具的种植面积证明","fields":[{"key":"land_contract","type":"photo","required":true,"label":"土地承包合同或证明"},{"key":"plant_area","type":"number","required":true,"label":"种植面积（亩）"}]},{"code":"bank_card","title":"银行卡信息","desc":"用于接收补贴款项","fields":[{"key":"bank_card_number","type":"text","required":true,"label":"银行卡号"},{"key":"bank_name","type":"text","required":true,"label":"开户行名称"}]}]}',
 NOW(), NOW()),
('2026年农机购置补贴',
 '对购置目录内农业机械的农户给予定额补贴。',
 '本镇农业经营主体；购置发票齐全；机具已入库登记。',
 '按机具类型定额补贴',
 '0571-87654321',
 'ONLINE',
 'MACHINERY',
 NULL,
 NULL,
 0,
 '{"counties":["示例县"],"towns":["示例镇"],"villages":[]}',
 '2023-01-01',
 '2026-12-31',
 '{"materials":[{"code":"id_card","title":"身份证明","desc":"请上传身份证正反面","fields":[{"key":"id_front","type":"photo","required":true,"label":"身份证正面"},{"key":"id_back","type":"photo","required":true,"label":"身份证反面"},{"key":"name","type":"text","required":true,"label":"姓名"},{"key":"id_number","type":"text","required":true,"label":"身份证号"}]},{"code":"machine_invoice","title":"购机证明","desc":"购机发票和机具合格证","fields":[{"key":"invoice","type":"photo","required":true,"label":"购机发票"},{"key":"certificate","type":"photo","required":true,"label":"机具合格证"}]},{"code":"machine_info","title":"机具信息","desc":"填写所购机具的基本信息","fields":[{"key":"machine_name","type":"text","required":true,"label":"机具名称"},{"key":"machine_model","type":"text","required":true,"label":"机具型号"},{"key":"purchase_date","type":"date","required":true,"label":"购买日期"}]},{"code":"bank_card","title":"银行卡信息","desc":"用于接收补贴款项","fields":[{"key":"bank_card_number","type":"text","required":true,"label":"银行卡号"},{"key":"bank_name","type":"text","required":true,"label":"开户行名称"}]}]}',
 NOW(), NOW());

-- 义务兵家庭优待金：完整材料 Schema（见 patch-apply-materials-seed.sql，此处精简插入基础行后由 patch 补全亦可）
-- 完整种子请执行 patch-apply-materials-seed.sql

-- 张三：1 条待初审
INSERT INTO subsidy_apply (user_id, policy_id, material_urls, status, village_code, town_code, created_at, updated_at)
SELECT u.id, p.id, '["/uploads/demo/pending-village.jpg"]', 'PENDING_VILLAGE', '东村', '示例镇', NOW(), NOW()
FROM sys_user u
CROSS JOIN subsidy_policy p
WHERE u.phone = '13800000001' AND p.title = '2026年种粮大户补贴'
LIMIT 1;

SET @apply_pending_village := LAST_INSERT_ID();

INSERT INTO apply_flow_log (apply_id, from_status, to_status, operator_id, opinion, created_at)
SELECT @apply_pending_village, NULL, 'PENDING_VILLAGE', u.id, '提交申请', NOW()
FROM sys_user u WHERE u.phone = '13800000001';

-- 张三：1 条待终审（已过初审）
INSERT INTO subsidy_apply (
    user_id, policy_id, material_urls, status, village_code, town_code,
    first_by, first_opinion, first_at, created_at, updated_at
)
SELECT u.id, p.id, '["/uploads/demo/pending-town.jpg"]', 'PENDING_TOWN', '东村', '示例镇',
       va.id, '材料齐全，同意上报', NOW(), NOW(), NOW()
FROM sys_user u
CROSS JOIN subsidy_policy p
CROSS JOIN sys_user va
WHERE u.phone = '13800000001'
  AND p.title = '2026年农机购置补贴'
  AND va.phone = '13800000011'
LIMIT 1;

SET @apply_pending_town := LAST_INSERT_ID();

INSERT INTO apply_flow_log (apply_id, from_status, to_status, operator_id, opinion, created_at)
SELECT @apply_pending_town, NULL, 'PENDING_VILLAGE', u.id, '提交申请', NOW()
FROM sys_user u WHERE u.phone = '13800000001';

INSERT INTO apply_flow_log (apply_id, from_status, to_status, operator_id, opinion, created_at)
SELECT @apply_pending_town, 'PENDING_VILLAGE', 'PENDING_TOWN', va.id, '材料齐全，同意上报', NOW()
FROM sys_user va WHERE va.phone = '13800000011';