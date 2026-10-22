# 乡策通 — REST API 接口契约

> **版本**：2.0  
> **日期**：2026-10-09  
> **依赖**：`docs/01-mvp-requirements.md`（角色/状态机/表结构）  
> **Base URL**：`http://localhost:8080`  
> **Content-Type**：`application/json`（文件上传除外）

---

## 1. 通用约定

### 1.1 统一响应结构

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | **0** 表示成功；非 0 表示业务/鉴权错误（见 §1.4） |
| `message` | string | 人类可读提示，前端可直接 Toast |
| `data` | object / array / null | 成功时载荷；失败时通常为 `null` |

### 1.2 HTTP 状态码

| HTTP | 场景 |
|------|------|
| 200 | 请求到达且已处理（含 `code≠0` 的业务错误） |
| 401 | 未携带 Token、Token 无效或已过期（`code=40101`） |
| 403 | 已登录但角色/数据权限不足（`code=40301`） |
| 404 | 路径不存在 |
| 413 | 上传文件超过大小限制 |
| 500 | 未捕获服务端异常（`code=50001`） |

### 1.3 鉴权

除 **§2.1 登录** 与 **§2.2 政策列表/详情（村民可读）** 外，其余接口均需在 Header 携带：

```
Authorization: Bearer <token>
```

Token 由登录接口签发（JWT + Redis 会话，TTL 建议 24h，具体以实现为准）。

### 1.4 全局错误码

| code | 含义 | 典型场景 |
|------|------|----------|
| 0 | 成功 | — |
| 40001 | 参数校验失败 | 缺字段、格式错误、枚举非法 |
| 40101 | 未认证 | 无 Token、Token 过期 |
| 40301 | 无权限 | 角色不匹配、跨村/跨镇、SYS_ADMIN 调审批 |
| 40401 | 资源不存在 | 政策/申请/用户 id 不存在 |
| 40901 | 冲突 | 手机号已注册 |
| 42201 | 业务规则不满足 | 政策已下架仍申报、状态不可审批、重复提交 |
| 50001 | 服务器内部错误 | 未预期异常 |

接口级错误在「错误码」列补充；未列出的场景使用上表全局码。

### 1.5 分页约定

列表类接口 Query 参数：

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `pageNum` | int | 1 | 页码，从 1 开始 |
| `pageSize` | int | 10 | 每页条数，最大 50 |

分页响应 `data` 结构：

```json
{
  "list": [],
  "total": 0,
  "pageNum": 1,
  "pageSize": 10
}
```

### 1.6 时间格式

所有日期时间字段使用 ISO-8601 字符串：`yyyy-MM-dd'T'HH:mm:ss`（服务器时区 UTC+8）。

---

## 2. 枚举定义

### 2.1 角色枚举（`sys_user.role`）

| 值 | 说明 | 端 |
|----|------|-----|
| `VILLAGER` | 村民 | H5 |
| `VILLAGE_ADMIN` | 村级管理员 | PC 管理端 |
| `TOWN_ADMIN` | 镇级管理员 | PC 管理端 |
| `SYS_ADMIN` | 系统管理员 | PC 管理端 |

**端侧约束（接口层强制）**

| 角色 | 可访问前缀 |
|------|------------|
| `VILLAGER` | `/api/villager/**`、`/api/policy/**`、`/api/apply/**`、`/api/file/**` |
| `VILLAGE_ADMIN` | `/api/admin/apply/**`（仅初审相关）、`/api/file/**` |
| `TOWN_ADMIN` | `/api/admin/apply/**`（仅终审相关）、`/api/file/**` |
| `SYS_ADMIN` | `/api/admin/**`（**不可**调用初审/终审接口） |

管理员共用 `POST /api/auth/login`；村民账号调管理端写接口返回 **40301**。

### 2.2 政策状态枚举（`subsidy_policy.status`）

| 值 | 说明 |
|----|------|
| `ONLINE` | 已上架，H5 政策列表可见 |
| `OFFLINE` | 已下架，H5 不可见，不可新申报 |

### 2.3 申请状态枚举（`subsidy_apply.status`）

```
DRAFT  →  PENDING_VILLAGE  →  PENDING_TOWN  →  APPROVED
                 ↓                  ↓
              REJECTED           REJECTED
```

| 值 | 说明 |
|----|------|
| `DRAFT` | 村民草稿（未提交审核；**不进入**管理端审批列表） |
| `PENDING_VILLAGE` | 待村级初审 |
| `PENDING_TOWN` | 村级已通过，待镇级终审 |
| `APPROVED` | 终审通过 |
| `REJECTED` | 初审或终审驳回（终态） |

### 2.4 审批动作枚举（初审/终审 Body）

| 值 | 说明 |
|----|------|
| `PASS` | 通过 |
| `REJECT` | 驳回（须填写 `opinion`） |

---

## 3. 认证

### 3.1 登录

| 项 | 内容 |
|----|------|
| **方法路径** | `POST /api/auth/login` |
| **鉴权** | 无需 Token |
| **角色** | 全部四角色 |

