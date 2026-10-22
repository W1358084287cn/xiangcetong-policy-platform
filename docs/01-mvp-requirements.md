# 乡策通｜最小可运行惠农补贴申报系统 — MVP 需求文档

> **版本**：2.0  
> **日期**：2026-10-09  
> **状态**：待下游拆解  
> **工作区**：`xiangcetong/`（`server` + `web-h5` + `web-admin` 三目录）

---

## 1. 背景与目标

实现「政策查阅 → 线上申报（手机拍照）→ 村初审 / 镇终审 → 结果查询」最小业务闭环，满足毕业设计演示与答辩。村民端采用 **H5 手机浏览器**访问（无需安装 APP）；村镇工作人员使用 **PC 管理端**审批与维护。

### 1.1 技术栈（已定，不可变更）

| 层级 | 技术 |
|------|------|
| 村民端 | Vue 3 + Vite + Vant 4（H5 适老化） |
| 管理端 | Vue 3 + Vite + Element Plus |
| 后端 | Spring Boot 3.x + MyBatis + Maven |
| 数据库 | MySQL 8，库名 **`xiangcetong`** |
| 缓存 | Redis（Token / 验证码 / 政策缓存） |
| 鉴权 | JWT + Redis 会话 |

### 1.2 数据库约定：沿用已有库表

**铁律：沿用 MySQL 库 `xiangcetong` 已有 6 张表，禁止要求删库重建，禁止擅自改表名、删字段或破坏性变更。**

| 表名 | 用途 |
|------|------|
| `sys_user` | 用户账号（手机号登录、角色、所属县/镇/村编码） |
| `villager_info` | 村民扩展信息（地址、种养类型、银行卡） |
| `subsidy_policy` | 惠农政策（标题、内容、申领条件、补贴标准、咨询电话、上下架状态） |
| `subsidy_apply` | 补贴申请主表（材料 URL、状态、初审/终审意见与时间） |
| `apply_flow_log` | 申请状态流转日志（from/to 状态、操作人、意见） |
| `operation_log` | 系统操作审计日志 |

若实现中发现字段不足，**仅允许**通过增量 `ALTER TABLE` 新增可空字段，且须单独评审；MVP 阶段优先用现有字段完成闭环。

### 1.3 角色与状态机

**四角色**（存于 `sys_user.role`）：

| 角色值 | 说明 |
|--------|------|
| `VILLAGER` | 村民，仅 H5 端 |
| `VILLAGE_ADMIN` | 村级管理员，PC 端，本村数据 + 初审 |
| `TOWN_ADMIN` | 镇级管理员，PC 端，待终审单据 + 终审 |
| `SYS_ADMIN` | 系统管理员，PC 端，用户/政策/日志维护，**不可审批** |

**申请状态**（存于 `subsidy_apply.status`）：

```
DRAFT  →  PENDING_VILLAGE  →  PENDING_TOWN  →  APPROVED
                 ↓                  ↓
              REJECTED           REJECTED
```

---

## 2. 用户故事

### 2.1 村民（H5）

| ID | 用户故事 |
|----|----------|
| US-V01 | 作为**村民**，我想用手机浏览器打开 H5 页面并用手机号登录，以便无需安装 APP 即可使用系统。 |
| US-V02 | 作为**村民**，我想维护县/乡镇/村、种养类型和银行卡号，以便申报时信息完整。 |
| US-V03 | 作为**村民**，我想浏览已上架的惠农政策列表并查看详情（条件、标准、咨询电话），以便判断是否可申报。 |
| US-V04 | 作为**村民**，我想选择政策、手机拍照上传材料并提交申请，以便在线完成补贴申报。 |
| US-V05 | 作为**村民**，我想在「我的补贴记录」中查看每条申请的状态与审批意见，以便知晓进度与驳回原因。 |
| US-V06 | 作为**村民**，我想收藏政策、按材料表单分步填写并保存草稿后再提交，以便中断后可继续申报。 |

