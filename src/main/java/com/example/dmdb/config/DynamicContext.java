package com.example.dmdb.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicContext {
    private static final ThreadLocal<String> contextHolder = new ThreadLocal<>();
    // 必须是 ConcurrentHashMap 保证线程安全
    private static final Map<String, String> DB_TYPE_MAP = new ConcurrentHashMap<>();

    public static void setKey(String key) { contextHolder.set(key); }
    public static String getKey() { return contextHolder.get(); }
    public static void clear() { contextHolder.remove(); }

    public static void setDbType(String key, String type) {
        if (key != null && type != null) DB_TYPE_MAP.put(key, type);
    }

    public static void removeDbType(String key) {
        if (key != null) DB_TYPE_MAP.remove(key);
    }

    public static String getCurrentDbType() {
        String key = getKey();
        // 增加空指针保护
        if (key == null) return "DM";
        return DB_TYPE_MAP.getOrDefault(key, "DM");
    }
}