package com.example.dmdb.mapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface RoleMapper {
    List<Map<String, Object>> getRoles();
    int countRoleUsers(@Param("roleName") String roleName);
    List<Map<String, Object>> getRoleRolePrivs(@Param("roleName") String roleName);
    List<Map<String, Object>> getRoleSysPrivs(@Param("roleName") String roleName);
    List<Map<String, Object>> getRoleObjPrivs(@Param("roleName") String roleName);
}