# 智能求职辅助系统（独立部署版）

从课程工程 `sb_test` 中抽取的**求职辅助子系统**，只保留简历、岗位、分析、模拟面试、反馈、用户权限、AI 网关与向量检索，
不包含原电商 AI 客服的 FAQ / 商品 / 订单相关功能，依赖更少、启动更快，可单独部署与提交。
> 工程位置：`C:\soft\job-assistant`（标准 Spring Boot 工程，含 `mvnw` 包装器；也可用 `mvn.cmd` 或 IDE 直接导入）。

- 后端：Java 17 + Spring Boot 3.5.16 + MyBatis（注解式）+ MySQL 8 + Druid + Spring AI Alibaba（DashScope / 通义千问）+ Milvus 2.4（向量检索 RAG）
- 前端：原生 ES Module + Hash 路由 SPA（零构建），`src/main/resources/static/job/`
- 数据库：`job_assistant`，执行 `sql/schema.sql` 一键建库建表
- 测试：223 个自动化用例（JUnit 5 + Mockito + MockMvc），另有端到端冒烟脚本与页面截图脚本

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

包结构按课程任务划分组织：**A（组长）的代码统一放在 `com.nuist.agent.zhangxu` 包下**，B/C 的业务模块在 `com.nuist.agent.job.*`，D 的前端在 `static/job/`，E 的脚本与建库在 `sql/`、`scripts/`。

| 分工 | 模块 | 代码位置 | 说明 |
| --- | --- | --- | --- |
| A | 用户与权限 | `zhangxu/auth/`、`zhangxu/controller/JobAuthController.java` | 注册登录、32 位令牌 + 7 天滑动续期、注销、USER/ADMIN、拦截器 + ThreadLocal 用户上下文 |
| A | AI 网关 | `zhangxu/ai/`、`zhangxu/controller/JobAdminAiController.java` | 模板三级加载、超时、重试 + 备用模型、单用户限流、结果缓存、熔断降级、结构化 JSON、调用日志 |
| A | 向量检索 RAG | `zhangxu/rag/`、`zhangxu/config/JobVectorConfig.java` | 简历/岗位切块写入 Milvus，分析与出题前语义召回注入提示词；懒连接 + 不可用自动降级 |
| A | 公共组件 | `zhangxu/common/`、`zhangxu/config/`、`zhangxu/exception/`、`zhangxu/dto/`、`zhangxu/entity/`、`zhangxu/mapper/` | 统一响应 `R` 与错误码、全局异常、配置绑定、JSON 工具、敏感信息脱敏、接口 DTO、A 模块实体与 Mapper |
| B | 简历管理 | `job/resume/` | 增删改查、txt/md 导入、正文变更自动留版本、版本查看/存档/回滚 |
| B | 岗位管理 | `job/position/` | 岗位与 JD 录入、检索、增删改查 |
| B | 简历岗位分析 | `job/analysis/` | 匹配度 + 维度评分 + 优势/短板/建议/缺失技能/面试重点，AI 不可用时关键词降级 |
| C | 模拟面试 | `job/interview/` | 围绕岗位出题、逐题评分点评、多轮追问、整场反馈报告、训练记录 |
| A/D | 求职概览与接口层 | `job/dashboard/`、`job/controller/` | 概览统计聚合；`/api/job/**` 共 42 个接口（含 A 的两个控制器在 `zhangxu/controller`） |
| D | 前端 | `static/job/` | 登录注册、概览、简历、岗位、分析、面试、反馈、AI 网关管理共 11 个页面 |
| E | 数据库与脚本 | `sql/schema.sql`、`scripts/` | 13 张表（2 张基础 + 11 张业务）+ 索引 + 演示数据；冒烟/截图/文档脚本 |

> 其余成员若要按同样方式拆包，告我名字即可（如 B 的 `job/resume|position|analysis` 迁到 `com.nuist.agent.<名字>.*`）。

