package com.example.dmdb.common;

import lombok.Data;

@Data
public class Result<T> {
    private int code;      // 状态码：200成功，500或其他失败
    private String msg;    // 提示信息
    private T data;        // 实际数据

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("操作成功");
        result.setData(data);
        return result;
    }

    // 现有的单参数方法（保持兼容）
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMsg(message);
        return result;
    }

    // [新增] 双参数方法，支持自定义状态码
    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(message);
        return result;
    }
}