### 2.2 村级管理员（PC）

| ID | 用户故事 |
|----|----------|
| US-VA01 | 作为**村级管理员**，我想登录 PC 后台并只看到本村村民提交的申请，以便按村管辖范围工作。 |
| US-VA02 | 作为**村级管理员**，我想对待初审单据执行通过或驳回并填写意见，以便完成村级审核环节。 |

### 2.3 镇级管理员（PC）

| ID | 用户故事 |
|----|----------|
| US-TA01 | 作为**镇级管理员**，我想只看到已通过初审、待终审的申请，以便聚焦终审任务。 |
| US-TA02 | 作为**镇级管理员**，我想查看申报材料与村级初审意见后执行终审通过或驳回，以便完成审批闭环。 |

### 2.4 系统管理员（PC）

| ID | 用户故事 |
|----|----------|
| US-SA01 | 作为**系统管理员**，我想管理村民与管理员账号及角色，以便维护系统用户体系。 |
| US-SA02 | 作为**系统管理员**，我想新增、编辑、下架惠农政策，以便村民端展示最新政策。 |
| US-SA03 | 作为**系统管理员**，我想查看操作日志与申请列表（只读），以便审计与运维，且**不能**执行任何审批操作。 |

---

## 3. 做与不做

### 3.1 MVP 必做

- 四角色 RBAC + 数据权限（村/镇隔离）
- 村民 H5：登录、个人信息、政策列表/详情、申报（拍照上传）、我的记录
- PC 管理端：登录、用户管理、政策 CRUD、补贴申请列表与两级审批、操作日志查看
- 申请状态机与 `apply_flow_log` 流转留痕
- 演示种子数据（含下文演示账号）
- 本地可运行：`server:8080`，`web-h5:5173`，`web-admin:5174`

### 3.2 明确不做（MVP 禁止范围）

- 不做独立 APP、小程序、短信/微信登录
- 不做注册、忘记密码、图形/滑块验证码（登录仅手机号 + 密码）
- 不做多级组织（县/市）、复杂工作流引擎、会签、加签
- 不做补贴金额自动核算、财政对接、银行代发
- 不做消息推送、站内信、邮件通知
- 不做数据大屏、BI 报表、Excel 批量导入导出
- 不做 OSS/云存储（材料存本地 `uploads/` 目录即可）
- 不做国际化、深色模式、复杂主题定制
- **系统管理员不得拥有审批按钮**（含接口层拦截）
- **不要求删库重建**；不得修改已有 6 表的核心语义

---

## 4. 演示账号约定

> 密码统一占位：**`Demo@123`**（BCrypt 入库，文档与种子脚本均使用该占位，禁止写入真实生产密码）。

| 手机号 | 角色 | 姓名（示例） | 所属范围（示例） | 用途 |
|--------|------|--------------|------------------|------|
| `13800000001` | `VILLAGER` | 张三 | 东村 | H5 申报演示 |
| `13800000002` | `VILLAGER` | 李四 | 西村 | 跨村数据隔离演示 |
| `13800000011` | `VILLAGE_ADMIN` | 王五 | 东村（`village_code=东村`） | 村级初审 |
| `13800000012` | `VILLAGE_ADMIN` | 赵六 | 西村 | 另一村初审（不可见东村单） |
| `13800000021` | `TOWN_ADMIN` | 钱七 | 本镇 | 镇级终审 |
| `13800000099` | `SYS_ADMIN` | 系统管理员 | 全镇 | 用户/政策/日志维护 |

种子数据须至少包含：2 条上架政策、张三 1 条待初审申请、1 条已进入待终审申请（供镇级演示）。

---

## 5. 验收标准（可勾选）

> 答辩演示路径：**张三 H5 登录 → 完善信息 → 浏览政策 → 提交申请 → 王五 PC 初审通过 → 钱七 PC 终审通过 → 张三 H5 查看已通过**。

### 5.1 环境与工程

