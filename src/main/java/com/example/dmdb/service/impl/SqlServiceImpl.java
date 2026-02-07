package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.service.base.AbstractDbService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SqlServiceImpl extends AbstractDbService {

    public Result<Object> executeSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) return Result.error("SQL不能为空");
        if (sql.trim().toUpperCase().startsWith("SELECT") || sql.trim().toUpperCase().startsWith("WITH")) {
            return Result.success(processResultList(dbMapper.runQuery(sql)));
        }
        dbMapper.executeSql(sql);
        return Result.success("执行成功");
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Object> executeBatchSql(List<String> sqlList) {
        try {
            for (String sql : sqlList) {
                if (sql != null && !sql.trim().isEmpty()) dbMapper.executeSql(sql);
            }
            return Result.success("执行成功");
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}