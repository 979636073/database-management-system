package com.example.dmdb.handler;

import com.example.dmdb.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public Result<Object> handleException(Exception e) {
        log.error("系统异常:", e);
        String cleanMsg = getRootCauseMessage(e);
        return Result.error(cleanMsg);
    }

    private String getRootCauseMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        String msg = root.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            return "操作失败，请查看后台日志";
        }

        // 1. 去除达梦错误码前缀 (如 [-2501]:)
        msg = msg.replaceAll("^\\[-?\\d+\\]:\\s*", "");

        // 2. 去除类名前缀 (如 dm.jdbc.driver.DMException:)
        if (msg.contains("DMException:")) {
            msg = msg.substring(msg.indexOf("DMException:") + 12);
        }

        // 3. 【核心新增】去除 "第1 行附近出现错误:" 这种描述
        // 匹配 "第" + 数字 + 任意空白 + "行附近出现错误:"
        msg = msg.replaceAll("第\\d+\\s*行附近出现错误:\\s*", "");

        return msg.trim();
    }
}