- [ ] **AC-01**  WHEN 执行 `mvn -pl server spring-boot:run` THEN 后端在 `8080` 启动且无报错。
- [ ] **AC-02**  WHEN 分别在 `web-h5`、`web-admin` 执行 `npm run dev` THEN 页面可访问（5173 / 5174）。
- [ ] **AC-03**  WHEN 后端连接配置指向库 `xiangcetong` THEN 启动后无需删库重建即可读写已有 6 表。

### 5.2 认证与权限

- [ ] **AC-04**  WHEN 村民账号登录 H5 THEN 返回 JWT 且只能访问村民接口；访问 PC 管理接口返回 403。
- [ ] **AC-05**  WHEN 管理员账号登录 PC THEN 返回 JWT；村民账号登录 PC 返回 403。
- [ ] **AC-06**  WHEN 任意角色携带错误/过期 Token 请求受保护接口 THEN 返回 401。
- [ ] **AC-07**  WHEN `SYS_ADMIN` 调用初审/终审接口 THEN 返回 403（前后端均不可见审批按钮）。

### 5.3 村民 H5

- [ ] **AC-08**  WHEN 使用 `13800000001` / `Demo@123` 登录 H5 THEN 进入首页且字体/按钮满足适老化（字号 ≥16px，主按钮高度 ≥44px）。
- [ ] **AC-08a** WHEN 点击全局字号浮钮切换「标准 / 大字 / 特大」THEN Toast 反馈当前档，且首页标题、列表卡片、材料进度、Tab 标签均随档放大且无叠字。
- [ ] **AC-08b** WHEN 处于「特大」档 THEN 列表「去申报」、材料「填写/预览」等主路径操作热区高度 ≥48px；收藏等图标热区 ≥44px。
- [ ] **AC-08c** WHEN 资料未齐点击首页主 CTA「立即匹配补贴」THEN Toast 口语说明缺项并进入资料向导；首页仅保留一条主匹配 CTA（完善资料走进度卡/次按钮，不与主 CTA 同 gate）。
- [ ] **AC-08d** WHEN 村民提交申报 / 退出登录 / 驳回后再申 THEN 均有二次确认对话框。
- [ ] **AC-08e** WHEN 点击「高对比」浮钮 THEN Toast 反馈开关状态，页面切为浅黄底黑字（`data-contrast=high`），再次点击恢复；不提供语音播报。
- [ ] **AC-09**  WHEN 村民保存个人信息（县/乡镇/村、种养类型、银行卡）THEN 数据写入 `villager_info` 且再次打开可回显。
- [ ] **AC-10**  WHEN 村民打开政策列表 THEN 仅展示 `subsidy_policy.status=ONLINE` 的政策。
- [ ] **AC-11**  WHEN 村民查看政策详情 THEN 展示标题、内容、申领条件、补贴标准、咨询电话。
- [ ] **AC-12**  WHEN 村民选择政策并上传 ≥1 张图片后提交 THEN 生成 `subsidy_apply` 记录，`status=PENDING_VILLAGE`，并写入 `apply_flow_log`。
- [ ] **AC-13**  WHEN 村民打开「我的补贴记录」THEN 仅展示本人申请，含状态中文展示与审批意见。

### 5.4 村级审批（PC）

- [ ] **AC-14**  WHEN 东村管理员 `13800000011` 打开申请列表 THEN 仅见 `village_code=东村` 的单据，不见西村单据。
- [ ] **AC-15**  WHEN 村级管理员对 `PENDING_VILLAGE` 单据点击「初审通过」并填写意见 THEN 状态变为 `PENDING_TOWN`，记录 `first_by/first_opinion/first_at` 与流转日志。
- [ ] **AC-16**  WHEN 村级管理员点击「初审驳回」并填写意见 THEN 状态变为 `REJECTED`，村民 H5 可见驳回原因。

### 5.5 镇级审批（PC）

