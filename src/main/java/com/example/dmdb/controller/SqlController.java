package com.example.dmdb.controller;

import com.example.dmdb.common.Result;
import com.example.dmdb.service.impl.SqlServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/db")
@CrossOrigin
public class SqlController {

    @Autowired
    private SqlServiceImpl sqlService;

    /**
     * 执行单条 SQL
     * 前端参数: { "sql": "SELECT...", "manualCommit": true/false }
     */
    @PostMapping("/execute")
    public Result<Object> execute(@RequestBody Map<String, Object> payload) {
        String sql = (String) payload.get("sql");

        // 获取前端传递的事务标志
        // 如果 manualCommit 为 true，则 Service 层会开启手动事务模式（不自动提交）
        // 默认为 false (即默认自动提交，兼容旧代码和系统其他功能)
        boolean manualCommit = false;
        if (payload.get("manualCommit") != null) {
            manualCommit = Boolean.parseBoolean(String.valueOf(payload.get("manualCommit")));
        }

        return sqlService.executeSql(sql, manualCommit);
    }

    /**
     * 【新增】执行多条 SQL (脚本模式)
     */
    @PostMapping("/execute/script")
    public Result<Object> executeScript(@RequestBody Map<String, Object> payload) {
        List<String> sqls = (List<String>) payload.get("sqls");
        boolean manualCommit = false;
        if (payload.get("manualCommit") != null) {
            manualCommit = Boolean.parseBoolean(String.valueOf(payload.get("manualCommit")));
        }
        return sqlService.executeScript(sqls, manualCommit);
    }

    /**
     * 批量执行 SQL
     * 前端参数: { "sqls": ["INSERT...", "UPDATE..."] }
     */
    @PostMapping("/execute/batch")
    public Result<Object> executeBatch(@RequestBody Map<String, Object> payload) {
        List<String> sqls = (List<String>) payload.get("sqls");
        return sqlService.executeBatchSql(sqls);
    }

    /**
     * 【新增】获取当前事务是否未提交 (Dirty Check)
     */
    @GetMapping("/transaction/status")
    public Result<Boolean> getStatus() {
        return sqlService.getTransactionStatus();
    }
}