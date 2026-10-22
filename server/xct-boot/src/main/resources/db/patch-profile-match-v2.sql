-- 村民精细画像 + 政策匹配扩展条件（增量可空，禁止删库）
-- 可重复执行：列已存在时跳过由脚本侧处理；本文件供人工对照

-- villager_info 精细资料
ALTER TABLE villager_info
  ADD COLUMN subject_type VARCHAR(32) NULL COMMENT '经营主体：FARMER/FAMILY_FARM/COOP/ENTERPRISE',
  ADD COLUMN labor_count INT NULL COMMENT '家庭劳动力人数',
  ADD COLUMN farm_years INT NULL COMMENT '从业年限',
  ADD COLUMN is_veteran_family TINYINT(1) NULL COMMENT '是否退役军人家庭 0/1',
  ADD COLUMN is_monitor_household TINYINT(1) NULL COMMENT '是否脱贫监测户 0/1',
  ADD COLUMN secondary_plant_type VARCHAR(64) NULL COMMENT '兼营种养类型',
  ADD COLUMN irrigated_area DECIMAL(10,2) NULL COMMENT '水浇地面积（亩）',
  ADD COLUMN has_land_cert TINYINT(1) NULL COMMENT '是否有土地承包经营权证 0/1',
  ADD COLUMN livestock_type VARCHAR(64) NULL COMMENT '养殖细分：生猪/肉牛/奶牛/肉羊/蛋鸡/肉鸡等',
  ADD COLUMN livestock_count INT NULL COMMENT '存栏数量',
  ADD COLUMN has_manure_facility TINYINT(1) NULL COMMENT '是否有粪污处理设施 0/1',
  ADD COLUMN need_machinery TINYINT(1) NULL COMMENT '是否有购机或报废更新意向 0/1',
  ADD COLUMN machinery_type VARCHAR(64) NULL COMMENT '意向农机类型',
  ADD COLUMN has_grain_insurance TINYINT(1) NULL COMMENT '是否已参保粮食保险 0/1';

-- subsidy_policy 扩展匹配条件
ALTER TABLE subsidy_policy
  ADD COLUMN target_subject_types JSON NULL COMMENT '适用经营主体数组；空=不限',
  ADD COLUMN require_veteran TINYINT(1) NULL DEFAULT 0 COMMENT '是否要求退役军人家庭',
  ADD COLUMN require_monitor TINYINT(1) NULL DEFAULT 0 COMMENT '是否要求脱贫监测户',
  ADD COLUMN min_livestock INT NULL COMMENT '最低存栏；空=不限',
  ADD COLUMN require_manure_facility TINYINT(1) NULL DEFAULT 0 COMMENT '是否要求粪污设施',
  ADD COLUMN require_machinery_intent TINYINT(1) NULL DEFAULT 0 COMMENT '是否要求购机/报废意向',
  ADD COLUMN require_grain_insurance TINYINT(1) NULL DEFAULT 0 COMMENT '是否要求已参保粮食保险',
  ADD COLUMN require_land_cert TINYINT(1) NULL DEFAULT 0 COMMENT '是否要求土地承包证';
