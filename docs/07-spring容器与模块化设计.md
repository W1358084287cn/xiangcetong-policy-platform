# 乡策通｜Spring 容器与模块化设计（高内聚 · 低耦合）

> **版本**：2.0
> **日期**：2026-10-10
> **状态**：已定稿
> **上游依赖**：[`02-architecture.md`](./02-architecture.md)
> **范围**：后端 `server/`（Java + Spring Boot + Maven + MyBatis + Redis）与前端 `web-h5/`、`web-admin/`（Vue 3）的模块化设计说明

---

## 1. 设计目标

以 **Spring 容器（IoC / DI）** 为核心机制，把系统拆成**职责单一、内聚、相互解耦**的模块，实现：

- **高内聚**：一个模块只做一件事，相关的类聚在一起，模块内部完整自洽。
- **低耦合**：模块之间通过**接口 + 依赖注入**协作，不互相 `new`、不穿透到对方的内部实现。
- **可测试**：依赖可替换（Mock 接口），单测不依赖 Spring 启动、不连库。
- **可维护**：新增功能只动对应模块，文件落位有固定规则。

技术栈严格限定：**Spring Boot 3.x + Maven + MyBatis + Redis**（后端），**Vue 3 + Vite**（前端），前后端分离。

---

## 2. 控制反转与依赖注入（IoC / DI）

### 2.1 核心思想

传统写法由对象自己创建依赖（`new XxxService()`），对象与具体实现强绑定，难以替换与测试。Spring 容器把「对象创建与装配」这件事**反转**出来：

```text
对象自己 new 依赖（耦合）          Spring 容器装配（解耦）
─────────────────────────         ─────────────────────────
A 内部 new B()                    容器创建 B 的实例（Bean）
A 依赖具体 B 类                    A 只声明依赖 B 的「接口」
无法替换 / 无法 Mock               容器把 B 注入 A，可替换实现
```

### 2.2 本项目落地方式

| 机制 | 落地 |
|------|------|
| Bean 托管 | `@Service` / `@Component` / `@Configuration` + `@Bean` 交由容器管理 |
| 依赖声明 | 类字段 `private final XxxService xxxService;`（**不可变 + 必须注入**） |
| 依赖注入 | **构造器注入**（唯一方式），Spring 自动装配 |
| 面向接口 | 依赖抽象接口（如 `PasswordEncoder`）而非具体类（`BCryptPasswordEncoder`） |
| 集中装配 | 第三方/基础设施对象用 `@Bean` 在 `config/` 统一创建，业务类不 `new` |

---

## 3. 后端模块划分（按领域分包）

```
com.xiangcetong
├── XiangcetongApplication.java   # 启动入口（仅负责引导，不含业务）
├── common/                        # 横切通用层（全模块共享，不依赖任何业务模块）
│   ├── ApiResponse.java           # 统一返回包装 {code,message,data}
│   ├── BizException.java          # 业务异常
│   ├── ErrorCodes.java            # 错误码常量
│   ├── GlobalExceptionHandler.java# 全局异常 → 统一响应
│   ├── PageResult.java            # 分页结果
│   └── UserContext.java           # 线程级登录上下文（ThreadLocal）
├── config/                        # 基础设施 Bean 装配（第三方对象唯一创建点）
│   ├── AppProperties.java         # @ConfigurationProperties 应用配置
│   ├── RedisConfig.java           # StringRedisTemplate / ObjectMapper Bean
│   ├── SecurityConfig.java        # PasswordEncoder Bean（密码加密器）
│   └── WebMvcConfig.java          # 拦截器 / 静态资源 / CORS
├── auth/                          # 认证鉴权
│   ├── controller/  AuthController
│   ├── service/     AuthService
│   ├── dto/         LoginRequest / ChangePasswordRequest
│   ├── JwtUtil.java               # JWT 签发校验
│   └── AuthInterceptor.java       # 请求拦截 + 会话校验
├── user/                          # 用户与村民档案
├── policy/                        # 政策管理 + 政策匹配
│   ├── support/                   # 匹配算法辅助（归一化等）
├── apply/                         # 申报与两级审批 + 发放
├── notice/                        # 通知公告
├── file/                          # 文件上传存储
└── log/                           # 操作日志 / 审批流水
```

**模块职责边界（高内聚）**：`auth` 只管登录鉴权，`apply` 只管申报审批发放，`policy` 只管政策与匹配，`notice` 只管公告，`log` 只管日志……互不越界。

