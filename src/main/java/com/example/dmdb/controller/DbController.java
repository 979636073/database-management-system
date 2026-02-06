package com.example.dmdb.controller;

import com.example.dmdb.common.Result;
import com.example.dmdb.service.DbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/db")
@CrossOrigin
public class DbController {

    @Autowired
    private DbService dbService;

    // ==========================================
    // 1. 基础元数据
    // ==========================================

    @GetMapping("/schemas")
    public Result<List<String>> getSchemas() {
        return dbService.getSchemas();
    }

    @GetMapping("/tables")
    public Result<List<Map<String, String>>> getTables(@RequestParam String schema) {
        return dbService.getTables(schema);
    }

    @GetMapping("/columns")
    public Result<List<Map<String, Object>>> getColumns(@RequestParam String schema, @RequestParam String tableName) {
        return dbService.getColumns(schema, tableName);
    }

    @GetMapping("/views")
    public Result<List<Map<String, Object>>> getViews(@RequestParam String schema) {
        return dbService.getViews(schema);
    }

    @GetMapping("/triggers")
    public Result<List<Map<String, Object>>> getTriggers(@RequestParam String schema) {
        return dbService.getTriggers(schema);
    }

    // ==========================================
    // 2. 数据操作
    // ==========================================

    @GetMapping("/data")
    public Result<Map<String, Object>> getData(@RequestParam String schema,
                                               @RequestParam String tableName,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return dbService.getData(schema, tableName, page, size);
    }

    @PostMapping("/filter")
    public Result<Map<String, Object>> filter(@RequestBody Map<String, Object> payload) {
        return dbService.filterData(payload);
    }

    @PostMapping("/execute")
    public Result<Object> execute(@RequestBody Map<String, String> payload) {
        String sql = payload.get("sql");
        if (sql == null || sql.trim().isEmpty()) {
            throw new RuntimeException("SQL 不能为空");
        }
        return dbService.executeSql(sql);
    }

    // ==========================================
    // 3. 业务变更 (逻辑已下沉)
    // ==========================================

    @PostMapping("/save")
    public Result<Object> save(@RequestParam String schema, @RequestParam String tableName, @RequestBody Map<String, Object> row) {
        return dbService.saveData(schema, tableName, row);
    }

    // 【新增】批量保存接口，解决 404 错误
    @PostMapping("/save/batch")
    public Result<Object> saveBatch(@RequestParam String schema, @RequestParam String tableName, @RequestBody Map<String, List<Map<String, Object>>> payload) {
        return dbService.saveBatch(schema, tableName, payload);
    }


    @PostMapping("/save/cascade")
    public Result<String> saveCascade(@RequestParam String schema, @RequestParam String tableName, @RequestBody Map<String, Object> payload) {
        return dbService.saveCascade(schema, tableName, payload);
    }

    @DeleteMapping("/delete")
    public Result<Object> delete(@RequestParam String schema,
                                 @RequestParam String tableName,
                                 @RequestParam Object internalId,
                                 @RequestParam(required = false) String pkValue) {
        return dbService.deleteData(schema, tableName, internalId, pkValue);
    }


    // 【新增】批量删除接口
    @PostMapping("/delete/batch")
    public Result<Object> deleteBatch(@RequestParam String schema,
                                      @RequestParam String tableName,
                                      @RequestBody List<String> rowIds) {
        return dbService.deleteBatch(schema, tableName, rowIds);
    }

    @GetMapping("/ddl")
    public Result<String> getDDL(@RequestParam String schema, @RequestParam String tableName) {
        return dbService.getTableDDL(schema, tableName);
    }

    @PostMapping("/execute/batch")
    public Result<Object> executeBatch(@RequestBody Map<String, Object> payload) {
        List<String> sqlList = (List<String>) payload.get("sqls");
        return dbService.executeBatchSql(sqlList);
    }

    // 【新增】获取索引
    @GetMapping("/indexes")
    public Result<List<Map<String, Object>>> getIndexes(@RequestParam String schema, @RequestParam String tableName) {
        return dbService.getIndexes(schema, tableName);
    }

    // 【新增】获取外键
    @GetMapping("/foreign-keys")
    public Result<List<Map<String, Object>>> getForeignKeys(@RequestParam String schema, @RequestParam String tableName) {
        return dbService.getForeignKeys(schema, tableName);
    }

    @GetMapping("/er-data")
    public Result<Map<String, Object>> getErData(
            @RequestParam String schema,
            @RequestParam String tableName,
            @RequestParam(defaultValue = "false") boolean showAll,
            @RequestParam(required = false) List<String> expandedTables // 【新增】
    ) {
        return dbService.getErData(schema, tableName, showAll, expandedTables);
    }

    // ================== 【新增】角色 API ==================
    @GetMapping("/roles")
    public Result<List<Map<String, Object>>> getRoles() {
        return dbService.getRoles();
    }

    @PostMapping("/role/create")
    public Result<Object> createRole(@RequestBody Map<String, String> payload) {
        return dbService.createRole(payload.get("roleName"));
    }

    @DeleteMapping("/role/delete")
    public Result<Object> deleteRole(@RequestParam String roleName) {
        return dbService.deleteRole(roleName);
    }

    @GetMapping("/role/detail")
    public Result<Map<String, Object>> getRoleDetail(@RequestParam String roleName) {
        return dbService.getRoleDetail(roleName);
    }

    @PostMapping("/role/role-privs")
    public Result<Object> updateRoleRolePrivs(@RequestBody Map<String, Object> payload) {
        String roleName = (String) payload.get("roleName");
        List<Map<String, Object>> changes = (List<Map<String, Object>>) payload.get("changes");
        return dbService.updateRoleRolePrivs(roleName, changes);
    }

    @PostMapping("/role/sys-privs")
    public Result<Object> updateRoleSysPrivs(@RequestBody Map<String, Object> payload) {
        String roleName = (String) payload.get("roleName");
        List<Map<String, Object>> changes = (List<Map<String, Object>>) payload.get("changes");
        return dbService.updateRoleSysPrivs(roleName, changes);
    }

    @PostMapping("/role/obj-privs")
    public Result<Object> updateRoleObjPrivs(@RequestBody Map<String, Object> payload) {
        String roleName = (String) payload.get("roleName");
        String schema = (String) payload.get("schema");
        String table = (String) payload.get("table");
        List<Map<String, Object>> changes = (List<Map<String, Object>>) payload.get("changes");
        return dbService.updateRoleObjPrivs(roleName, schema, table, changes);
    }
}