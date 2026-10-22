# 乡策通｜村民端「政策匹配」方案（MySQL 多条件 WHERE）

> **版本**：2.0  
> **日期**：2026-10-08  
> **状态**：已落地（替代原 WRS 加权打分）  
> **上游依赖**：[`docs/01-mvp-requirements.md`](./01-mvp-requirements.md)、[`docs/02-architecture.md`](./02-architecture.md)、[`docs/03-api.md`](./03-api.md)  
> **铁律**：Spring Boot + MySQL；禁止重型 ML；库表仅增量 `ALTER`，禁止删库重建。

---

## 0. 设计目标

1. 村民端 **不能** 直接刷全量上架政策；须按本人资料筛选「适合」的政策。  
2. 匹配逻辑用 **MySQL 多条件 WHERE**，易懂、易答辩、易维护。  
3. 政策某条件字段为空 / 标志未开启 → **不限**；有值则必须与村民对应字段满足。  
4. 默认只返回条件符合的政策；`includeNone=true` 时可附带「条件不符」项（不打分，仅标记）。

---

## 1. 选定方案：SQL 多条件过滤

**名称**：MySQL Profile WHERE Match（简称 PWM）

**一句话**：把村民画像参数传入 MyBatis，在 `subsidy_policy` 上用一组 `AND` 条件筛出全部满足的政策；不再做加权打分、不再有「基本匹配」中间档。

| 对比 | 原 WRS | 现 PWM |
|------|--------|--------|
| 核心 | Java 内存加权评分 | MySQL `WHERE` |
| 结果 | FULL / PARTIAL / NONE + 0～100 分 | FULL（条件符合）/ NONE（条件不符） |
| 可解释 | 维度权重条 | 条件勾选清单 `scoreDetail.checks` |

落点：

| 职责 | 位置 |
|------|------|
| WHERE 语句 | `SubsidyPolicyMapper.xml` → `listMatchByProfile` / `countMatchOne` |
| 资料门禁 + 归一化种养/主体 | `PolicyMatchService` |
| H5 入口 | `GET /api/policy/match`（仅 VILLAGER） |

---

## 2. 村民资料（匹配输入）

七步完善（H5 `profileGate` / `ProfileForm`）：

| 步 | 字段 |
|----|------|
| 地区 | `countyName` `townName` `villageName` |
| 经营主体 | `subjectType` `laborCount` `farmYears` |
| 身份 | `isVeteranFamily` `isMonitorHousehold` |
| 种养 | `plantType`（可选 `secondaryPlantType`） |
| 规模 | `plantArea` `irrigatedArea` `hasLandCert`；养殖类另需 `livestockType` `livestockCount` |
| 设施意向 | `needMachinery`（=1 时 `machineryType`）`hasGrainInsurance` `hasManureFacility` |
| 银行卡 | `bankCard` `bankName` |

**资料不全** → `42202`，`data.missingFields[]`（与上表对齐）。

种养归一化（Java，写入 SQL 前）：小麦/玉米/水稻/大豆等 → `RICE`；畜禽 → `LIVESTOCK`；等。

---

## 3. 政策条件字段（WHERE 右侧）

| 政策列 | 村民对照 | 规则 |
|--------|----------|------|
| `status` / `valid_from` / `valid_to` | — | 须 ONLINE 且在有效期 |
| `target_plant_types` JSON | 归一化 `plantCode` | 空=不限；否则 `JSON_CONTAINS` |
| `region_codes` JSON | 县/镇/村 | 空=不限；有 villages 优先，否则 towns，否则 counties |
| `min_area` | `plantArea` | 空或≤0 不限；否则 `plantArea >= min_area` |
| `require_bank_card` | 是否有卡 | =1 时须已绑卡 |
| `target_subject_types` JSON | `subjectType` | 空=不限；否则 `JSON_CONTAINS` |
| `require_veteran` | `isVeteranFamily` | =1 时须为 1 |
| `require_monitor` | `isMonitorHousehold` | =1 时须为 1 |
| `min_livestock` | `livestockCount` | 空或≤0 不限；否则存栏达标 |
| `require_manure_facility` | `hasManureFacility` | =1 时须为 1 |
| `require_machinery_intent` | `needMachinery` | =1 时须为 1 |
| `require_grain_insurance` | `hasGrainInsurance` | =1 时须为 1 |
| `require_land_cert` | `hasLandCert` | =1 时须为 1 |

用户二次筛选：`category`、`plantType`（归一化后）、`townName` 再叠 `AND`。

增量 DDL / 演示打标：`scripts/_patch_profile_match_v2.py`、`server/.../patch-profile-match-v2.sql`。

---

## 4. 接口行为

`GET /api/policy/match`

- 成功：`list[]` 每项含 `matchLevel=FULL|NONE`、`matchLevelLabel=条件符合|条件不符`、`score=100|0`、`scoreDetail.checks[]`、`reasons[]`。  
- 默认隐藏 NONE；`includeNone=true` 时附带未命中上架政策。  
- 详情 `myMatch`：对单条政策跑 `countMatchOne`。

错误码：`42202` 资料不完整；`40101` / `40301` 等同 `03-api`。

---

## 5. 验收要点

- [ ] 资料缺任一七步必填 → `42202` + `missingFields`
- [ ] `13800000001`（农户·小麦·购机意向·粮食保险·土地证）能命中农机购置/报废、耕地地力、粮油类等；义务兵（要求退役军人）不命中
- [ ] `13800000002`（家庭农场·生猪 80·粪污）能命中畜禽粪污（min_livestock=50）
- [ ] 关闭「显示暂不符合」时列表无 NONE
- [ ] H5 详情展示条件勾选清单，无权重进度条

---

## 6. 变更说明（相对 v1 WRS）

- 删除：维度权重、阈值 FULL/PARTIAL、面积「接近」比例分。  
- 保留：村民主入口仍是匹配接口；Admin 结构化条件字段；增量 ALTER。  
- H5：列表/详情文案改为「条件符合/不符」。
