package com.example.dmdb.config;

/**
 * 动态数据源上下文
 * 用于存放当前请求对应的连接ID (Conn-Id)
 */
public class DynamicContext {
    // 使用 ThreadLocal 保证线程安全，存放当前线程的数据库连接ID
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    /**
     * 设置当前线程的数据库连接ID
     */
    public static void setKey(String key) {
        CONTEXT.set(key);
    }

    /**
     * 获取当前线程的数据库连接ID
     */
    public static String getKey() {
        return CONTEXT.get();
    }

    /**
     * 清除当前线程的数据库连接ID
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 【新增】获取当前连接ID (Alias for getKey)
     * 供 SqlServiceImpl 调用，语义更清晰
     */
    public static String getConnId() {
        return getKey();
    }
}