package com.example.dmdb.config;

import com.example.dmdb.service.ConnectionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {
    @Bean
    @Primary // 标记为主数据源，覆盖默认配置
    public DataSource dataSource(ConnectionManager connectionManager) {
        return connectionManager;
    }
}