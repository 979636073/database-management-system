package com.example.dmdb.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {
    // 获取用户列表
    List<Map<String, Object>> getUsers();

    // 获取表空间列表
    List<String> getTablespaces();

    // 获取系统权限
    List<Map<String, Object>> getUserSysPrivs(@Param("username") String username);

    // 获取对象权限
    List<Map<String, Object>> getUserObjPrivs(@Param("username") String username);

    // 【新增】获取用户拥有的角色
    List<Map<String, Object>> getUserRoles(@Param("username") String username);

    // 【新增】获取系统所有可用角色
    List<String> getAllRoles();

    // 获取所有系统权限名
    List<String> getAllSysPrivileges();
}