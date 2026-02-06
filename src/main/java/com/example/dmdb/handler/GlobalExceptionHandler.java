package com.example.dmdb.handler;

import com.example.dmdb.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 作用：统一捕获 Controller 抛出的异常，转换为标准 Result 格式返回
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理所有未捕获的通用异常
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("系统异常:", e);

        // 获取最根源的报错信息
        Throwable rootCause = e;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        String msg = rootCause.getMessage();
        if (msg == null) msg = e.toString();

        // 直接返回错误信息，不再添加“请联系管理员”等后缀
        return Result.error(msg);
    }
}