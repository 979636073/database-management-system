package com.example.dmdb.controller;

import com.example.dmdb.common.Result;
import com.example.dmdb.service.impl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/db")
@CrossOrigin
public class DbController {

    @Autowired
    private MetadataServiceImpl metadataService;
    @Autowired
    private TableDataServiceImpl tableDataService;
    @Autowired
    private RoleServiceImpl roleService;
    @Autowired
    private SqlServiceImpl sqlService;

    // 1. 元数据
    @GetMapping("/schemas")
    public Result<List<String>> getSchemas() {
        return metadataService.getSchemas();
    }

    @GetMapping("/tables")
    public Result<List<Map<String, String>>> getTables(@RequestParam String schema) {
        return metadataService.getTables(schema);
    }

    @GetMapping("/columns")
    public Result<List<Map<String, Object>>> getColumns(@RequestParam String schema, @RequestParam String tableName) {
        return metadataService.getColumns(schema, tableName);
    }

    @GetMapping("/views")
    public Result<List<Map<String, Object>>> getViews(@RequestParam String schema) {
        return metadataService.getViews(schema);
    }

    @GetMapping("/triggers")
    public Result<List<Map<String, Object>>> getTriggers(@RequestParam String schema) {
        return metadataService.getTriggers(schema);
    }

    @GetMapping("/ddl")
    public Result<String> getDDL(@RequestParam String schema, @RequestParam String tableName) {
        return metadataService.getTableDDL(schema, tableName);
    }

    @GetMapping("/indexes")
    public Result<List<Map<String, Object>>> getIndexes(@RequestParam String schema, @RequestParam String tableName) {
        return metadataService.getIndexes(schema, tableName);
    }

    @GetMapping("/foreign-keys")
    public Result<List<Map<String, Object>>> getForeignKeys(@RequestParam String schema, @RequestParam String tableName) {
        return metadataService.getForeignKeys(schema, tableName);
    }

    @GetMapping("/er-data")
    public Result<Map<String, Object>> getErData(@RequestParam String schema, @RequestParam String tableName, @RequestParam(defaultValue = "false") boolean showAll, @RequestParam(required = false) List<String> expandedTables) {
        return metadataService.getErData(schema, tableName, showAll, expandedTables);
    }

    // 2. 数据操作
    @GetMapping("/data")
    public Result<Map<String, Object>> getData(@RequestParam String schema, @RequestParam String tableName, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int size) {
        return tableDataService.getData(schema, tableName, page, size);
    }

    @PostMapping("/filter")
    public Result<Map<String, Object>> filter(@RequestBody Map<String, Object> payload) {
        return tableDataService.filterData(payload);
    }

    @PostMapping("/save")
    public Result<Object> save(@RequestParam String schema, @RequestParam String tableName, @RequestBody Map<String, Object> row) {
        return tableDataService.saveData(schema, tableName, row);
    }

    @PostMapping("/save/batch")
    public Result<Object> saveBatch(@RequestParam String schema, @RequestParam String tableName, @RequestBody Map<String, List<Map<String, Object>>> payload) {
        return tableDataService.saveBatch(schema, tableName, payload);
    }

    @DeleteMapping("/delete")
    public Result<Object> delete(@RequestParam String schema, @RequestParam String tableName, @RequestParam Object internalId, @RequestParam(required = false) String pkValue) {
        return tableDataService.deleteData(schema, tableName, internalId, pkValue);
    }

    @PostMapping("/delete/batch")
    public Result<Object> deleteBatch(@RequestParam String schema, @RequestParam String tableName, @RequestBody List<String> rowIds) {
        return tableDataService.deleteBatch(schema, tableName, rowIds);
    }

    @PostMapping("/save/cascade")
    public Result<String> saveCascade(@RequestParam String schema, @RequestParam String tableName, @RequestBody Map<String, Object> payload) {
        return Result.error("暂不支持");
    }

    // 3. SQL
    @PostMapping("/execute")
    public Result<Object> execute(@RequestBody Map<String, String> payload) {
        return sqlService.executeSql(payload.get("sql"));
    }

    @PostMapping("/execute/batch")
    public Result<Object> executeBatch(@RequestBody Map<String, Object> payload) {
        return sqlService.executeBatchSql((List<String>) payload.get("sqls"));
    }

    // 4. 角色
    @GetMapping("/roles")
    public Result<List<Map<String, Object>>> getRoles() {
        return roleService.getRoles();
    }

    @PostMapping("/role/create")
    public Result<Object> createRole(@RequestBody Map<String, String> payload) {
        return roleService.createRole(payload.get("roleName"));
    }

    @DeleteMapping("/role/delete")
    public Result<Object> deleteRole(@RequestParam String roleName) {
        return roleService.deleteRole(roleName);
    }

    @GetMapping("/role/detail")
    public Result<Map<String, Object>> getRoleDetail(@RequestParam String roleName) {
        return roleService.getRoleDetail(roleName);
    }

    @PostMapping("/role/role-privs")
    public Result<Object> updateRoleRolePrivs(@RequestBody Map<String, Object> payload) {
        return roleService.updateRoleRolePrivs((String) payload.get("roleName"), (List<Map<String, Object>>) payload.get("changes"));
    }

    @PostMapping("/role/sys-privs")
    public Result<Object> updateRoleSysPrivs(@RequestBody Map<String, Object> payload) {
        return roleService.updateRoleSysPrivs((String) payload.get("roleName"), (List<Map<String, Object>>) payload.get("changes"));
    }

    @PostMapping("/role/obj-privs")
    public Result<Object> updateRoleObjPrivs(@RequestBody Map<String, Object> payload) {
        return roleService.updateRoleObjPrivs((String) payload.get("roleName"), (String) payload.get("schema"), (String) payload.get("table"), (List<Map<String, Object>>) payload.get("changes"));
    }
}