package com.example.dmdb.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Mapper
@Repository
public interface DbMapper {

    // ================== 原有基础接口 (保持不变) ==================

    List<String> getAllSchemas();

    List<Map<String, String>> getTablesBySchema(@Param("schema") String schema);

    List<Map<String, Object>> getColumns(@Param("schema") String schema, @Param("tableName") String tableName);

    List<Map<String, Object>> getViews(@Param("schema") String schema);

    List<Map<String, Object>> getTriggers(@Param("schema") String schema);

    long countData(@Param("schema") String schema, @Param("tableName") String tableName);

    List<Map<String, Object>> getDataPage(@Param("schema") String schema, @Param("tableName") String tableName, @Param("limit") int limit, @Param("offset") int offset);

    Map<String, Object> getDataByRowId(@Param("schema") String schema, @Param("tableName") String tableName, @Param("rowId") String rowId);

    long countByConditions(@Param("schema") String schema, @Param("tableName") String tableName, @Param("conditions") List<Map<String, String>> conditions, @Param("logic") String logic);

    List<Map<String, Object>> queryByConditionsPage(@Param("schema") String schema, @Param("tableName") String tableName, @Param("conditions") List<Map<String, String>> conditions, @Param("logic") String logic, @Param("limit") int limit, @Param("offset") int offset);

    void executeSql(@Param("sql") String sql);

    List<Map<String, Object>> runQuery(@Param("sql") String sql);

    void insertData(@Param("schema") String schema, @Param("tableName") String tableName, @Param("data") Map<String, Object> data);

    void updateByRowId(@Param("schema") String schema, @Param("tableName") String tableName, @Param("rowId") String rowId, @Param("data") Map<String, Object> data);

    void deleteByRowId(@Param("schema") String schema, @Param("tableName") String tableName, @Param("rowId") String rowId);

    Object getDDL(@Param("schema") String schema, @Param("tableName") String tableName, @Param("type") String type);

    List<Map<String, Object>> getTableRelations(@Param("schema") String schema, @Param("tableName") String tableName);

    List<Map<String, Object>> getColumnsForTables(@Param("schema") String schema, @Param("tableList") List<String> tableList);

    List<Map<String, Object>> getTableComments(@Param("schema") String schema, @Param("tableList") List<String> tableList);

    List<Map<String, String>> getAllChildTables(@Param("schema") String schema, @Param("tableName") String tableName);

    void updateTableField(@Param("schema") String schema, @Param("tableName") String tableName, @Param("column") String column, @Param("newVal") String newVal, @Param("oldVal") String oldVal);

    List<Map<String, Object>> getIndexes(@Param("schema") String schema, @Param("tableName") String tableName);

    List<Map<String, Object>> getForeignKeys(@Param("schema") String schema, @Param("tableName") String tableName);

    String getPkColumn(@Param("schema") String schema, @Param("tableName") String tableName);

    int countReference(@Param("schema") String schema, @Param("tableName") String tableName, @Param("columnName") String columnName, @Param("value") Object value);

    // ================== 【新增】角色管理接口 ==================

    /**
     * 获取所有角色列表
     */
    List<Map<String, Object>> getRoles();

    /**
     * 统计该角色下的用户数量（用于删除前检查）
     */
    int countRoleUsers(@Param("roleName") String roleName);

    /**
     * 获取该角色拥有的其他角色权限 (对应前端“常规”Tab)
     * 返回字段: GRANTED_ROLE, ADMIN_OPTION
     */
    List<Map<String, Object>> getRoleRolePrivs(@Param("roleName") String roleName);

    /**
     * 获取该角色拥有的系统权限 (对应前端“系统权限”Tab)
     * 返回字段: PRIVILEGE, ADMIN_OPTION
     */
    List<Map<String, Object>> getRoleSysPrivs(@Param("roleName") String roleName);

    /**
     * 获取该角色拥有的对象权限 (对应前端“对象权限”Tab)
     * 返回字段: OWNER, TABLE_NAME, PRIVILEGE, GRANTABLE
     */
    List<Map<String, Object>> getRoleObjPrivs(@Param("roleName") String roleName);
}