**请求 Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `phone` | string | 是 | 11 位手机号 |
| `password` | string | 是 | 明文密码（传输建议 HTTPS；演示环境 HTTP 本地） |

**成功响应 `data`**

```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": 1,
    "name": "张三",
    "role": "VILLAGER",
    "villageCode": "东村",
    "townCode": "示例镇"
  }
}
```

| 字段 | 说明 |
|------|------|
| `user.villageCode` | 村民/村级管理员所属村编码；镇级/系统管理员可为空字符串 |
| `user.townCode` | 所属镇编码 |

**错误码**

| code | 说明 |
|------|------|
| 40001 | 手机号或密码格式不合法 |
| 40101 | 手机号或密码错误 |
| 40301 | 村民账号尝试登录 PC 管理端专用场景（若前端分端校验，后端仍按角色返回 Token；访问管理写接口时再 403） |

**数据权限**：无。

**副作用**：写入 `operation_log`（action=`LOGIN`）。

---

## 4. 村民 — 个人信息

### 4.1 获取个人信息

| 项 | 内容 |
|----|------|
| **方法路径** | `GET /api/villager/profile` |
| **鉴权** | Bearer Token |
| **角色** | `VILLAGER` |

**请求**：无 Body。

**成功响应 `data`**

```json
{
  "userId": 1,
  "name": "张三",
  "phone": "13800000001",
  "countyName": "示例县",
  "townName": "示例镇",
  "villageName": "东村",
  "plantType": "水稻种植",
  "bankCard": "6222********1234",
  "bankName": "中国农业银行"
}
```

字段映射：`villager_info` + `sys_user.name/phone`。`bankName` 可选，未填写时可为 `null` 或空字符串。

**错误码**：40101、40301。

**数据权限**：仅返回当前登录村民本人记录。

---

### 4.2 更新个人信息

| 项 | 内容 |
|----|------|
| **方法路径** | `PUT /api/villager/profile` |
| **鉴权** | Bearer Token |
| **角色** | `VILLAGER` |

**请求 Body**（全部可选，至少传 1 个字段）

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 姓名（1~32 字，写 `sys_user.name`） |
| `phone` | string | 手机号（11 位，登录账号；须全库唯一，修改后下次登录用新号） |
| `countyName` | string | 县名称 |
| `townName` | string | 乡镇名称 |
| `villageName` | string | 村名称 |
| `plantType` | string | 种养类型 |
| `bankCard` | string | 银行卡号 |
| `bankName` | string | 开户行名称（可选） |

**成功响应 `data`**：同 §4.1 最新快照。

**错误码**：40001（姓名非法 / 手机号格式错误 / 手机号已被占用）、40101、40301。

**数据权限**：仅更新当前登录村民；若 `villageName` 变更，同步更新 `sys_user.village_code`（与村名保持一致，供数据权限过滤）；姓名/手机号变更后同步 Redis 会话快照，避免操作日志记录旧值。

---

### 4.3 我的收藏政策（分页）

| 项 | 内容 |
|----|------|
| **方法路径** | `GET /api/villager/favorites` |
| **鉴权** | Bearer Token |
| **角色** | `VILLAGER` |

**Query 参数**：`pageNum`、`pageSize`（见 §1.5）。

**成功响应 `data`**：分页结构；`list[]` 元素为政策摘要（至少含 `id`、`title`、`standard`、`status`、`favoritedAt`），仅 `ONLINE` 政策；已下架政策可从收藏列表剔除或标 `status=OFFLINE`（以实现约定为准，建议剔除）。

```json
{
  "list": [
    {
      "id": 1,
      "title": "2026年种粮大户补贴",
      "standard": "每亩200元",
      "status": "ONLINE",
      "favoritedAt": "2026-10-08T12:00:00"
    }
  ],
  "total": 1,
  "pageNum": 1,
  "pageSize": 10
}
```

**错误码**：40101、40301。

**数据权限**：仅当前登录村民的收藏记录。

---

## 5. 政策（村民可读）

> **村民主入口**：登录后请调用 **§5.0 政策匹配**；`/api/policy/list` 仅作兜底/未登录预览，不得作为 H5「适合我的政策」主路径。

### 5.0 政策匹配（村民主入口）

| 项 | 内容 |
|----|------|
| **方法路径** | `GET /api/policy/match` |
| **鉴权** | Bearer Token **必填** |
| **角色** | `VILLAGER` |

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `plantType` | string | 否 | 二次筛选种养（枚举或中文，后端 normalize） |
| `category` | string | 否 | 政策类别：`GRAIN` / `MACHINERY` / `LIVESTOCK` / `GENERAL` 等 |
| `townName` | string | 否 | 二次筛选乡镇 |
| `includeNone` | boolean | 否 | 默认 `false`；`true` 时含 `NONE` |
| `pageNum` / `pageSize` | int | 否 | 见 §1.5 |

**成功 `data.list[]` 元素**：含政策摘要 + `score`、`matchLevel`（`FULL`/`PARTIAL`/`NONE`）、`matchLevelLabel`、`scoreDetail`、`reasons[]`。算法见 `docs/04-policy-match-algorithm.md`。