## 三、接口一览（前缀 `/api/job`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/register`、`/auth/login` | 注册 / 登录，返回令牌 |
| GET | `/auth/me`、`/auth/ping` | 当前用户 / 健康检查（免登录） |
| POST | `/auth/logout` | 注销令牌 |
| GET/POST | `/resumes` | 简历分页 / 新建 |
| GET/PUT/DELETE | `/resumes/{id}` | 详情 / 编辑 / 删除 |
| GET/POST | `/resumes/{id}/versions` | 版本列表 / 手动存档 |
| GET | `/resumes/{id}/versions/{no}` | 版本详情 |
| POST | `/resumes/{id}/versions/{no}/restore` | 回滚版本 |
| POST | `/resumes/upload-text` | 上传 txt/md（返回脱敏正文） |
| GET/POST/PUT/DELETE | `/positions`、`/positions/{id}` | 岗位增删改查 |
| POST/GET | `/analyses`、`/analyses/{id}` | 生成分析 / 报告列表与详情 |
| POST/GET | `/interviews`、`/interviews/{id}` | 开始面试 / 会话详情 |
| POST | `/interviews/{id}/answers`、`/follow-up`、`/finish` | 作答评分 / 追问 / 结束出报告 |
| GET | `/interviews/{id}/report` | 反馈报告 |
| GET | `/dashboard` | 求职概览 |
| GET/PUT/DELETE | `/admin/ai/config`、`/stats`、`/scenes`、`/templates/{scene}`、`/logs` | 网关配置（密钥脱敏）、统计、模板管理、调用日志（仅 ADMIN） |

详细契约见课程文档《04-接口规范与详细设计.docx》（交付目录 `C:\Users\wangchuan\Desktop\软件工程实践实验\报告\`）。

## 四、验证

```bash
# 自动化测试（223 个用例，无需数据库与网络）
mvn test

# 端到端冒烟（需先启动服务，默认 http://127.0.0.1:8080）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke_test.ps1

# 页面截图（可选，需 Chrome 以 --remote-debugging-port=9222 启动）
node scripts/ui_shots.mjs shots
```

## 五、目录结构

```
job-assistant/
├── pom.xml
├── sql/schema.sql                   # 13 张表 + 演示数据
├── scripts/                         # 冒烟脚本、截图脚本、请求样例、md→docx 工具
├── src/main/java/com/nuist/agent/
│   ├── JobAssistantApplication.java # 启动类（@MapperScan com.nuist.agent.mapper + zhangxu.mapper）
│   ├── zhangxu/                     # A（组长）：ai/auth/rag/common/config/controller/dto/entity/mapper/exception/service
│   ├── job/                         # B/C 业务：resume/position/analysis/interview/controller/dashboard
│   ├── entity/、mapper/             # B/C 模块的实体与注解式 MyBatis Mapper
├── src/main/resources/
│   ├── application.yml              # 端口/数据源/网关与业务参数
│   ├── prompts/job/*.st             # 5 个场景内置提示词模板
│   └── static/{index.html,job/}     # 落地页 + 前端 SPA
└── src/test/java/com/nuist/agent/   # 223 个用例（19 个测试类；A 模块测试在 zhangxu/ 下）
```

## 六、安全与合规

- DashScope 密钥只从环境变量 `DASHSCOPE_API_KEY` 读取，不入库、不下发前端；管理端只返回脱敏值。
- 简历/岗位正文入库前对手机号、邮箱、身份证、银行卡脱敏（`PrivacyMasker`）。
- 所有业务查询以登录上下文 `userId` 为条件；管理员接口二次校验角色。
- AI 输出经敏感词过滤（忽略纯数字命中，避免误伤"2024 届""15k-25k"）。
- Druid 监控页默认关闭；密码 MD5 加盐存储；令牌 7 天过期且可注销。

## 七、与课程文档的关系

课程文档 00-08 已转换为 Word 文档，交付在 `C:\Users\wangchuan\Desktop\软件工程实践实验\报告\`（共 9 份 .docx，
含封面、目录页码、表格、代码块，`06-测试报告` 末尾内嵌 11 张页面截图）。
工程内不再保留 Markdown 源文件；如需重新生成 Word，可用 `scripts/md2docx.py` + `scripts/build_course_docx.py`
（先准备好 Markdown 源目录，再执行：`python scripts/build_course_docx.py <md目录> <输出目录>`）。
文档中的路径引用（`src/main/java/com/nuist/agent/zhangxu/...`、`job/...`、`sql/...`、`scripts/...`）在本工程中均存在。
