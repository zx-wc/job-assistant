CREATE DATABASE IF NOT EXISTS job_assistant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE job_assistant;

SET NAMES utf8mb4;

-- ============================================================================
-- A. 基础表
-- ============================================================================

-- A1. 用户账号（密码为 盐"airui-mall" + 明文 的 MD5）
CREATE TABLE IF NOT EXISTS t_user_account (
    id          BIGINT      AUTO_INCREMENT PRIMARY KEY COMMENT '用户主键',
    username    VARCHAR(32) NOT NULL COMMENT '登录用户名',
    password    VARCHAR(64) NOT NULL COMMENT '密码（MD5 加盐存储）',
    nickname    VARCHAR(32) DEFAULT '' COMMENT '昵称',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户账号表';

-- A2. 敏感词（AI 输出过滤使用，type: deny 拦截 / allow 放行）
CREATE TABLE IF NOT EXISTS t_sensitive_word (
    id          BIGINT      AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    word        VARCHAR(64) NOT NULL COMMENT '敏感词内容',
    type        VARCHAR(16) NOT NULL DEFAULT 'deny' COMMENT 'deny 拦截 / allow 放行',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_type (type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='敏感词表';

-- ============================================================================
-- B. 求职业务表
-- ============================================================================

-- B1. 简历资料
CREATE TABLE IF NOT EXISTS t_job_resume (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '简历主键',
    user_id         BIGINT       NOT NULL COMMENT '所属用户',
    title           VARCHAR(128) NOT NULL COMMENT '简历标题',
    candidate_name  VARCHAR(64)  DEFAULT '' COMMENT '求职者姓名',
    target_position VARCHAR(128) DEFAULT '' COMMENT '目标岗位',
    summary         VARCHAR(1024) DEFAULT '' COMMENT '一句话概述',
    content         MEDIUMTEXT   COMMENT '简历正文（入库前脱敏）',
    status          TINYINT      DEFAULT 1 COMMENT '1 正常 / 0 归档',
    current_version INT          DEFAULT 1 COMMENT '当前版本号',
    deleted         TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_resume_user (user_id, deleted),
    INDEX idx_resume_update (update_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='简历资料表';

-- B2. 简历版本快照
CREATE TABLE IF NOT EXISTS t_job_resume_version (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '版本主键',
    resume_id   BIGINT       NOT NULL COMMENT '简历主键',
    version_no  INT          NOT NULL COMMENT '版本号（从 1 递增）',
    title       VARCHAR(128) DEFAULT '' COMMENT '标题快照',
    content     MEDIUMTEXT   COMMENT '正文快照',
    change_note VARCHAR(255) DEFAULT '' COMMENT '版本说明',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_resume_version (resume_id, version_no),
    INDEX idx_version_resume (resume_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='简历版本表';

-- B3. 岗位信息
CREATE TABLE IF NOT EXISTS t_job_position (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '岗位主键',
    user_id      BIGINT       NOT NULL COMMENT '所属用户',
    company      VARCHAR(128) DEFAULT '' COMMENT '公司名称',
    position     VARCHAR(128) NOT NULL COMMENT '岗位名称',
    city         VARCHAR(64)  DEFAULT '' COMMENT '工作城市',
    salary_range VARCHAR(64)  DEFAULT '' COMMENT '薪资范围',
    job_desc     TEXT         COMMENT '岗位职责',
    requirements TEXT         COMMENT '任职要求',
    source_url   VARCHAR(512) DEFAULT '' COMMENT '来源链接',
    status       TINYINT      DEFAULT 1 COMMENT '1 关注中 / 0 已关闭',
    deleted      TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_position_user (user_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='岗位信息表';

-- B4. 简历与岗位分析报告
CREATE TABLE IF NOT EXISTS t_job_analysis (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '分析主键',
    user_id          BIGINT       NOT NULL COMMENT '所属用户',
    resume_id        BIGINT       NOT NULL COMMENT '简历主键',
    position_id      BIGINT       NOT NULL COMMENT '岗位主键',
    match_score      INT          DEFAULT 0 COMMENT '综合匹配度 0-100',
    summary          VARCHAR(2048) DEFAULT '' COMMENT '总体结论',
    strengths        TEXT         COMMENT '优势（JSON 数组）',
    weaknesses       TEXT         COMMENT '短板（JSON 数组）',
    suggestions      TEXT         COMMENT '改进建议（JSON 数组）',
    missing_skills   TEXT         COMMENT '缺失技能（JSON 数组）',
    interview_focus  TEXT         COMMENT '面试重点（JSON 数组）',
    dimension_scores TEXT         COMMENT '维度评分（JSON 对象）',
    model            VARCHAR(64)  DEFAULT '' COMMENT '生成模型',
    degraded         TINYINT      DEFAULT 0 COMMENT '1 本地降级',
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_analysis_user (user_id, create_time),
    INDEX idx_analysis_resume (resume_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='简历与岗位分析报告表';

-- B5. 模拟面试会话
CREATE TABLE IF NOT EXISTS t_job_interview_session (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '会话主键',
    user_id         BIGINT       NOT NULL COMMENT '所属用户',
    resume_id       BIGINT       DEFAULT NULL COMMENT '关联简历',
    position_id     BIGINT       DEFAULT NULL COMMENT '关联岗位',
    title           VARCHAR(128) DEFAULT '' COMMENT '面试标题',
    difficulty      VARCHAR(16)  DEFAULT 'NORMAL' COMMENT 'EASY/NORMAL/HARD',
    total_questions INT          DEFAULT 0 COMMENT '题目总数',
    answered_count  INT          DEFAULT 0 COMMENT '已作答题数',
    status          TINYINT      DEFAULT 0 COMMENT '0 进行中 / 1 已结束',
    overall_score   INT          DEFAULT NULL COMMENT '综合得分',
    model           VARCHAR(64)  DEFAULT '' COMMENT '出题模型',
    degraded        TINYINT      DEFAULT 0 COMMENT '1 本地降级',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    end_time        DATETIME     DEFAULT NULL COMMENT '结束时间',
    INDEX idx_session_user (user_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='模拟面试会话表';

-- B6. 面试题目与作答
CREATE TABLE IF NOT EXISTS t_job_interview_question (
    id              BIGINT      AUTO_INCREMENT PRIMARY KEY COMMENT '题目主键',
    session_id      BIGINT      NOT NULL COMMENT '所属会话',
    question_no     INT         NOT NULL COMMENT '题号',
    question        TEXT        NOT NULL COMMENT '题干',
    question_type   VARCHAR(32) DEFAULT '技能' COMMENT '技能/项目/行为/岗位匹配/追问',
    expected_points TEXT        COMMENT '考察要点（JSON 数组）',
    answer          MEDIUMTEXT  COMMENT '作答文本',
    answer_feedback TEXT        COMMENT '单题点评',
    score           INT         DEFAULT NULL COMMENT '单题得分 0-100',
    status          TINYINT     DEFAULT 0 COMMENT '0 待作答 / 1 已作答',
    degraded        TINYINT     DEFAULT 0 COMMENT '1 本地降级',
    create_time     DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    answer_time     DATETIME    DEFAULT NULL COMMENT '作答时间',
    UNIQUE KEY uk_session_question (session_id, question_no),
    INDEX idx_question_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='面试题目与作答表';

-- B7. 面试反馈报告
CREATE TABLE IF NOT EXISTS t_job_interview_feedback (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '报告主键',
    session_id       BIGINT       NOT NULL COMMENT '所属会话',
    overall_score    INT          DEFAULT 0 COMMENT '综合得分',
    summary          VARCHAR(2048) DEFAULT '' COMMENT '总体评价',
    strengths        TEXT         COMMENT '亮点（JSON 数组）',
    weaknesses       TEXT         COMMENT '不足（JSON 数组）',
    suggestions      TEXT         COMMENT '改进建议（JSON 数组）',
    dimension_scores TEXT         COMMENT '维度评分（JSON 对象）',
    model            VARCHAR(64)  DEFAULT '' COMMENT '生成模型',
    degraded         TINYINT      DEFAULT 0 COMMENT '1 本地降级',
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_feedback_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='面试反馈报告表';

-- B8. 大模型调用日志（网关可观测性）
CREATE TABLE IF NOT EXISTS t_llm_call_log (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '日志主键',
    user_id           BIGINT       DEFAULT NULL COMMENT '调用用户',
    scene             VARCHAR(64)  NOT NULL COMMENT '业务场景',
    model             VARCHAR(64)  DEFAULT '' COMMENT '模型',
    prompt_tokens     INT          DEFAULT 0 COMMENT '输入 token',
    completion_tokens INT          DEFAULT 0 COMMENT '输出 token',
    total_tokens      INT          DEFAULT 0 COMMENT '总 token',
    duration_ms       BIGINT       DEFAULT 0 COMMENT '耗时毫秒',
    retry_count       INT          DEFAULT 0 COMMENT '重试次数',
    cached            TINYINT      DEFAULT 0 COMMENT '1 命中缓存',
    success           TINYINT      DEFAULT 1 COMMENT '1 成功 / 0 失败',
    degraded          TINYINT      DEFAULT 0 COMMENT '1 已降级',
    error_msg         VARCHAR(500) DEFAULT '' COMMENT '失败原因（已脱敏）',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    INDEX idx_llm_scene (scene, create_time),
    INDEX idx_llm_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='大模型调用日志表';

-- B9. 提示词模板（数据库模板优先于 classpath 内置模板）
CREATE TABLE IF NOT EXISTS t_llm_prompt_template (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '模板主键',
    scene       VARCHAR(64)  NOT NULL COMMENT '场景',
    name        VARCHAR(128) DEFAULT '' COMMENT '模板名称',
    content     MEDIUMTEXT   NOT NULL COMMENT '模板内容（{变量} 占位）',
    description VARCHAR(255) DEFAULT '' COMMENT '说明',
    enabled     TINYINT      DEFAULT 1 COMMENT '1 启用 / 0 停用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_scene (scene)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='提示词模板表';

-- B10. 登录会话（令牌）
CREATE TABLE IF NOT EXISTS t_job_user_session (
    id               BIGINT      AUTO_INCREMENT PRIMARY KEY COMMENT '会话主键',
    user_id          BIGINT      NOT NULL COMMENT '用户主键',
    username         VARCHAR(32) DEFAULT '' COMMENT '用户名',
    token            VARCHAR(64) NOT NULL COMMENT '登录令牌',
    expire_time      DATETIME    NOT NULL COMMENT '过期时间',
    last_access_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '最后访问时间',
    create_time      DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '签发时间',
    UNIQUE KEY uk_token (token),
    INDEX idx_session_user (user_id),
    INDEX idx_session_expire (expire_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='登录会话（令牌）表';

-- B11. 用户角色
CREATE TABLE IF NOT EXISTS t_job_user_role (
    user_id     BIGINT      NOT NULL PRIMARY KEY COMMENT '用户主键',
    role        VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户角色表';

-- ============================================================================
-- C. 演示数据
-- ============================================================================

-- 演示账号：jobuser / 123456（普通用户）、admin / 123456（管理员）
INSERT INTO t_user_account (username, password, nickname)
SELECT 'jobuser', '9df8755dcc463e1c780d996ce6a12e2e', '演示求职者'
WHERE NOT EXISTS (SELECT 1 FROM t_user_account WHERE username = 'jobuser');

INSERT INTO t_user_account (username, password, nickname)
SELECT 'admin', '9df8755dcc463e1c780d996ce6a12e2e', '系统管理员'
WHERE NOT EXISTS (SELECT 1 FROM t_user_account WHERE username = 'admin');

INSERT INTO t_job_user_role (user_id, role)
SELECT id, 'ADMIN' FROM t_user_account WHERE username = 'admin'
ON DUPLICATE KEY UPDATE role = 'ADMIN';

INSERT INTO t_job_user_role (user_id, role)
SELECT id, 'USER' FROM t_user_account WHERE username = 'jobuser'
ON DUPLICATE KEY UPDATE role = 'USER';

-- 演示简历
INSERT INTO t_job_resume (user_id, title, candidate_name, target_position, summary, content, current_version)
SELECT u.id, 'Java 后端开发-秋招版', '张同学', 'Java 后端开发工程师',
       '计算机专业应届生，熟悉 Java/Spring Boot/MySQL，做过两个完整的后端项目。',
       '教育背景：某大学 计算机科学与技术 本科 2022-2026，GPA 3.6/4.0\n'
       '专业技能：Java、Spring Boot、MyBatis、MySQL、Redis、Linux、Git、Docker 基础\n'
       '项目经历：\n'
       '1. 校园二手交易平台（Spring Boot + MySQL + Redis）：负责订单与支付模块，使用 Redis 缓存热点商品，接口平均响应从 320ms 降到 90ms；\n'
       '2. 智能客服系统（Spring AI + 向量检索）：实现 FAQ 语义检索与多轮对话，FAQ 命中率提升到 72%。\n'
       '实习经历：某软件公司 后端开发实习 3 个月，参与报表导出接口开发与 SQL 优化。\n'
       '荣誉奖项：校程序设计竞赛二等奖。',
       1
FROM t_user_account u
WHERE u.username = 'jobuser'
  AND NOT EXISTS (SELECT 1 FROM t_job_resume r WHERE r.user_id = u.id AND r.title = 'Java 后端开发-秋招版');

INSERT INTO t_job_resume_version (resume_id, version_no, title, content, change_note)
SELECT r.id, 1, r.title, r.content, '初始版本'
FROM t_job_resume r
WHERE r.title = 'Java 后端开发-秋招版'
  AND NOT EXISTS (SELECT 1 FROM t_job_resume_version v WHERE v.resume_id = r.id AND v.version_no = 1);

-- 演示岗位
INSERT INTO t_job_position (user_id, company, position, city, salary_range, job_desc, requirements, source_url)
SELECT u.id, '某互联网公司', 'Java 后端开发工程师', '南京', '15k-25k',
       '负责公司核心业务系统的后端设计与开发；参与需求评审、接口设计、性能优化；保障线上服务稳定性。',
       '1. 熟悉 Java 基础与 JVM 内存模型，熟悉 Spring Boot / Spring MVC；\n'
       '2. 熟悉 MySQL 索引与 SQL 优化，了解 Redis 缓存使用场景；\n'
       '3. 熟悉 Linux 常用命令，了解 Docker / CI-CD；\n'
       '4. 有良好的编码习惯与问题定位能力，具备团队协作与沟通能力；\n'
       '5. 有高并发、消息队列（Kafka/RocketMQ）经验者优先。',
       ''
FROM t_user_account u
WHERE u.username = 'jobuser'
  AND NOT EXISTS (SELECT 1 FROM t_job_position p WHERE p.user_id = u.id AND p.position = 'Java 后端开发工程师');

-- 演示敏感词（AI 输出过滤：命中后脱敏为 *）
INSERT INTO t_sensitive_word (word, type)
SELECT '假货', 'deny' WHERE NOT EXISTS (SELECT 1 FROM t_sensitive_word WHERE word = '假货');
INSERT INTO t_sensitive_word (word, type)
SELECT '刷单', 'deny' WHERE NOT EXISTS (SELECT 1 FROM t_sensitive_word WHERE word = '刷单');

-- ============================================================================
-- D. 运维常用查询
-- ============================================================================
-- 当日 AI 调用统计
-- SELECT count(*) total, sum(success = 1) ok, sum(degraded = 1) degraded,
--        round(avg(duration_ms)) avg_ms, sum(total_tokens) tokens
-- FROM t_llm_call_log WHERE create_time >= CURDATE();
--
-- 失败明细（排查超时/限流/密钥）
-- SELECT id, scene, model, retry_count, duration_ms, error_msg
-- FROM t_llm_call_log WHERE success = 0 ORDER BY id DESC LIMIT 20;
--
-- 清理过期令牌
-- DELETE FROM t_job_user_session WHERE expire_time < NOW();
