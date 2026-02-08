package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.mapper.ProcedureMapper;
import com.example.dmdb.mapper.SqlMapper;
import com.example.dmdb.service.ProcedureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProcedureServiceImpl implements ProcedureService {

    @Autowired
    private ProcedureMapper procedureMapper;

    @Autowired
    private SqlMapper sqlMapper;

    @Override
    public Result<Object> list(String schema) {
        return Result.success(procedureMapper.listProcedures(schema));
    }

    @Override
    public Result<Object> getDetail(String schema, String name, String type) {
        // ALL_SOURCE 返回的是多行文本，需要拼接
        List<String> lines = procedureMapper.getProcedureSource(schema, name, type);
        String source = String.join("", lines);

        // 如果无法通过 ALL_SOURCE 获取（权限问题），可以尝试 DBMS_METADATA.GET_DDL (暂略)

        Map<String, String> data = new HashMap<>();
        data.put("source", source);
        data.put("schema", schema);
        data.put("name", name);
        data.put("type", type);
        return Result.success(data);
    }

    @Override
    public Result<Object> compile(String sql) {
        // 创建或替换存储过程实际上就是执行 SQL
        try {
            sqlMapper.executeSql(sql);
            return Result.success("编译成功");
        } catch (Exception e) {
            return Result.error(500, "编译失败: " + e.getCause().getMessage());
        }
    }

    @Override
    public Result<Object> delete(String schema, String name, String type) {
        try {
            String sql = String.format("DROP %s \"%s\".\"%s\"", type, schema, name);
            sqlMapper.executeSql(sql);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }
}