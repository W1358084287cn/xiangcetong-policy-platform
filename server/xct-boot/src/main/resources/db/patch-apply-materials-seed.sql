-- 申报材料/收藏相关补丁（可重复执行，禁止删库）
-- consult_info / apply_conditions / material_schema 均为 JSON 列，须写入合法 JSON。
-- 推荐本机执行：python scripts/_apply_materials_seed.py

SET NAMES utf8mb4;

UPDATE villager_info vi
INNER JOIN sys_user u ON u.id = vi.user_id
SET vi.bank_name = '中国农业银行'
WHERE u.phone IN ('13800000001', '13800000002');

-- 完整 INSERT 见 scripts/_apply_materials_seed.py（避免 SQL 文件中超长 JSON 转义出错）
-- 为 GRAIN / MACHINERY / LIVESTOCK 类 ONLINE 政策补 schema：
--   python scripts/_patch_policy_schemas.py
-- 河北官网向政策正文/电话/地区（正定县）：
--   python scripts/_seed_hebei_policies.py
