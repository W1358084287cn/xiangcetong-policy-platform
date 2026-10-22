# 乡策通助农扶持政策管理平台的设计与实现 2.0

村民 H5 + PC 管理端（三模式）+ Spring Boot Maven 多模块后端的惠农补贴申报系统。

## 工程结构

| 目录 | 说明 | 端口 |
|------|------|------|
| `server/` | Spring Boot 3.4.2 + Spring AI 1.0.0-M7 + MyBatis + Maven 多模块（10 子模块） + MySQL + Redis | 8080 |
| `web-h5/` | 村民端 Vue3 + Vite + Vant4（适老、政策匹配 / 申报 / 我的） | 5173 |
| `web-admin/` | PC 管理端 Vue3 + Vite + Element Plus（统一管理端，登录选身份） | 5177 |
| `docs/` | 需求、架构、API、匹配算法、项目记忆 | — |
| `scripts/` | run.ps1 启动脚本、logs/ | — |

### 后端模块划分

```
server/
├── xct-common    # 通用层（返回封装、异常、分页、上下文、常量、配置）
├── xct-log       # 操作与流程日志
├── xct-user      # 村民与后台账号管理
├── xct-policy    # 政策查询 / 收藏 / 匹配
├── xct-apply     # 补贴申报与审批（村初审 → 镇终审 → 发放）
├── xct-auth      # 登录鉴权（JWT + Redis 会话）
├── xct-notice    # 通知公告
├── xct-file      # 材料图片上传
├── xct-ai        # AI 知识库与智能问答（RAG + DeepSeek）
└── xct-boot      # 启动模块（装配所有子模块、配置、SQL 种子）
```

## 快速启动

**一键（推荐）**：双击根目录 `run.bat`。
> 脚本会自动 Maven 打包、启动后端 8080、启动 H5 村民端 5173、统一管理端 5177，并打开浏览器。

**手动启动：**

1. 确保 MySQL 库 `xiangcetong` 已存在，Redis `localhost:6379` 已启动
2. 后端（Maven 打包 + 运行）：
   ```bash
   cd server && mvn package -DskipTests
   java -jar server/xct-boot/target/xiangcetong-server-1.0.0.jar
   ```
3. H5：`cd web-h5 && npm run dev`
4. 管理端（统一端口 5177，登录页选身份）：`cd web-admin && npm run dev:unified`

## 演示账号

**后台三端统一账号**（各端固定角色）：

| 用户名 | 密码 | 端 | 角色 |
|--------|------|-----|------|
| `admin` | `123456` | 村管端 | VILLAGE_ADMIN |
| `admin` | `123456` | 镇管端 | TOWN_ADMIN |
| `admin` | `123456` | 系统端 | SYS_ADMIN |

**村民账号**（密码均为 `123456`）：

| 手机号         | 角色 | 备注 |
|-------------|------|------|
| 15632489079 | 村民 | 正定县正定镇东村，小麦约 40 亩 |
| 15632489079 | 村民 | 正定县正定镇西村，畜禽养殖 |

**旧手机号登录**（密码均为 `123456`，仍兼容）：

| 手机号         | 角色 |
|-------------|------|
| 15632489079 | 村管（东村） |
| admin       | 村管（西村） |
| admin       | 镇管 |
| admin       | 系统管理员 |

## 核心特性

- **政策匹配**：MySQL 多条件 WHERE 过滤，非加权打分；匹配结果 FULL/NONE
- **申报审批**：草稿 → 提交 → 村初审 → 镇终审 → 发放，完整状态机流转
- **材料上传**：支持拍照上传，POST `/api/file/upload` 返回 URL
- **通知公告**：管理端发布，H5 首页展示
- **政策收藏**：村民可收藏关注的政策
- **AI 智能问答**：RAG 知识库 + DeepSeek-V3 对话，村民可 AI 咨询政策；管理端管理知识库文档与 AI 配置
- **操作日志**：审批流程日志 + 后台操作日志 + AI 调用用量日志
- **适老设计**：H5 全局字号放大，简化操作流程

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 3.4.2 + Spring AI 1.0.0-M7 + MyBatis + Maven 多模块，JDK 17 |
| 数据库 | MySQL `xiangcetong`（沿用已有表，禁止删库重建） |
| 缓存 | Redis（JWT 会话 token） |
| AI | Spring AI OpenAI 兼容协议 → 硅基流动（DeepSeek-V3 + BGE-large-zh-v1.5） |
| H5 前端 | Vue 3 + Vite + Vant 4 |
| PC 管理端 | Vue 3 + Vite + Element Plus（中文） |

## 版本说明

本仓库 **main** 分支仅保留本项目 2.0 源码（Maven 多模块后端 + 双前端），历史杂项已清空。