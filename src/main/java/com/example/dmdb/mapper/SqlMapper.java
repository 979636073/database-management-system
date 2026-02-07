package com.example.dmdb.mapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface SqlMapper {
    void executeSql(@Param("sql") String sql);
    List<Map<String, Object>> runQuery(@Param("sql") String sql);
}