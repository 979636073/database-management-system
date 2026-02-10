package com.example.dmdb.service;

import com.example.dmdb.config.DynamicContext;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectionManager extends AbstractRoutingDataSource {

    // 【新增】日志记录器，用于排查超时问题
    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

    private static final Map<Object, Object> targetDataSources = new ConcurrentHashMap<>();

    // 缓存 SQL 控制台的专用长连接
    private static final Map<String, Connection> consoleConnections = new ConcurrentHashMap<>();

    // 缓存 SQL 控制台的事务状态 (true=有未提交事务, false=无)
    private static final Map<String, Boolean> consoleDirtyStatus = new ConcurrentHashMap<>();

    @Override
    protected Object determineCurrentLookupKey() {
        return DynamicContext.getKey();
    }

    @Override
    public void afterPropertiesSet() {
        super.setTargetDataSources(targetDataSources);
        super.afterPropertiesSet();
    }

    /**
     * 添加数据源
     * @param key 连接ID
     * @param host 主机
     * @param port 端口
     * @param user 用户名
     * @param password 密码
     * @param dbType 数据库类型 (DM/ORACLE)
     * @param serviceName 服务名 (Oracle专用)
     */
    public void addDataSource(String key, String host, String port, String user, String password, String dbType, String serviceName) throws SQLException {
        // 【探针日志 1】记录开始请求的时间和参数
        log.info(">>> [ConnectionManager] 开始创建连接. ID: {}, Type: {}, Host: {}", key, dbType, host);

        if (targetDataSources.containsKey(key)) {
            log.info(">>> [ConnectionManager] 连接已存在，先移除旧连接: {}", key);
            removeDataSource(key);
        }

        HikariDataSource ds = new HikariDataSource();

        // 确定数据库类型，默认为 DM
        String finalDbType = (dbType == null || dbType.trim().isEmpty()) ? "DM" : dbType.toUpperCase();

        // 将数据库类型注册到 DynamicContext
        DynamicContext.setDbType(key, finalDbType);

        if ("ORACLE".equals(finalDbType)) {
            // Oracle 连接配置
            ds.setJdbcUrl("jdbc:oracle:thin:@" + host + ":" + port + "/" + serviceName);
            ds.setDriverClassName("oracle.jdbc.OracleDriver");
            ds.setConnectionTestQuery("SELECT 1 FROM DUAL");

            // 【网络优化】防止底层 Socket 阻塞导致前端超时
            // 这些参数能强制驱动在网络不通时快速抛出异常，而不是无限等待
            ds.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT", "5000"); // 5秒连接超时
            ds.addDataSourceProperty("oracle.jdbc.ReadTimeout", "10000");   // 10秒读取超时
        } else {
            // 达梦数据库连接配置
            ds.setJdbcUrl("jdbc:dm://" + host + ":" + port);
            ds.setDriverClassName("dm.jdbc.driver.DmDriver");

            // 达梦驱动超时配置
            ds.addDataSourceProperty("loginTimeout", "5");
            ds.addDataSourceProperty("socketTimeout", "10000");
        }

        ds.setUsername(user);
        ds.setPassword(password);

        // 连接池通用配置
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        ds.setConnectionTimeout(5000); // 从池中获取连接的等待时间
        ds.setValidationTimeout(3000);
        ds.setIdleTimeout(600000);

        log.info(">>> [ConnectionManager] 数据源配置完毕，准备建立物理连接(这是最容易超时的一步)...");
        long start = System.currentTimeMillis();

        // 【探针日志 2】立即尝试建立连接以验证有效性
        try (Connection conn = ds.getConnection()) {
            long duration = System.currentTimeMillis() - start;
            // 如果这里耗时超过 5000ms，说明网络极慢或驱动初始化卡死（如 Random 熵不足）
            log.info(">>> [ConnectionManager] 物理连接建立成功! 耗时: {} ms", duration);
        } catch (SQLException e) {
            long duration = System.currentTimeMillis() - start;
            log.error(">>> [ConnectionManager] 物理连接建立失败! 耗时: {} ms. 错误信息: {}", duration, e.getMessage());
            ds.close(); // 失败必须关闭，释放资源
            throw e;
        }

        targetDataSources.put(key, ds);
        super.setTargetDataSources(targetDataSources);
        super.afterPropertiesSet();

        log.info(">>> [ConnectionManager] 数据源添加完成，已加入路由映射.");
    }

    public void removeDataSource(String key) {
        log.info(">>> [ConnectionManager] 正在移除连接: {}", key);
        closeConsoleConnection(key);

        // 清理上下文中的数据库类型记录
        DynamicContext.removeDbType(key);

        Object ds = targetDataSources.remove(key);
        if (ds != null) {
            if (ds instanceof HikariDataSource) {
                ((HikariDataSource) ds).close();
            } else if (ds instanceof Closeable) {
                try {
                    ((Closeable) ds).close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        super.setTargetDataSources(targetDataSources);
        super.afterPropertiesSet();
    }

    public boolean hasDataSource(String key) {
        return targetDataSources.containsKey(key);
    }

    /**
     * 获取控制台专用长连接
     */
    public static Connection getConsoleConnection(String key) throws SQLException {
        if (key == null) return null;
        Connection existingConn = consoleConnections.get(key);
        if (existingConn != null && !existingConn.isClosed()) {
            return existingConn;
        }

        Object dsObj = targetDataSources.get(key);
        if (dsObj instanceof DataSource) {
            Connection newConn = ((DataSource) dsObj).getConnection();
            consoleConnections.put(key, newConn);
            consoleDirtyStatus.put(key, false);
            return newConn;
        }
        return null;
    }

    /**
     * 获取普通短连接
     */
    public static Connection getNewConnection(String key) throws SQLException {
        if (key == null) return null;
        Object dsObj = targetDataSources.get(key);
        if (dsObj instanceof DataSource) {
            return ((DataSource) dsObj).getConnection();
        }
        return null;
    }

    public static void closeConsoleConnection(String key) {
        if (key == null) return;
        Connection conn = consoleConnections.remove(key);
        consoleDirtyStatus.remove(key);

        if (conn != null) {
            try {
                if (!conn.isClosed()) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void setDirty(String key, boolean dirty) {
        if (key != null) consoleDirtyStatus.put(key, dirty);
    }

    public static boolean isDirty(String key) {
        return key != null && consoleDirtyStatus.getOrDefault(key, false);
    }
}