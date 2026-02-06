package com.example.dmdb.service;

import com.example.dmdb.config.DynamicContext;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectionManager extends AbstractRoutingDataSource {
    // 缓存所有活跃的数据源
    private static final Map<Object, Object> targetDataSources = new ConcurrentHashMap<>();

    @Override
    protected Object determineCurrentLookupKey() {
        return DynamicContext.getKey();
    }

    @Override
    public void afterPropertiesSet() {
        // 必须设置一个默认数据源，防止启动报错
        // 实际项目中这里可以配置 application.yml 里的主数据源
        super.setTargetDataSources(targetDataSources);
        super.afterPropertiesSet();
    }

    /**
     * 动态添加数据源 (带连接校验)
     */
    public void addDataSource(String key, String host, String port, String user, String password) throws SQLException {
        // 如果为了更新配置（比如输错密码重试），建议先移除旧的
        if (targetDataSources.containsKey(key)) {
            removeDataSource(key);
        }

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:dm://" + host + ":" + port); // 达梦JDBC URL格式
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setDriverClassName("dm.jdbc.driver.DmDriver");

        // 优化连接池配置
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        ds.setConnectionTimeout(5000); // 【重要】设置超时5秒，避免连不上时卡死

        // 【核心修改】尝试获取连接，验证配置是否正确
        try (Connection conn = ds.getConnection()) {
            // 连接成功，立即关闭连接（HikariCP 会将其放回池中或按策略处理）
        } catch (SQLException e) {
            ds.close(); // 验证失败，必须关闭数据源，释放资源
            throw e; // 抛出异常，让 Controller 捕获并返回错误信息给前端
        }

        targetDataSources.put(key, ds);
        super.setTargetDataSources(targetDataSources); // 刷新映射
        super.afterPropertiesSet(); // 重新初始化
    }

    /**
     * 移除数据源
     */
    public void removeDataSource(String key) {
        Object ds = targetDataSources.remove(key);
        // 尝试关闭连接池
        if (ds instanceof Closeable) {
            try { ((Closeable) ds).close(); } catch (IOException e) { e.printStackTrace(); }
        } else if (ds instanceof HikariDataSource) {
            ((HikariDataSource) ds).close();
        }

        super.setTargetDataSources(targetDataSources);
        super.afterPropertiesSet();
    }

    /**
     * 【新增】检查是否存在
     */
    public boolean hasDataSource(String key) {
        return targetDataSources.containsKey(key);
    }
}