**错误码**：40101、40301、42202（资料不完整，`data.missingFields`）、40001。

---

### 5.1 政策列表

| 项 | 内容 |
|----|------|
| **方法路径** | `GET /api/policy/list` |
| **鉴权** | 可选（未登录也可读；H5 登录后携带 Token） |
| **角色** | 公开；**不作为**村民主入口 |

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `status` | string | 否 | 默认 `ONLINE`；村民端固定传 `ONLINE` |
| `pageNum` | int | 否 | 见 §1.5 |
| `pageSize` | int | 否 | 见 §1.5 |

**成功响应 `data.list[]` 元素**

```json
{
  "id": 1,
  "title": "2026年种粮大户补贴",
  "standard": "每亩200元",
  "consultPhone": "0571-12345678",
  "status": "ONLINE",
  "createdAt": "2026-10-01T10:00:00"
}
```

**错误码**：40001（非法 status）。

**数据权限**：无组织隔离；仅返回 `status` 匹配的政策。

---

### 5.2 政策详情

| 项 | 内容 |
|----|------|
| **方法路径** | `GET /api/policy/{id}` |
| **鉴权** | 可选 |
| **角色** | 公开；`VILLAGER` 主用 |

**路径参数**：`id` — 政策 ID。

**成功响应 `data`**

```json
{
  "id": 1,
  "title": "2026年种粮大户补贴",
  "content": "政策正文...",
  "conditions": "申领条件（人类可读，兼容旧字段）...",
  "standard": "每亩200元",
  "consultPhone": "0571-12345678",
  "status": "ONLINE",
  "category": "GRAIN",
  "minArea": 50,
  "requireBankCard": true,
  "validFrom": "2023-01-01",
  "validTo": "2026-12-31",
  "sourceOrg": "县农业农村局",
  "subsidyAmountText": "每亩最高200元，按实际核实面积兑付",
  "deadlineText": "2026年12月31日前完成申报",
  "consultInfo": "咨询电话：0571-12345678；工作日 9:00–17:00",
  "applyConditions": [
    "本村户籍或土地流转在本村",
    "种植面积不少于50亩",
    "已绑定本人一类银行卡"
  ],
  "materialSchema": {
    "code": "GRAIN_LARGE_HOLDER",
    "title": "种粮大户补贴申报材料",
    "docName": "种粮大户补贴申报材料清单",
    "desc": "请按下列材料逐项拍照上传，可先保存草稿",
    "fields": [
      {
        "key": "idCardFront",
        "label": "身份证正面",
        "type": "photo",
        "prefill": null
      },
      {
        "key": "landContract",
        "label": "土地承包或流转合同",
        "type": "photo",
        "prefill": null
      },
      {
        "key": "bankCardPhoto",
        "label": "银行卡照片",
        "type": "photo",
        "prefill": "bankCard"
      }
    ]
  },
  "createdAt": "2026-10-01T10:00:00",
  "updatedAt": "2026-10-05T15:30:00",
  "favorited": true,
  "myMatch": {
    "score": 95,
    "matchLevel": "PARTIAL",
    "matchLevelLabel": "基本匹配",
    "scoreDetail": {},
    "reasons": ["种植面积差10亩，补足后可提升为完全匹配"]
  }
}
```

**扩展展示字段（详情必返，可为 `null`）**

| 字段 | 类型 | 说明 |
|------|------|------|
| `sourceOrg` | string \| null | 来源单位 |
| `subsidyAmountText` | string \| null | 补贴额度说明文案 |
| `deadlineText` | string \| null | 申报截止说明文案 |
| `consultInfo` | string \| null | 咨询方式综合文案（可含电话/时段） |
| `applyConditions` | string[] \| null | 申领条件条目列表；与旧字段 `conditions` 并存 |
| `materialSchema` | object \| null | 申报材料表单 Schema，驱动 H5 分步填报 |

**`materialSchema` 结构**

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | string | Schema 编码 |
| `title` | string | 表单标题 |
| `docName` | string | 材料清单名称 |
| `desc` | string | 填写说明 |
| `fields` | array | 材料字段列表 |

**`materialSchema.fields[]` 元素**

| 字段 | 类型 | 说明 |
|------|------|------|
| `key` | string | 字段键，写入草稿 `materialData` 的 key |
| `label` | string | 展示标签 |
| `type` | string | 当前支持 `photo`（拍照/相册上传 URL）；后续可扩展 |
| `prefill` | string \| null | 预填来源：如 `bankCard` 表示可从村民资料银行卡相关字段提示；无则 `null` |

**村民附加字段（仅已登录且角色为 `VILLAGER` 时有意义）**

| 字段 | 类型 | 说明 |
|------|------|------|
| `favorited` | boolean | 当前用户是否已收藏该政策；未登录或非村民时为 `false` 或不返回（建议统一返 `false`） |
| `myMatch` | object \| null | 匹配结果；资料完整时计算；未登录、非村民或资料不完整时为 `null` |

