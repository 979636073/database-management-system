package com.example.dmdb.config;

import com.example.dmdb.service.ConnectionManager;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@MapperScan("com.example.dmdb.mapper")
public class DataSourceConfig {
    @Bean
    @Primary // 标记为主数据源，覆盖默认配置
    public DataSource dataSource(ConnectionManager connectionManager) {
        return connectionManager;
    }

    // 【新增】配置 DatabaseIdProvider，用于在 XML 中区分数据库
//    @Bean
//    public DatabaseIdProvider databaseIdProvider() {
//        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
//        Properties props = new Properties();
//        props.setProperty("DM DBMS", "dm");      // 达梦识别为 dm
//        props.setProperty("Oracle", "oracle");   // Oracle 识别为 oracle
//        provider.setProperties(props);
//        return provider;
//    }
}