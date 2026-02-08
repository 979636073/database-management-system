package com.example.dmdb.service;
import com.example.dmdb.common.Result;
import java.util.Map;

public interface ProcedureService {
    Result<Object> list(String schema);
    Result<Object> getDetail(String schema, String name, String type);
    Result<Object> compile(String sql); // 编译/保存使用通用的 SQL 执行
    Result<Object> delete(String schema, String name, String type);
}