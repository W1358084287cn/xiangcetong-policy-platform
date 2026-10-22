-- 乡策通：为已有本地库补齐/校正政策匹配演示数据（可重复执行，禁止删库）
-- 兼容两套标题：旧演示「2026年种粮大户补贴」与官网向种子「粮油规模种植主体单产提升奖补」等

SET NAMES utf8mb4;

-- 张三：水稻 40 亩（对 min_area=50 的粮食政策 → 期望 PARTIAL）
UPDATE villager_info vi
INNER JOIN sys_user u ON u.id = vi.user_id
SET vi.plant_type = '水稻种植',
    vi.plant_area = 40.00,
    vi.county_name = IFNULL(NULLIF(vi.county_name, ''), '示例县'),
    vi.town_name = IFNULL(NULLIF(vi.town_name, ''), '示例镇'),
    vi.village_name = IFNULL(NULLIF(vi.village_name, ''), '东村'),
    vi.updated_at = NOW()
WHERE u.phone = '13800000001';

-- 李四：畜禽养殖（粮食类种养不命中 → NONE；畜牧类可 FULL）
UPDATE villager_info vi
INNER JOIN sys_user u ON u.id = vi.user_id
SET vi.plant_type = '畜禽养殖',
    vi.plant_area = 20.00,
    vi.county_name = IFNULL(NULLIF(vi.county_name, ''), '示例县'),
    vi.town_name = IFNULL(NULLIF(vi.town_name, ''), '示例镇'),
    vi.village_name = IFNULL(NULLIF(vi.village_name, ''), '西村'),
    vi.updated_at = NOW()
WHERE u.phone = '13800000002';

-- 旧演示标题（若存在）
UPDATE subsidy_policy
SET category = 'GRAIN',
    target_plant_types = '["RICE"]',
    min_area = 50.00,
    require_bank_card = 1,
    region_codes = '{"counties":["示例县"],"towns":["示例镇"],"villages":[]}',
    valid_from = '2023-01-01',
    valid_to = '2026-12-31',
    updated_at = NOW()
WHERE title = '2026年种粮大户补贴';

UPDATE subsidy_policy
SET category = 'MACHINERY',
    target_plant_types = NULL,
    min_area = NULL,
    require_bank_card = 0,
    region_codes = '{"counties":["示例县"],"towns":["示例镇"],"villages":[]}',
    valid_from = '2023-01-01',
    valid_to = '2026-12-31',
    updated_at = NOW()
WHERE title = '2026年农机购置补贴';

-- 官网向种子：面积门槛 50 亩的粮油奖补（张三 40 亩 → PARTIAL）
UPDATE subsidy_policy
SET category = 'GRAIN',
    target_plant_types = '["RICE"]',
    min_area = 50.00,
    require_bank_card = 1,
    region_codes = '{"counties":["示例县"],"towns":["示例镇"],"villages":[]}',
    valid_from = IFNULL(valid_from, '2023-01-01'),
    valid_to = IFNULL(valid_to, '2026-12-31'),
    updated_at = NOW()
WHERE title LIKE '%粮油规模种植主体单产提升%';

-- 畜牧类：仅畜禽
UPDATE subsidy_policy
SET category = 'LIVESTOCK',
    target_plant_types = '["LIVESTOCK"]',
    require_bank_card = 1,
    region_codes = '{"counties":["示例县"],"towns":["示例镇"],"villages":[]}',
    updated_at = NOW()
WHERE title LIKE '%畜禽养殖%' OR category = 'LIVESTOCK';

-- 义务兵优待金 → 社保优抚类（不属于种养/畜牧/农机）
UPDATE subsidy_policy
SET category = 'SOCIAL',
    target_plant_types = NULL,
    min_area = NULL,
    min_livestock = NULL,
    require_bank_card = 0,
    require_veteran = 1,
    region_codes = '{"counties":["示例县"],"towns":[],"villages":[]}',
    updated_at = NOW()
WHERE title LIKE '%义务兵%' OR category = 'SOCIAL';