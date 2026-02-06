package com.example.dmdb.config;

public class DynamicContext {
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void setKey(String key) { CONTEXT.set(key); }
    public static String getKey() { return CONTEXT.get(); }
    public static void clear() { CONTEXT.remove(); }
}