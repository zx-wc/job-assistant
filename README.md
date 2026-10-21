# 智能求职辅助系统（A 部分 · 组长 zhangxu）

本仓库按课程《智能求职辅助系统 五人任务划分》组织：**当前只提交成员 A（zhangxu）的代码**，
即用户与权限、大模型 API 网关、向量检索 RAG、公共组件与接口契约；B/C/D/E 位于 `com.nuist.agent.b/c/d/e`
的**占位包**，由各成员补充实现。

> 完整五人代码在 git 历史中仍可找回（提交 `371d250` 及之前），需要时执行
> `git checkout 371d250 -- .` 或 `git revert` 即可恢复。

- 后端：Java 17 + Spring Boot 3.5.16 + MyBatis（注解式）+ MySQL 8 + Druid + Spring AI Alibaba（DashScope / 通义千问）+ Milvus 2.4（向量检索 RAG）
- 前端：原生 ES Module + Hash 路由 SPA（零构建，`src/main/resources/static/job/`，由成员 D 补充）
- 数据库：`job_assistant`，执行 `sql/schema.sql` 建库建表（由成员 E 补充）
- 测试：A 的 11 个测试类（AI 网关、鉴权、脱敏、RAG、管理端鉴权）

## 一、快速开始

```bash
# 1. 建库建表 + 演示数据（脚本内含 CREATE DATABASE / USE）
mysql -uroot -p < sql/schema.sql

# 2. 配置大模型密钥（只放服务端环境变量；不配置也能跑，AI 调用会走本地降级）
#    PowerShell: $env:DASHSCOPE_API_KEY="<你的 DashScope Key>"
#    Milvus（可选，简历/岗位向量检索）：$env:MILVUS_HOST / $env:MILVUS_PORT，默认 192.168.100.200:19530

# 3. 构建
mvn -DskipTests package

# 4. 启动（默认端口 8080，可用 --server.port 覆盖）
java -jar target/job-assistant-1.0.0.jar
```

- 前端入口：<http://127.0.0.1:8080/>（落地页）或 <http://127.0.0.1:8080/job/index.html>
- 演示账号：`jobuser / 123456`（普通用户）、`admin / 123456`（管理员）
- 健康检查：`GET /api/job/auth/ping`
- 离线演示：`java -jar target/job-assistant-1.0.0.jar --job.ai.enabled=false`，全部业务走本地规则降级

> 数据库账号密码在 `application.yml` 中通过 `${DB_USERNAME:root}` / `${DB_PASSWORD:Nuist@123}` 读取，
> 可用环境变量覆盖，无需改代码。

## 二、功能模块与代码位置（按五人分工划分包）

包结构按课程任务划分组织：**A 的代码统一在 `com.nuist.agent.zhangxu`**，B/C 用 `com.nuist.agent.b` / `com.nuist.agent.c`，
D 用 `com.nuist.agent.d`，E 用 `com.nuist.agent.e`（后四个目前只有 `package-info.java` 占位，等成员补齐）。

| 分工 | 模块 | 代码位置 | 状态 |
| --- | --- | --- | --- |
| A | 用户与权限 | `zhangxu/auth/`、`zhangxu/controller/JobAuthController.java` | ✅ 已提交：注册登录、32 位令牌 + 7 天滑动续期、注销、USER/ADMIN、拦截器 + ThreadLocal 用户上下文 |
| A | AI 网关 | `zhangxu/ai/`、`zhangxu/controller/JobAdminAiController.java` | ✅ 已提交：模板三级加载、超时、重试 + 备用模型、单用户限流、结果缓存、熔断降级、结构化 JSON、调用日志 |
| A | 向量检索 RAG | `zhangxu/rag/`、`zhangxu/config/JobVectorConfig.java` | ✅ 已提交：文本切块、Milvus 懒连接、按用户/来源检索、故障短路降级 |
| A | 公共组件 | `zhangxu/common/`、`zhangxu/config/`、`zhangxu/exception/`、`zhangxu/dto/`、`zhangxu/entity/`、`zhangxu/mapper/` | ✅ 已提交：统一响应 `R` 与错误码、全局异常、配置绑定、JSON 工具、敏感信息脱敏、接口 DTO、A 模块实体与 Mapper |
| B | 简历管理 | `com.nuist.agent.b.resume` | ⬜ 占位包，待成员 B 实现 |
| B | 岗位管理 | `com.nuist.agent.b.position` | ⬜ 占位包，待成员 B 实现 |
| B | 简历岗位分析 | `com.nuist.agent.b.analysis` | ⬜ 占位包，待成员 B 实现 |
| C | 模拟面试与反馈 | `com.nuist.agent.c.interview` | ⬜ 占位包，待成员 C 实现 |
| D | 前端页面 | `com.nuist.agent.d.web` + `src/main/resources/static/job/` | ⬜ 占位包，待成员 D 实现 |
| E | 数据库/测试/部署 | `com.nuist.agent.e.db` + `sql/`、`scripts/` | ⬜ 占位包，待成员 E 实现 |

