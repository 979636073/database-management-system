package com.example.dmdb.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface TablespaceMapper {
    // 获取所有表空间列表
    List<Map<String, Object>> listTablespaces();

    // 获取指定表空间的数据文件列表
    List<Map<String, Object>> listDatafiles(@Param("tablespaceName") String tablespaceName);

    // [新增] 获取数据库页大小
    Long getPageSize();
}