**错误码**：40401（不存在）；40301（村民访问 `OFFLINE` 政策详情时可选拦截，建议返回 40401 避免泄露）。

**数据权限**：无；村民仅应访问 `ONLINE` 政策。

---

### 5.3 收藏政策

| 项 | 内容 |
|----|------|
| **方法路径** | `POST /api/policy/{id}/favorite` |
| **鉴权** | Bearer Token |
| **角色** | `VILLAGER` |

**路径参数**：`id` — 政策 ID（须为 `ONLINE`）。

**请求**：无 Body。

**成功响应 `data`**

```json
{
  "policyId": 1,
  "favorited": true
}
```

**错误码**

| code | 说明 |
|------|------|
| 40401 | 政策不存在或非上架 |
| 40101 / 40301 | 未认证 / 非村民 |
| 42201 | 可选：已收藏时幂等返回成功，或返回冲突（推荐**幂等成功**） |

**数据权限**：收藏绑定当前 `user_id`。

---

### 5.4 取消收藏

| 项 | 内容 |
|----|------|
| **方法路径** | `DELETE /api/policy/{id}/favorite` |
| **鉴权** | Bearer Token |
| **角色** | `VILLAGER` |

**路径参数**：`id` — 政策 ID。

**成功响应 `data`**

```json
{
  "policyId": 1,
  "favorited": false
}
```

**错误码**：40101、40301；未收藏时推荐幂等成功（`favorited=false`）。

**数据权限**：仅删除当前用户对该政策的收藏。

---

## 6. 补贴申请（村民）

### 6.1 直接提交申请（兼容）

| 项 | 内容 |
|----|------|
| **方法路径** | `POST /api/apply` |
| **鉴权** | Bearer Token |
| **角色** | `VILLAGER` |

> 兼容旧路径：材料已齐时可直接提交为 `PENDING_VILLAGE`。推荐新流程：先 §6.2 存草稿，再 §6.4 提交草稿。

**请求 Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `policyId` | long | 是 | 政策 ID |
| `materialUrls` | string[] | 是 | 材料 URL 数组，≥1；由 §11.1 上传接口获得 |
| `materialData` | object | 否 | 按 `materialSchema.fields` 的 key→值；有则同步落库 |

**成功响应 `data`**

```json
{
  "id": 100,
  "policyId": 1,
  "policyTitle": "2026年种粮大户补贴",
  "status": "PENDING_VILLAGE",
  "materialUrls": ["/uploads/2026/10/abc.jpg"],
  "materialData": {
    "idCardFront": "/uploads/2026/10/id1.jpg",
    "landContract": "/uploads/2026/10/land.jpg"
  },
  "createdAt": "2026-10-08T14:00:00"
}
```

**错误码**

| code | 说明 |
|------|------|
| 40001 | `materialUrls` 为空 |
| 40401 | 政策不存在 |
| 42201 | 政策非 `ONLINE`；村民个人信息不完整（缺村/银行卡等，规则以实现为准）；同用户同政策已有非终态申请冲突（以实现为准） |

**数据权限**：申请写入当前用户 `user_id`；冗余写入 `village_code`、`town_code`（来自 `sys_user`），供管理端过滤。

**副作用**：插入 `subsidy_apply`；插入 `apply_flow_log`（`from_status=null`, `to_status=PENDING_VILLAGE`）。

---

### 6.2 保存 / 更新申报草稿

| 项 | 内容 |
|----|------|
| **方法路径** | `POST /api/apply/draft` |
| **鉴权** | Bearer Token |
| **角色** | `VILLAGER` |

**请求 Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `policyId` | long | 是 | 政策 ID（须 `ONLINE`） |
| `materialData` | object | 是 | key→值；`type=photo` 的值为上传后的 URL 字符串；未填字段可省略或 `null` |

**约束**：同一用户、同一政策**仅允许一份** `status=DRAFT` 草稿；再次调用则**覆盖更新**该草稿（upsert）。若同政策已有 `PENDING_VILLAGE` / `PENDING_TOWN` / `APPROVED`，禁止再开草稿（`42201`）。**`REJECTED` 可再开草稿**（重新申报）；直提 `POST /api/apply` 遵守同一规则。

**成功响应 `data`**

```json
{
  "id": 100,
  "policyId": 1,
  "policyTitle": "2026年种粮大户补贴",
  "status": "DRAFT",
  "materialData": {
    "idCardFront": "/uploads/2026/10/id1.jpg",
    "landContract": null
  },
  "materialUrls": [],
  "createdAt": "2026-10-08T13:00:00",
  "updatedAt": "2026-10-08T13:30:00"
}
```

草稿阶段 `materialUrls` 可为空数组；正式提交时由服务端从 `materialData` 中所有 `type=photo` 且非空的值汇总写入。

**错误码**

| code | 说明 |
|------|------|
| 40001 | 缺 `policyId` / `materialData` 非对象 |
| 40401 | 政策不存在 |
| 42201 | 政策非 `ONLINE`；同政策已存在 `PENDING_*`/`APPROVED` 进行中申请不可再开草稿（以实现为准） |

