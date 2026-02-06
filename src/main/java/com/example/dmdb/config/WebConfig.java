package com.example.dmdb.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private DbContextInterceptor dbContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 拦截所有 /api/db/ 开头的路径，应用上下文管理
        registry.addInterceptor(dbContextInterceptor)
                .addPathPatterns("/api/db/**");
    }
}