- [ ] **AC-17**  WHEN 镇级管理员打开申请列表 THEN 仅见 `status=PENDING_TOWN` 的单据，不见 `PENDING_VILLAGE` 单据。
- [ ] **AC-18**  WHEN 镇级管理员「终审通过」并填写意见 THEN 状态变为 `APPROVED`，记录 `final_by/final_opinion/final_at` 与流转日志。
- [ ] **AC-19**  WHEN 镇级管理员「终审驳回」并填写意见 THEN 状态变为 `REJECTED`，村民 H5 可见终审意见。

### 5.6 系统管理（PC）

- [ ] **AC-20**  WHEN `SYS_ADMIN` 新增/编辑/下架政策 THEN `subsidy_policy` 正确更新；下架后 H5 列表不可见。
- [ ] **AC-21**  WHEN `SYS_ADMIN` 维护用户 THEN 可创建四角色账号并绑定 `county_code/town_code/village_code`。
- [ ] **AC-22**  WHEN `SYS_ADMIN` 查看操作日志 THEN 展示 `operation_log` 最近记录（登录、政策变更、审批等关键操作）。
- [ ] **AC-23**  WHEN `SYS_ADMIN` 查看申请列表 THEN 为只读，无通过/驳回操作入口。

### 5.7 非功能（最小）

- [ ] **AC-24**  WHEN 接口发生业务错误 THEN 返回统一 JSON（含 `code`、`message`），前端可 Toast 展示。
- [ ] **AC-25**  WHEN 上传图片 THEN 支持 JPG/PNG，单张 ≤5MB，材料 URL 写入 `subsidy_apply.material_urls`（JSON 数组字符串）。

**验收条目合计：25 条**

---

## 6. 下游任务（四要素）

> 一次只派一个主责 Agent；下列为推荐顺序：**architect → api-designer → backend / frontend（backend 先行）**。

---

### 6.1 主责：architect

#### 交付物

- `docs/02-architecture.md`：三工程目录结构、模块划分、部署拓扑、JWT+Redis 鉴权时序、数据权限过滤策略、文件上传方案、状态机说明。
- 模块边界图（可 Mermaid）：auth / villager / policy / apply / audit / admin。

#### 验收标准

- [ ] **AR-01** 文档明确 `server`、`web-h5`、`web-admin` 职责与端口。
- [ ] **AR-02** 文档声明 **沿用 `xiangcetong` 已有 6 表**，并给出实体与表字段映射表。
- [ ] **AR-03** 数据权限规则可落地：村级按 `village_code`、镇级按 `town_code` + 状态过滤。
- [ ] **AR-04** 状态迁移与 `apply_flow_log` 写入时机有明确说明。

#### 依赖关系

- 依赖：本文档（`01-mvp-requirements.md`）定稿。
- 阻塞：api-designer、backend-engineer、frontend-engineer。

#### 禁止范围

- 不改技术栈选型；不设计 MVP 范围外模块（见 §3.2）；不写业务代码。

#### 负责文件清单（唯一）

- `docs/02-architecture.md`

---

### 6.2 主责：api-designer

#### 交付物

- `docs/03-api-contract.md`：REST 路径、请求/响应 JSON 示例、错误码表、分页约定、角色可见性矩阵。
- 覆盖最小接口集：登录、个人信息 CRUD、政策列表/详情、申请提交/列表/详情、初审/终审、用户 CRUD、政策 CRUD、日志查询、文件上传。

#### 验收标准

- [ ] **API-01** 每个接口标注所需角色与数据权限规则。
- [ ] **API-02** 状态枚举与本文档 §1.3 一致，无第二套命名。
- [ ] **API-03** 统一响应包装（如 `{ code, message, data }`）与 HTTP 状态码约定完整。
- [ ] **API-04** 明确 `SYS_ADMIN` 不可调用的审批类接口清单。

#### 依赖关系

- 依赖：`docs/02-architecture.md`（architect 产出）。
- 阻塞：backend-engineer、frontend-engineer。

#### 禁止范围

