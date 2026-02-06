package com.example.dmdb.config;

import com.example.dmdb.context.DbContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 数据库上下文拦截器
 * 作用：自动提取请求头中的 Conn-Id，并在请求结束后自动清理，防止内存泄漏
 */
@Component
public class DbContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 获取请求头中的 Conn-Id
        String connId = request.getHeader("Conn-Id");

        // 2. 如果存在，设置到 ThreadLocal
        if (connId != null && !connId.isEmpty()) {
            DbContext.setConnId(connId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 3. 请求结束（无论成功失败），强制清理 ThreadLocal
        DbContext.clear();
    }
}