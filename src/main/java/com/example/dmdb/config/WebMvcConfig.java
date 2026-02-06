package com.example.dmdb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 【核心修复】使用 CorsFilter Bean 替代 addCorsMappings。
     * Filter 的优先级高于 Interceptor，确保在进入拦截器之前就已经处理好跨域握手。
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 1. 允许所有来源 (SpringBoot 2.4+ 推荐使用 addAllowedOriginPattern)
        config.addAllowedOriginPattern("*");
        // 2. 允许所有请求头
        config.addAllowedHeader("*");
        // 3. 允许所有方法 (GET, POST, PUT, DELETE, OPTIONS)
        config.addAllowedMethod("*");
        // 4. 允许携带凭证 (Cookie 等)
        config.setAllowCredentials(true);
        // 5. 预检请求缓存时间 (秒)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册数据源拦截器
        registry.addInterceptor(new DataSourceInterceptor())
                .addPathPatterns("/api/db/**") // 拦截数据操作接口
                .addPathPatterns("/api/connection/update") // 拦截连接更新
                .addPathPatterns("/api/connection/delete"); // 拦截连接删除
    }
}