**数据权限**：草稿 `user_id` = 当前用户；**不**进入村/镇审批列表。

**副作用**：upsert `subsidy_apply`（`status=DRAFT`）；首次创建可不写 `apply_flow_log`，或写 `to_status=DRAFT`（以实现为准）。

---

### 6.3 查询某政策的草稿

| 项 | 内容 |
|----|------|
| **方法路径** | `GET /api/apply/draft` |
| **鉴权** | Bearer Token |
| **角色** | `VILLAGER` |

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `policyId` | long | 是 | 政策 ID |

**成功响应 `data`**

- 存在草稿：同 §6.2 成功体。
- 无草稿：`data` 为 `null`（`code=0`）。

**错误码**：40001（缺 `policyId`）、40101、40301。

**数据权限**：仅查当前用户 + 指定政策的 `DRAFT`。

---

### 6.4 提交草稿（DRAFT → PENDING_VILLAGE）

| 项 | 内容 |
|----|------|
| **方法路径** | `POST /api/apply/{id}/submit-draft` |
| **鉴权** | Bearer Token |
| **角色** | `VILLAGER` |

**路径参数**：`id` — 申请（草稿）ID。

**请求**：无 Body（以库中最新 `materialData` 为准）；可选 Body 再传一次 `materialData` 做最终覆盖（以实现为准，建议支持）。

**业务规则**

1. 当前状态必须为 `DRAFT`，且 `user_id` 为当前用户。
2. 关联政策须仍为 `ONLINE`。
3. 按该政策 `materialSchema.fields` **校验材料齐全**：每个必填 `photo` 字段在 `materialData` 中均有非空 URL。
4. 将所有 `type=photo` 且非空的 URL **按 fields 顺序汇总** 写入 `material_urls`（JSON 数组）。
5. 状态迁移：`DRAFT` → `PENDING_VILLAGE`。

**成功响应 `data`**

```json
{
  "id": 100,
  "policyId": 1,
  "policyTitle": "2026年种粮大户补贴",
  "status": "PENDING_VILLAGE",
  "materialData": {
    "idCardFront": "/uploads/2026/10/id1.jpg",
    "landContract": "/uploads/2026/10/land.jpg",
    "bankCardPhoto": "/uploads/2026/10/bank.jpg"
  },
  "materialUrls": [
    "/uploads/2026/10/id1.jpg",
    "/uploads/2026/10/land.jpg",
    "/uploads/2026/10/bank.jpg"
  ],
  "createdAt": "2026-10-08T13:00:00",
  "updatedAt": "2026-10-08T14:00:00"
}
```

**错误码**

| code | 说明 |
|------|------|
| 40401 | 申请不存在或不属于当前用户 |
| 42201 | 非 `DRAFT`；政策已下架；`materialSchema` 要求的材料未齐（`message` 可指出缺失 `key`/`label`） |

**数据权限**：仅本人草稿。

**副作用**：更新 `status`、`material_urls`；写 `apply_flow_log`（`from_status=DRAFT`, `to_status=PENDING_VILLAGE`）。

---

### 6.5 我的申请列表

| 项 | 内容 |
|----|------|
| **方法路径** | `GET /api/apply/mine` |
| **鉴权** | Bearer Token |
| **角色** | `VILLAGER` |

**Query 参数**：`pageNum`、`pageSize`；可选 `status` 过滤（**含 `DRAFT`**；不传则返回全部状态含草稿）。

**成功响应 `data.list[]` 元素**

```json
{
  "id": 100,
  "policyId": 1,
  "policyTitle": "2026年种粮大户补贴",
  "status": "DRAFT",
  "statusLabel": "草稿",
  "materialUrls": [],
  "materialData": {
    "idCardFront": "/uploads/2026/10/id1.jpg",
    "landContract": null
  },
  "firstOpinion": null,
  "finalOpinion": null,
  "createdAt": "2026-10-08T13:00:00",
  "updatedAt": "2026-10-08T13:30:00"
}
```

| 字段 | 说明 |
|------|------|
| `materialData` | object \| null；草稿与已提交申请均应返回（无则为 `null` 或 `{}`） |
| `materialUrls` | 已提交后由 photo 字段汇总；草稿阶段可为空数组 |
| `statusLabel` | 含「草稿」「待村级初审」等中文 |

**错误码**：40101、40301。

**数据权限**：`WHERE user_id = 当前用户`；不返回他人申请。

---

## 7. 管理端 — 申请查询与审批

### 7.1 申请列表（管理端）

| 项 | 内容 |
|----|------|
| **方法路径** | `GET /api/admin/apply` |
| **鉴权** | Bearer Token |
| **角色** | `VILLAGE_ADMIN`、`TOWN_ADMIN`、`SYS_ADMIN`（只读） |

**Query 参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| `status` | string | 可选；村级默认 `PENDING_VILLAGE`；镇级默认 `PENDING_TOWN` |
| `villageCode` | string | 可选；`SYS_ADMIN` 筛选用 |
| `pageNum` / `pageSize` | int | 分页 |

**成功响应 `data.list[]` 元素**

