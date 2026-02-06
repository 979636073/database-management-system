package com.example.dmdb.service;

import com.example.dmdb.common.Result;
import java.util.List;
import java.util.Map;

public interface DbService {

    // ================== 基础元数据查询 ==================
    Result<List<String>> getSchemas();

    Result<List<Map<String, String>>> getTables(String schema);

    Result<List<Map<String, Object>>> getColumns(String schema, String tableName);

    Result<List<Map<String, Object>>> getViews(String schema);

    Result<List<Map<String, Object>>> getTriggers(String schema);

    // ================== 数据操作与查询 ==================
    Result<Map<String, Object>> getData(String schema, String tableName, int page, int size);

    Result<Map<String, Object>> filterData(Map<String, Object> payload);

    Result<Object> executeSql(String sql);

    Result<Object> executeBatchSql(List<String> sqlList);

    /**
     * 单条数据保存（新增或更新）
     */
    Result<Object> saveData(String schema, String tableName, Map<String, Object> row);

    /**
     * 【新增】批量保存接口（事务原子性）
     * @param payload 包含 insertList 和 updateList 的 map
     */
    Result<Object> saveBatch(String schema, String tableName, Map<String, List<Map<String, Object>>> payload);

    /**
     * 删除数据
     */
    Result<Object> deleteData(String schema, String tableName, Object internalId, String pkValue);

    // 【新增】批量删除接口
    Result<Object> deleteBatch(String schema, String tableName, List<String> rowIds);

    Result<String> saveCascade(String schema, String tableName, Map<String, Object> payload);

    // ================== 结构分析与DDL ==================
    Result<Map<String, Object>> getErData(String schema, String tableName, boolean showAll, List<String> expandedTables);

    Result<String> getTableDDL(String schema, String tableName);

    Result<List<Map<String, Object>>> getIndexes(String schema, String tableName);

    Result<List<Map<String, Object>>> getForeignKeys(String schema, String tableName);

    // ================== 角色管理接口 ==================

    /**
     * 获取所有角色列表
     */
    Result<List<Map<String, Object>>> getRoles();

    /**
     * 创建新角色
     */
    Result<Object> createRole(String roleName);

    /**
     * 删除角色
     */
    Result<Object> deleteRole(String roleName);

    /**
     * 获取角色详情（包含常规角色权限、系统权限、对象权限）
     */
    Result<Map<String, Object>> getRoleDetail(String roleName);

    /**
     * 更新角色的角色权限（常规 Tab）
     */
    Result<Object> updateRoleRolePrivs(String roleName, List<Map<String, Object>> changes);

    /**
     * 更新角色的系统权限
     */
    Result<Object> updateRoleSysPrivs(String roleName, List<Map<String, Object>> changes);

    /**
     * 更新角色的对象权限
     */
    Result<Object> updateRoleObjPrivs(String roleName, String schema, String table, List<Map<String, Object>> changes);
}