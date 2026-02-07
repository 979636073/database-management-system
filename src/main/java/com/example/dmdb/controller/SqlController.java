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

    @Autowired private SqlServiceImpl sqlService;

    @PostMapping("/execute")
    public Result<Object> execute(@RequestBody Map<String, String> payload) { return sqlService.executeSql(payload.get("sql")); }

    @PostMapping("/execute/batch")
    public Result<Object> executeBatch(@RequestBody Map<String, Object> payload) { return sqlService.executeBatchSql((List<String>) payload.get("sqls")); }
}