```json
{
  "id": 100,
  "applicantName": "张三",
  "applicantPhone": "13800000001",
  "policyTitle": "2026年种粮大户补贴",
  "status": "PENDING_VILLAGE",
  "villageCode": "东村",
  "townCode": "示例镇",
  "materialUrls": ["/uploads/2026/10/abc.jpg"],
  "firstOpinion": null,
  "firstAt": null,
  "finalOpinion": null,
  "finalAt": null,
  "createdAt": "2026-10-08T14:00:00"
}
```

**错误码**：40101、40301。

**数据权限**

| 角色 | 过滤规则 |
|------|----------|
| `VILLAGE_ADMIN` | `subsidy_apply.village_code = 当前用户.village_code`；**排除** `DRAFT` |
| `TOWN_ADMIN` | `subsidy_apply.town_code = 当前用户.town_code` **且** 强制/默认 `status=PENDING_TOWN`；**排除** `DRAFT` |
| `SYS_ADMIN` | 全镇只读；默认排除 `DRAFT`（审计若需可显式 `status=DRAFT`，MVP 可不支持） |

---

### 7.2 村级初审

| 项 | 内容 |
|----|------|
| **方法路径** | `POST /api/admin/apply/{id}/village-audit` |
| **鉴权** | Bearer Token |
| **角色** | **`VILLAGE_ADMIN` only** |

**路径参数**：`id` — 申请 ID。

**请求 Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | `PASS` \| `REJECT` |
| `opinion` | string | 否 | 意见；`REJECT` 时必填 |

**成功响应 `data`**

```json
{
  "id": 100,
  "status": "PENDING_TOWN",
  "firstOpinion": "材料齐全，同意上报",
  "firstAt": "2026-10-08T15:00:00"
}
```

驳回时 `status=REJECTED`。

**错误码**

| code | 说明 |
|------|------|
| 40301 | 非村级管理员；跨村；**SYS_ADMIN/TOWN_ADMIN 调用** |
| 40401 | 申请不存在 |
| 42201 | 当前状态非 `PENDING_VILLAGE` |

**数据权限**：申请 `village_code` 必须等于操作者 `village_code`。

**状态迁移**

| action | 新 status |
|--------|-----------|
| `PASS` | `PENDING_TOWN` |
| `REJECT` | `REJECTED` |

**副作用**：更新 `first_by/first_opinion/first_at`；写 `apply_flow_log`；写 `operation_log`。

---

### 7.3 镇级终审

| 项 | 内容 |
|----|------|
| **方法路径** | `POST /api/admin/apply/{id}/town-audit` |
| **鉴权** | Bearer Token |
| **角色** | **`TOWN_ADMIN` only** |

**路径参数**：`id` — 申请 ID。

**请求 Body**：同 §7.2（`action`、`opinion`）。

**成功响应 `data`**

```json
{
  "id": 100,
  "status": "APPROVED",
  "finalOpinion": "同意发放",
  "finalAt": "2026-10-08T16:00:00"
}
```

**错误码**

| code | 说明 |
|------|------|
| 40301 | 非镇级管理员；跨镇；**SYS_ADMIN/VILLAGE_ADMIN 调用** |
| 40401 | 申请不存在 |
| 42201 | 当前状态非 `PENDING_TOWN` |

**数据权限**：申请 `town_code` 必须等于操作者 `town_code`。

**状态迁移**

| action | 新 status |
|--------|-----------|
| `PASS` | `APPROVED` |
| `REJECT` | `REJECTED` |

**副作用**：更新 `final_by/final_opinion/final_at`；写 `apply_flow_log`；写 `operation_log`。

---

## 8. 管理端 — 政策 CRUD 与上下架

> 前缀 `/api/admin/policy`；角色均为 **`SYS_ADMIN`**。

### 8.1 政策列表（管理端）

| 项 | 内容 |
|----|------|
| **方法路径** | `GET /api/admin/policy/list` |
| **角色** | `SYS_ADMIN` |

**Query**：`status`（可选）、`pageNum`、`pageSize`。

**成功响应**：分页结构；`list[]` 含完整字段（含 `OFFLINE`）。

**数据权限**：全镇政策，无村/镇隔离。

---

### 8.2 新增政策

| 项 | 内容 |
|----|------|
| **方法路径** | `POST /api/admin/policy` |
| **角色** | `SYS_ADMIN` |

