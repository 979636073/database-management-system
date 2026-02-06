package com.example.dmdb.config;

import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DataSourceInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 从 Header 中获取连接ID
        String connId = request.getHeader("Conn-Id");

        // 2. 如果存在，设置到当前线程上下文
        // (这样在进入 Controller 的 @Transactional 之前，数据源就已经定好了)
        if (connId != null && !connId.isEmpty()) {
            DynamicContext.setKey(connId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 3. 请求结束后清理上下文，防止内存泄漏
        DynamicContext.clear();
    }
}