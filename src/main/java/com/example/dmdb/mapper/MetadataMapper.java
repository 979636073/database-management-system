package com.example.dmdb.mapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface MetadataMapper {
    List<String> getAllSchemas();
    List<Map<String, String>> getTablesBySchema(@Param("schema") String schema);
    List<Map<String, Object>> getColumns(@Param("schema") String schema, @Param("tableName") String tableName);
    List<Map<String, Object>> getViews(@Param("schema") String schema);
    List<Map<String, Object>> getTriggers(@Param("schema") String schema);
    Object getDDL(@Param("schema") String schema, @Param("tableName") String tableName, @Param("type") String type);
    List<Map<String, Object>> getIndexes(@Param("schema") String schema, @Param("tableName") String tableName);
    List<Map<String, Object>> getForeignKeys(@Param("schema") String schema, @Param("tableName") String tableName);
    List<Map<String, Object>> getTableRelations(@Param("schema") String schema, @Param("tableName") String tableName);
    List<Map<String, Object>> getColumnsForTables(@Param("schema") String schema, @Param("tableList") List<String> tableList);
    List<Map<String, Object>> getTableComments(@Param("schema") String schema, @Param("tableList") List<String> tableList);
    List<Map<String, String>> getAllChildTables(@Param("schema") String schema, @Param("tableName") String tableName);
    String getPkColumn(@Param("schema") String schema, @Param("tableName") String tableName);
}