- 不新增 MVP 外接口；不修改数据库表结构文档中的表名；不写实现代码。

#### 负责文件清单（唯一）

- `docs/03-api-contract.md`

---

### 6.3 主责：backend-engineer

#### 交付物

- `server/` 完整 Spring Boot 工程：实体/Mapper/Service/Controller、JWT 鉴权、RBAC 拦截、数据权限 SQL、文件上传、种子数据脚本（演示账号）。
- 配置文件：`application.yml`（库名 `xiangcetong`，敏感项走环境变量，禁止明文密码入库）。

#### 验收标准

- [ ] **BE-01** 通过 §5 中 AC-01、AC-03、AC-04～AC-07、AC-12～AC-22 的后端相关项。
- [ ] **BE-02** 审批操作事务内更新 `subsidy_apply` 并插入 `apply_flow_log`。
- [ ] **BE-03** 关键操作写入 `operation_log`（登录、政策变更、审批）。
- [ ] **BE-04** 提供可重复执行的 seed（如 `server/src/main/resources/db/seed.sql` 或 CommandLineRunner），写入 §4 演示账号与样例数据。

#### 依赖关系

- 依赖：`docs/02-architecture.md`、`docs/03-api-contract.md`。
- 依赖：MySQL `xiangcetong` 库已存在 6 表；Redis 可用。
- 阻塞：frontend-engineer 联调。

#### 禁止范围

- 不删库重建；不对 6 张已有表做破坏性 DDL；不实现 §3.2 所列功能；不提交含真实 DB 密码的文件。

#### 负责文件清单（唯一）

- `server/**`（整个后端工程目录）

---

### 6.4 主责：frontend-engineer

#### 交付物

- `web-h5/`：登录、个人中心、政策列表/详情、申报（拍照上传）、我的记录；Vant 适老化样式。
- `web-admin/`：登录、布局与菜单（按角色渲染）、用户管理、政策管理、申请管理与审批、操作日志；Element Plus。

#### 验收标准

- [ ] **FE-01** 通过 §5 中 AC-02、AC-04～AC-05、AC-08～AC-13、AC-14～AC-19、AC-20～AC-23、AC-25 的前端相关项。
- [ ] **FE-02** H5 在 Chrome 移动模拟器（375px 宽）下可完成完整申报演示。
- [ ] **FE-03** PC 管理端按角色隐藏无权限菜单与按钮（含 SYS_ADMIN 无审批按钮）。
- [ ] **FE-04** 接口对接与 `docs/03-api-contract.md` 一致，axios 统一处理 401/403。

#### 依赖关系

- 依赖：`docs/03-api-contract.md`。
- 依赖：backend `server` 可启动并联调（至少登录、政策、申请主流程可用）。
- 可选并行：静态页面可在 Mock 阶段先行，但验收前必须真实联调。

#### 禁止范围

- 不做小程序/APP；不做 MVP 外页面；不硬编码真实密码；不修改 `server/` 与 `docs/` 下 architect/api 文档。

#### 负责文件清单（唯一）

- `web-h5/**`、`web-admin/**`

---

## 7. 附录：表字段速查（沿用，勿改名）

<details>
<summary>点击展开已有表结构摘要</summary>

**sys_user**：`id, phone, password_hash, name, role, county_code, town_code, village_code, created_at`

**villager_info**：`id, user_id, county_name, town_name, village_name, plant_type, bank_card, updated_at`

**subsidy_policy**：`id, title, content, conditions, standard, consult_phone, status, created_at, updated_at`

**subsidy_apply**：`id, user_id, policy_id, material_urls, status, village_code, town_code, first_by, first_opinion, first_at, final_by, final_opinion, final_at, created_at, updated_at`

**apply_flow_log**：`id, apply_id, from_status, to_status, operator_id, opinion, created_at`

**operation_log**：`id, user_id, action, target, detail, created_at`

</details>

---

*文档结束 — 下游请 strictly 按四要素接单，发现范围膨胀须回溯本文档修订。*
