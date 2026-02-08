package com.example.dmdb.service;

import com.example.dmdb.config.DynamicContext;
import com.zaxxer.hikari.HikariDataSource;
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

    private static final Map<Object, Object> targetDataSources = new ConcurrentHashMap<>();

    // 缓存 SQL 控制台的专用长连接
    private static final Map<String, Connection> consoleConnections = new ConcurrentHashMap<>();

    // 【新增】缓存 SQL 控制台的事务状态 (true=有未提交事务, false=无)
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

    public void addDataSource(String key, String host, String port, String user, String password) throws SQLException {
        if (targetDataSources.containsKey(key)) {
            removeDataSource(key);
        }

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:dm://" + host + ":" + port);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setDriverClassName("dm.jdbc.driver.DmDriver");

        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        ds.setConnectionTimeout(5000);
        ds.setValidationTimeout(3000);
        ds.setIdleTimeout(600000);

        try (Connection conn = ds.getConnection()) {
        } catch (SQLException e) {
            ds.close();
            throw e;
        }

        targetDataSources.put(key, ds);
        super.setTargetDataSources(targetDataSources);
        super.afterPropertiesSet();
    }

    public void removeDataSource(String key) {
        closeConsoleConnection(key);

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
        Connection existingConn = consoleConnections.get(key);
        if (existingConn != null && !existingConn.isClosed()) {
            return existingConn;
        }

        Object dsObj = targetDataSources.get(key);
        if (dsObj instanceof DataSource) {
            Connection newConn = ((DataSource) dsObj).getConnection();
            consoleConnections.put(key, newConn);
            // 【新增】初始化事务状态为 false (Clean)
            consoleDirtyStatus.put(key, false);
            return newConn;
        }
        return null;
    }

    /**
     * 获取普通短连接
     */
    public static Connection getNewConnection(String key) throws SQLException {
        Object dsObj = targetDataSources.get(key);
        if (dsObj instanceof DataSource) {
            return ((DataSource) dsObj).getConnection();
        }
        return null;
    }

    public static void closeConsoleConnection(String key) {
        Connection conn = consoleConnections.remove(key);
        // 【新增】移除事务状态
        consoleDirtyStatus.remove(key);

        if (conn != null) {
            try {
                if (!conn.isClosed()) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 【新增】设置事务状态
     */
    public static void setDirty(String key, boolean dirty) {
        consoleDirtyStatus.put(key, dirty);
    }

    /**
     * 【新增】检查是否有未提交事务
     */
    public static boolean isDirty(String key) {
        return consoleDirtyStatus.getOrDefault(key, false);
    }
}