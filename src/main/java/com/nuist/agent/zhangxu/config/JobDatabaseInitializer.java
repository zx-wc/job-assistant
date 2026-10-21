package com.nuist.agent.zhangxu.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 启动期自动建库建表，解决换一台机器启动就报 {@code Unknown database 'job_assistant'} 的问题。
 *
 * <p>执行时机在 Spring 容器刷新之前（{@link ApplicationContextInitializer}），因此早于
 * DataSource / Druid 连接池初始化，顺序为：</p>
 * <ol>
 *   <li>解析 {@code spring.datasource.url}，取出 host / port / 库名；</li>
 *   <li>先连 MySQL 服务（不指定库），库不存在就 {@code CREATE DATABASE}；</li>
 *   <li>再连目标库，核心表缺失时执行 {@code classpath:db/schema.sql}（建表 + 演示数据）。</li>
 * </ol>
 *
 * <p>失败策略：MySQL 连不上时不阻断启动，把原始错误留给 DataSource；能连上但建库/建表失败时，
 * 抛出带操作指引的异常，避免只看到一句 Unknown database。</p>
 */
public class JobDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(JobDatabaseInitializer.class);

    /** jdbc:mysql://host[:port]/database[?params] */
    private static final Pattern MYSQL_URL = Pattern.compile(
            "^jdbc:mysql://([^/:?]+)(?::(\\d+))?/([^?]*)(?:\\?(.*))?$",
            Pattern.CASE_INSENSITIVE);

    /** 建表脚本随 jar 分发，内容与 sql/schema.sql 一致（去掉 CREATE DATABASE / USE 两行）。 */
    private static final String SCHEMA_LOCATION = "db/schema.sql";

    /** 探测表：存在即认为库已经初始化过。 */
    private static final String PROBE_TABLE = "t_sensitive_word";

    private static final String DEFAULT_PARAMS = "useUnicode=true&characterEncoding=UTF-8&useSSL=false"
            + "&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();
        String url = env.getProperty("spring.datasource.url");
        String username = env.getProperty("spring.datasource.username", "root");
        String password = env.getProperty("spring.datasource.password", "");

        if (url == null || url.isBlank()) {
            return;
        }
        Matcher matcher = MYSQL_URL.matcher(url.trim());
        if (!matcher.matches()) {
            log.debug("数据源不是 MySQL，跳过自动建库：{}", url);
            return;
        }

        String host = matcher.group(1);
        String port = (matcher.group(2) == null || matcher.group(2).isBlank()) ? "3306" : matcher.group(2);
        String database = matcher.group(3) == null ? "" : matcher.group(3).trim();
        String params = (matcher.group(4) == null || matcher.group(4).isBlank()) ? DEFAULT_PARAMS : matcher.group(4);
        if (database.isEmpty()) {
            log.debug("JDBC URL 未指定库名，跳过自动建库");
            return;
        }

        String serverUrl = "jdbc:mysql://" + host + ":" + port + "/?" + params;
        boolean created = createDatabaseIfMissing(serverUrl, username, password, database);

        try {
            initSchemaIfMissing(url, username, password);
        } catch (SQLException e) {
            throw new IllegalStateException("数据库 " + database + " 的表结构初始化失败：" + e.getMessage()
                    + "。可执行随程序分发的 db/schema.sql 手工建表，或检查该账号是否有建表权限。", e);
        }

        if (created) {
            log.info("首次启动：数据库 {} 已创建并初始化完成（演示账号 jobuser/123456、admin/123456）", database);
        }
    }

    /** 连到 MySQL 服务层，库不存在就建库；返回是否新建。连不上服务时只告警，不阻断启动。 */
    private boolean createDatabaseIfMissing(String serverUrl, String username, String password, String database) {
        Connection connection;
        try {
            connection = DriverManager.getConnection(serverUrl, username, password);
        } catch (SQLException e) {
            log.warn("跳过自动建库：无法连接 MySQL（{}），原因：{}", serverUrl, e.getMessage());
            return false;
        }

        try (Connection conn = connection) {
            if (databaseExists(conn, database)) {
                log.info("数据库 {} 已存在，跳过建库", database);
                return false;
            }
            try (Statement statement = conn.createStatement()) {
                statement.executeUpdate("CREATE DATABASE `" + escapeIdentifier(database)
                        + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            }
            log.info("数据库 {} 不存在，已自动创建（utf8mb4）", database);
            return true;
        } catch (SQLException e) {
            throw new IllegalStateException("数据库 " + database + " 不存在且创建失败：" + e.getMessage()
                    + "。请确认 MySQL 账号 " + username + " 拥有 CREATE 权限，或先执行 db/schema.sql 手工建库。", e);
        }
    }

    private boolean databaseExists(Connection conn, String database) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?")) {
            ps.setString(1, database);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** 连到目标库，核心表缺失时执行建表脚本（脚本本身用 IF NOT EXISTS，可重复执行）。 */
    private void initSchemaIfMissing(String url, String username, String password) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            if (tableExists(conn)) {
                log.debug("数据表已存在，跳过建表初始化");
                return;
            }
            ScriptUtils.executeSqlScript(conn,
                    new EncodedResource(new ClassPathResource(SCHEMA_LOCATION), StandardCharsets.UTF_8));
            log.info("检测到数据表缺失，已执行 {} 完成建表与演示数据初始化", SCHEMA_LOCATION);
        }
    }

    private boolean tableExists(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
            ps.setString(1, PROBE_TABLE);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private String escapeIdentifier(String identifier) {
        return identifier.replace("`", "``");
    }
}