> 说明：本仓库当前只提交 **A 的代码**；A 的 RAG 服务保留了与简历/岗位实体交互的方法签名，
> 待 B 的实体与 Mapper 补齐后即可编译运行（当前缺 B/C 代码，`mvn compile` 会报找不到符号，属预期）。

## 三、接口一览（前缀 `/api/job`，当前仅 A 的接口）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/register`、`/auth/login` | 注册 / 登录，返回令牌 |
| GET | `/auth/me`、`/auth/ping` | 当前用户 / 健康检查（免登录） |
| POST | `/auth/logout` | 注销令牌 |
| GET | `/admin/ai/config` | 网关配置（密钥脱敏，仅 ADMIN） |
| GET | `/admin/ai/stats` | 调用统计（仅 ADMIN） |
| GET | `/admin/ai/scenes` | 场景与模板来源（仅 ADMIN） |
| GET/PUT/DELETE | `/admin/ai/templates`、`/admin/ai/templates/{scene}` | 模板列表 / 保存 / 还原（仅 ADMIN） |
| GET | `/admin/ai/logs` | 调用日志分页（仅 ADMIN） |
| GET | `/admin/ai/vector` | 向量库状态（仅 ADMIN） |
| POST | `/admin/ai/vector/reindex` | 重建索引（仅 ADMIN） |
| POST | `/admin/ai/vector/search` | 语义检索调试（仅 ADMIN） |

B/C/D 的接口（简历、岗位、分析、面试、概览）随各自模块一起补充。

详细契约见课程文档《04-接口规范与详细设计.docx》（交付目录 `C:\Users\wangchuan\Desktop\软件工程实践实验\报告\`）。

## 四、验证

```bash
# A 模块单元测试（11 个测试类：AI 网关、缓存、限流、JSON、模板、令牌、脱敏、RAG、管理端鉴权）
mvn test

# 说明：完整版（含 B/C 的 223 个用例）在提交 371d250 中，可用 git checkout 371d250 -- src/test 找回
```

> 当前仓库缺少 B/C 的实体与 Mapper，`mvn test` 会因找不到符号失败，属按分工裁剪后的预期状态。

## 五、目录结构

```
job-assistant/
├── pom.xml
├── src/main/java/com/nuist/agent/
│   ├── JobAssistantApplication.java # 启动类（@MapperScan com.nuist.agent.mapper + zhangxu.mapper）
│   ├── zhangxu/                     # A：ai/auth/rag/common/config/controller/dto/entity/mapper/exception/service
│   ├── b/resume|position|analysis   # B：占位包（package-info.java）
│   ├── c/interview                  # C：占位包
│   ├── d/web                        # D：占位包（前端在 static/job/）
│   └── e/db                         # E：占位包（脚本在 sql/、scripts/）
├── src/main/resources/
│   ├── application.yml              # 端口/数据源/网关与业务参数
│   └── prompts/job/*.st             # 5 个场景内置提示词模板
└── src/test/java/com/nuist/agent/zhangxu/  # A 的 11 个测试类
```

## 六、安全与合规

- DashScope 密钥只从环境变量 `DASHSCOPE_API_KEY` 读取，不入库、不下发前端；管理端只返回脱敏值。
- 公共组件提供 `PrivacyMasker`（手机号/邮箱/身份证/银行卡掩码），供 B 的简历与岗位模块调用。
- 所有业务查询以登录上下文 `userId` 为条件；管理员接口二次校验角色（`UserContextHolder.requireAdmin()`）。
- AI 输出经敏感词过滤（忽略纯数字命中，避免误伤"2024 届""15k-25k"）。
- Druid 监控页默认关闭；密码 MD5 加盐存储；令牌 7 天过期且可注销。

## 七、与课程文档的关系

- 课程文档 00-08 已转换为 Word，交付在 `C:\Users\wangchuan\Desktop\软件工程实践实验\报告\`（9 份 .docx，
  含封面、目录页码、表格、代码块，`06-测试报告` 末尾内嵌 11 张页面截图）。文档描述的是**完整五人系统**。
- 本仓库当前只提交 A 的代码，`sql/`、`scripts/`、前端页面、B/C 模块已按分工移除（包结构保留）。
- 完整可运行版本在 git 历史提交 `371d250`（含 B/C/D/E 全部代码、223 个测试、建库脚本与冒烟脚本），需要时：
  `git checkout 371d250 -- .` 恢复全部，或 `git checkout 371d250 -- src/test sql scripts` 只取回测试与脚本。
