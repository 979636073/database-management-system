package com.example.dmdb.context; // 建议放在 context 包下

/**
 * 数据库上下文持有者
 * 使用 ThreadLocal 确保 Conn-Id 仅在当前请求线程内有效
 */
public class DbContext {

    private static final ThreadLocal<String> currentConnId = new ThreadLocal<>();

    /**
     * 设置当前线程的连接ID
     */
    public static void setConnId(String connId) {
        currentConnId.set(connId);
    }

    /**
     * 获取当前线程的连接ID
     * (给动态数据源或 Mapper 拦截器使用)
     */
    public static String getConnId() {
        return currentConnId.get();
    }

    /**
     * 清除上下文
     * (非常重要！防止线程复用导致的数据源错乱)
     */
    public static void clear() {
        currentConnId.remove();
    }
}