**每个业务模块内部固定分层（`controller` / `service` / `mapper` / `dto` / `entity`）**：

| 层 | 职责 | 注解 | 依赖方向 |
|----|------|------|---------|
| `controller` | HTTP 接入、参数接收、返回包装 | `@RestController` | → service |
| `service` | 业务逻辑、事务、权限校验 | `@Service` | → mapper + 其他 service |
| `mapper` | 数据访问（MyBatis 接口） | `@Mapper` | → 数据库 |
| `dto` | 入参对象（请求体） | 无 | 被 controller/service 引用 |
| `entity` | 表实体 | 无 | 被 mapper/service 引用 |

> **依赖规则**：只允许上层依赖下层（controller → service → mapper），**禁止反向依赖**；`common`/`config` 被所有模块依赖，但它们**不依赖任何业务模块**。

---

## 4. 模块间协作：通过接口注入，禁止 `new`

### 4.1 正确示例（现状已是如此）

`ApplyService` 需要政策匹配与日志能力，只**声明依赖**，由容器注入：

```java
@Service
public class ApplyService {
    private final ApplyFlowLogService flowLogService;   // 审批流水
    private final OperationLogService operationLogService; // 操作日志
    private final PolicyMatchService policyMatchService; // 政策匹配（跨模块）

    // 构造器注入：Spring 自动装配，字段 final 不可变
    public ApplyService(ApplyFlowLogService flowLogService,
                        OperationLogService operationLogService,
                        PolicyMatchService policyMatchService) {
        this.flowLogService = flowLogService;
        this.operationLogService = operationLogService;
        this.policyMatchService = policyMatchService;
    }
}
```

### 4.2 面向接口解耦（本次重构示范）

密码加密器原为 `new BCryptPasswordEncoder()`，两处 Service 各自创建、绑定具体实现。现改为：

```java
// config/SecurityConfig.java —— 基础设施对象交给容器统一创建
@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

// 业务类只依赖抽象接口，由容器注入
private final PasswordEncoder passwordEncoder;
```

好处：① 实现只出现一次（`config/`）；② 业务类依赖 `PasswordEncoder` 接口，未来换加密算法零改动；③ 测试可直接注入替身。

---

## 5. 高内聚低耦合的几条硬约束

1. **构造器注入 + `private final`**，不用 `@Autowired` 字段注入、不用 setter 注入。
2. **禁止业务类内 `new` 依赖对象**；第三方/基础设施对象一律在 `config/` 用 `@Bean` 装配。
3. **跨模块协作走对方模块的 `service` 接口**，不直接 `new`、不绕过分层直连对方的 mapper（数据查询除外，mapper 为 MyBatis 接口、由容器托管）。
4. **`common` 是唯一允许被所有模块共享的层**，且必须零业务依赖。
5. **Mapper 全部 `#{}` 预编译**，禁止 `${}` 字符串拼接（防 SQL 注入，门禁强制）。
6. **改动类与工具函数要有白话头注释**（门禁强制）。

---

## 6. 前端 Vue 3 模块化（前后端分离）

| 工程 | 角色 | 端口 | 说明 |
|------|------|------|------|
| `web-h5/` | 村民端 | 5173 | Vue 3 + Vite + Vant 4 |
| `web-admin/` | 村管/镇管/系统 | 5174/5175/5176 | Vue 3 + Vite + Element Plus，一套代码三角色产物 |

前端关键解耦约定：

- **统一 API 层**：`src/api/*.js` 封装 axios 请求，页面不直接写请求。
- **角色/菜单/默认页集中配置**：`src/config/role.js`，三端由 `VITE_APP_ROLE` 注入，`dev:town`/`dev:sys` 脚本切换。
- **统一工具层**：`src/utils/*`（状态映射、鉴权、无障碍偏好等）纯函数、可单测（vitest）。
- **前后端分离**：前端 `baseURL=/api` 经 Vite 代理到 `http://localhost:8080`，后端返回统一 `{code,message,data}`。

---

## 7. 本次重构记录（2026-10-10）

| 项 | 改动前 | 改动后 |
|----|--------|--------|
| 密码加密器 | `AuthService` / `AdminUserService` 各自 `new BCryptPasswordEncoder()` | `config/SecurityConfig` 定义 `@Bean PasswordEncoder`，两处构造器注入接口 |
| 单测 | — | `AuthServiceTest` / `AdminUserServiceTest` 同步传入 encoder，**104 用例全通过** |

验证：`mvn test` 104 passed；后端重打包重启后冒烟登录正常。