**请求 Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | string | 是 | |
| `content` | string | 是 | |
| `conditions` | string | 是 | 人类可读申领条件 |
| `standard` | string | 是 | |
| `consultPhone` | string | 是 | |
| `status` | string | 否 | 默认 `OFFLINE` |
| `category` | string | 否 | 如 `GRAIN`/`MACHINERY`/`LIVESTOCK`/`GENERAL` |
| `targetPlantTypes` | string[] | 否 | 适用种养枚举数组；空=不限 |
| `minArea` | number | 否 | 最小面积（亩）；空=不限 |
| `requireBankCard` | boolean/int | 否 | 是否要求绑卡 |
| `regionCodes` | object | 否 | `{"counties":[],"towns":[],"villages":[]}` |
| `validFrom` / `validTo` | date | 否 | `yyyy-MM-dd`；空=不限 |
| `targetSubjectTypes` | string[] | 否 | 适用主体：`FARMER`/`FAMILY_FARM`/`COOP`/`ENTERPRISE`；空=不限 |
| `requireVeteran` | boolean | 否 | 是否要求退役军人家庭；`false`/缺省=不限 |
| `requireMonitor` | boolean | 否 | 是否要求脱贫监测户 |
| `minLivestock` | int | 否 | 最低存栏；空=不限 |
| `requireManureFacility` | boolean | 否 | 是否要求粪污处理设施 |
| `requireMachineryIntent` | boolean | 否 | 是否要求购机/报废意向 |
| `requireGrainInsurance` | boolean | 否 | 是否要求已参保粮食保险 |
| `requireLandCert` | boolean | 否 | 是否要求土地承包证 |
| `materialSchema` | object \| null | 否 | 申报材料 Schema（见 §5.2）；`null`/空=无分步材料（走旧直提） |

> 管理端编辑时建议**一次提交全部匹配字段**（含开关关闭为 `false`、Schema 原样回传），避免只改基础字段时把库中的 v2 条件误清空。

**成功响应 `data`**：含 `id` 与上述结构化字段（含 v2 与 `materialSchema`）的完整政策对象。

**错误码**：40001、40301。

**副作用**：`operation_log`（action=`POLICY_CREATE`）。

---

### 8.3 政策详情（管理端）

| 项 | 内容 |
|----|------|
| **方法路径** | `GET /api/admin/policy/{id}` |
| **角色** | `SYS_ADMIN` |

**成功响应 `data`**：同 §8.2 全字段（含结构化匹配字段；无 `myMatch`）。

**错误码**：40401。

---

### 8.4 编辑政策

| 项 | 内容 |
|----|------|
| **方法路径** | `PUT /api/admin/policy/{id}` |
| **角色** | `SYS_ADMIN` |

**请求 Body**：同 §8.2，字段均可选（至少 1 个）。结构化字段写入后立即影响村民 `/api/policy/match`。

**成功响应 `data`**：更新后完整对象。

**错误码**：40001、40401、40301。

**副作用**：`operation_log`（action=`POLICY_UPDATE`）。

---

### 8.5 政策上下架

| 项 | 内容 |
|----|------|
| **方法路径** | `PUT /api/admin/policy/{id}/status` |
| **角色** | `SYS_ADMIN` |

**请求 Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `status` | string | 是 | `ONLINE` \| `OFFLINE` |

**成功响应 `data`**

```json
{
  "id": 1,
  "status": "OFFLINE",
  "updatedAt": "2026-10-08T17:00:00"
}
```

**错误码**：40001、40401、40301。

**副作用**：下架后 H5 `GET /api/policy/list?status=ONLINE` 不可见；`operation_log`（action=`POLICY_STATUS`）。

---

## 9. 管理端 — 用户管理

> 角色均为 **`SYS_ADMIN`**。

### 9.1 用户列表

| 项 | 内容 |
|----|------|
| **方法路径** | `GET /api/admin/users` |
| **角色** | `SYS_ADMIN` |

**Query**：`role`（可选）、`phone`（模糊）、`pageNum`、`pageSize`。

**成功响应 `data.list[]` 元素**

```json
{
  "id": 2,
  "phone": "13800000011",
  "name": "王五",
  "role": "VILLAGE_ADMIN",
  "countyCode": "示例县",
  "townCode": "示例镇",
  "villageCode": "东村",
  "createdAt": "2026-10-01T09:00:00"
}
```

**数据权限**：全镇用户列表，不含 `password_hash`。

---

### 9.2 创建用户

| 项 | 内容 |
|----|------|
| **方法路径** | `POST /api/admin/users` |
| **角色** | `SYS_ADMIN` |

**请求 Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `phone` | string | 是 | 唯一 |
| `password` | string | 是 | 明文，后端 BCrypt 存储 |
| `name` | string | 是 | 姓名 |
| `role` | string | 是 | 四角色枚举之一 |
| `countyCode` | string | 否 | 县编码/名称 |
| `townCode` | string | 否 | 镇；`VILLAGE_ADMIN`/`TOWN_ADMIN` 必填 |
| `villageCode` | string | 否 | 村；`VILLAGER`/`VILLAGE_ADMIN` 必填 |

**成功响应 `data`**：同 §9.1 单条（无密码）。

**错误码**：40001、40901（手机号重复）、40301。

**副作用**：`operation_log`（action=`USER_CREATE`）。

---

## 10. 管理端 — 操作日志

### 10.1 操作日志列表

| 项 | 内容 |
|----|------|
| **方法路径** | `GET /api/admin/logs` |
| **鉴权** | Bearer Token |
| **角色** | `SYS_ADMIN` |

**Query**：`action`（可选）、`userId`（可选）、`pageNum`、`pageSize`。

**成功响应 `data.list[]` 元素**

