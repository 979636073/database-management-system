package com.example.dmdb.mapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface SqlMapper {
    // [修改] 返回 int，表示受影响的行数
    int executeSql(@Param("sql") String sql);

    List<Map<String, Object>> runQuery(@Param("sql") String sql);
}