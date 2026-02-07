package com.example.dmdb.mapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface TableDataMapper {
    long countData(@Param("schema") String schema, @Param("tableName") String tableName);
    List<Map<String, Object>> getDataPage(@Param("schema") String schema, @Param("tableName") String tableName, @Param("limit") int limit, @Param("offset") int offset);
    Map<String, Object> getDataByRowId(@Param("schema") String schema, @Param("tableName") String tableName, @Param("rowId") String rowId);
    long countByConditions(@Param("schema") String schema, @Param("tableName") String tableName, @Param("conditions") List<Map<String, String>> conditions, @Param("logic") String logic);
    List<Map<String, Object>> queryByConditionsPage(@Param("schema") String schema, @Param("tableName") String tableName, @Param("conditions") List<Map<String, String>> conditions, @Param("logic") String logic, @Param("limit") int limit, @Param("offset") int offset);
    void insertData(@Param("schema") String schema, @Param("tableName") String tableName, @Param("data") Map<String, Object> data);
    void updateByRowId(@Param("schema") String schema, @Param("tableName") String tableName, @Param("rowId") String rowId, @Param("data") Map<String, Object> data);
    void deleteByRowId(@Param("schema") String schema, @Param("tableName") String tableName, @Param("rowId") String rowId);
    int countReference(@Param("schema") String schema, @Param("tableName") String tableName, @Param("columnName") String columnName, @Param("value") Object value);
}