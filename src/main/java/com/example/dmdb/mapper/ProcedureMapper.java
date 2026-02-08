package com.example.dmdb.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProcedureMapper {
    // 获取指定模式下的存储过程或函数列表
    List<Map<String, Object>> listProcedures(@Param("schema") String schema);

    // 获取存储过程或函数的源代码
    List<String> getProcedureSource(@Param("schema") String schema, @Param("name") String name, @Param("type") String type);
}