```json
{
  "id": 1,
  "userId": 99,
  "userName": "系统管理员",
  "action": "LOGIN",
  "target": "sys_user:99",
  "detail": "PC端登录",
  "createdAt": "2026-10-08T08:00:00"
}
```

**数据权限**：全镇 `operation_log`，按 `created_at` 倒序。

**错误码**：40101、40301。

---

## 11. 文件上传

### 11.1 上传材料图片

| 项 | 内容 |
|----|------|
| **方法路径** | `POST /api/file/upload` |
| **Content-Type** | `multipart/form-data` |
| **鉴权** | Bearer Token |
| **角色** | `VILLAGER`（申报）；管理端角色可按需放开只读引用 |

**请求 Form 字段**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | file | 是 | JPG/PNG；单张 ≤ 5MB |

**成功响应 `data`**

```json
{
  "url": "/uploads/2026/10/abc.jpg",
  "originalName": "材料.jpg",
  "size": 102400
}
```

**错误码**

| code | 说明 |
|------|------|
| 40001 | 未传文件、格式非 JPG/PNG |
| 413 | 超过 5MB（HTTP 413 或 code=40001） |

**数据权限**：文件落盘 `server/uploads/`；URL 写入 `subsidy_apply.material_urls`（JSON 数组字符串）。

---

## 12. 角色 × 接口矩阵（摘要）

| 接口 | VILLAGER | VILLAGE_ADMIN | TOWN_ADMIN | SYS_ADMIN |
|------|:--------:|:-------------:|:----------:|:---------:|
| POST /api/auth/login | ✓ | ✓ | ✓ | ✓ |
| GET/PUT /api/villager/profile | ✓ | ✗ | ✗ | ✗ |
| GET /api/villager/favorites | ✓ | ✗ | ✗ | ✗ |
| GET /api/policy/match | ✓ | ✗ | ✗ | ✗ |
| GET /api/policy/list, /{id} | ✓（详情可读） | ✗ | ✗ | ✗ |
| POST/DELETE /api/policy/{id}/favorite | ✓ | ✗ | ✗ | ✗ |
| POST /api/apply | ✓ | ✗ | ✗ | ✗ |
| POST/GET /api/apply/draft | ✓ | ✗ | ✗ | ✗ |
| POST /api/apply/{id}/submit-draft | ✓ | ✗ | ✗ | ✗ |
| GET /api/apply/mine（含 DRAFT） | ✓ | ✗ | ✗ | ✗ |
| GET /api/admin/apply | ✗ | ✓ 本村（无草稿） | ✓ 本镇待终审 | ✓ 只读 |
| POST .../village-audit | ✗ | ✓ | ✗ | **✗** |
| POST .../town-audit | ✗ | ✗ | ✓ | **✗** |
| /api/admin/policy/** | ✗ | ✗ | ✗ | ✓ |
| GET/POST /api/admin/users | ✗ | ✗ | ✗ | ✓ |
| GET /api/admin/logs | ✗ | ✗ | ✗ | ✓ |
| POST /api/file/upload | ✓ | ✓ | ✓ | ✓ |

---

## 13. 接口清单（计数）

| # | 方法 | 路径 |
|---|------|------|
| 1 | POST | /api/auth/login |
| 2 | GET | /api/villager/profile |
| 3 | PUT | /api/villager/profile |
| 4 | GET | /api/villager/favorites |
| 5 | GET | /api/policy/match |
| 6 | GET | /api/policy/list |
| 7 | GET | /api/policy/{id} |
| 8 | POST | /api/policy/{id}/favorite |
| 9 | DELETE | /api/policy/{id}/favorite |
| 10 | POST | /api/apply |
| 11 | POST | /api/apply/draft |
| 12 | GET | /api/apply/draft |
| 13 | POST | /api/apply/{id}/submit-draft |
| 14 | GET | /api/apply/mine |
| 15 | GET | /api/admin/apply |
| 16 | POST | /api/admin/apply/{id}/village-audit |
| 17 | POST | /api/admin/apply/{id}/town-audit |
| 18 | GET | /api/admin/policy/list |
| 19 | POST | /api/admin/policy |
| 20 | GET | /api/admin/policy/{id} |
| 21 | PUT | /api/admin/policy/{id} |
| 22 | PUT | /api/admin/policy/{id}/status |
| 23 | GET | /api/admin/users |
| 24 | POST | /api/admin/users |
| 25 | GET | /api/admin/logs |
| 26 | POST | /api/file/upload |

**合计：26 个 REST 接口**（在原 MVP 闭环上增补：政策详情扩展字段、收藏、申报草稿与按 Schema 提交；清单补录既有 GET /api/policy/match）。

> **兼容说明（非破坏）**：状态枚举新增 DRAFT；详情与 GET /api/apply/mine 增字段为向后兼容扩展；新接口不影响既有 POST /api/apply 直提路径。

---

*文档结束 — 实现须与 docs/01-mvp-requirements.md §1.3 状态机及四角色约束严格一致；SYS_ADMIN 审批类接口必须在 Controller 层返回 40301；草稿与收藏仅村民可见。*
