package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.service.base.AbstractDbService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoleServiceImpl extends AbstractDbService {

    public Result<List<Map<String, Object>>> getRoles() {
        try {
            List<Map<String, Object>> allRoles = dbMapper.getRoles();
            Set<String> allowList = new HashSet<>(Arrays.asList("DBA", "PUBLIC", "RESOURCE", "SOI", "SVI", "VTI"));
            List<Map<String, Object>> filteredRoles = allRoles.stream().filter(r -> {
                String roleName = (String) r.get("ROLE_NAME");
                if (roleName == null) return false;
                String upperName = roleName.toUpperCase();
                if (allowList.contains(upperName)) return true;
                if (upperName.startsWith("DB_")) return false;
                if (upperName.startsWith("SYS")) return false;
                if (upperName.equals("AUDITOR") || upperName.equals("SECADMIN") || upperName.equals("OPERATOR")) return false;
                return true;
            }).collect(Collectors.toList());
            return Result.success(filteredRoles);
        } catch (Exception e) {
            return Result.error("获取角色失败: " + e.getMessage());
        }
    }

    public Result<Object> createRole(String roleName) {
        validateIdentifiers(roleName);
        try {
            dbMapper.executeSql("CREATE ROLE \"" + roleName + "\"");
            return Result.success("创建成功");
        } catch (Exception e) {
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    public Result<Object> deleteRole(String roleName) {
        validateIdentifiers(roleName);
        int count = dbMapper.countRoleUsers(roleName);
        if (count > 0) return Result.error("删除失败：关联了" + count + "个用户");
        try {
            dbMapper.executeSql("DROP ROLE \"" + roleName + "\"");
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    public Result<Map<String, Object>> getRoleDetail(String roleName) {
        validateIdentifiers(roleName);
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("rolePrivs", dbMapper.getRoleRolePrivs(roleName));
            data.put("sysPrivs", dbMapper.getRoleSysPrivs(roleName));
            data.put("objPrivs", processResultList(dbMapper.getRoleObjPrivs(roleName)));
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("获取详情失败: " + e.getMessage());
        }
    }

    public Result<Object> updateRoleRolePrivs(String roleName, List<Map<String, Object>> changes) {
        validateIdentifiers(roleName);
        List<String> errorMsgs = new ArrayList<>();
        for (Map<String, Object> change : changes) {
            String targetRole = (String) change.get("role");
            String action = (String) change.get("action");
            boolean admin = Boolean.parseBoolean(String.valueOf(change.get("admin")));
            validateIdentifiers(targetRole);
            String qTarget = quote(targetRole);
            String qRole = quote(roleName);
            if ("REVOKE".equals(action)) {
                executeSqlQuietly("REVOKE " + qTarget + " FROM " + qRole);
            } else if ("GRANT".equals(action)) {
                executeSqlQuietly("REVOKE " + qTarget + " FROM " + qRole);
                String sql = "GRANT " + qTarget + " TO " + qRole;
                if (admin) sql += " WITH ADMIN OPTION";
                try { dbMapper.executeSql(sql); } catch (Exception e) { errorMsgs.add("授予角色[" + targetRole + "]失败: " + e.getMessage()); }
            }
        }
        if (!errorMsgs.isEmpty()) return Result.error("部分角色授权失败:\n" + String.join("\n", errorMsgs));
        return Result.success("更新成功");
    }

    public Result<Object> updateRoleSysPrivs(String roleName, List<Map<String, Object>> changes) {
        validateIdentifiers(roleName);
        List<String> errorMsgs = new ArrayList<>();
        for (Map<String, Object> change : changes) {
            String priv = (String) change.get("priv");
            String action = (String) change.get("action");
            boolean admin = Boolean.parseBoolean(String.valueOf(change.get("admin")));
            if (!priv.matches("^[A-Z_\\s]+$")) continue;
            if ("GRANT ANY PRIVILEGE".equals(priv) || "CREATE ANY INDEX".equals(priv)) {
                log.info("Skipping problematic privilege: {}", priv);
                continue;
            }
            String qRole = quote(roleName);
            String qPriv = quoteSysPriv(priv);
            if ("REVOKE".equals(action)) {
                executeSqlQuietly("REVOKE " + qPriv + " FROM " + qRole);
            } else if ("GRANT".equals(action)) {
                executeSqlQuietly("REVOKE " + qPriv + " FROM " + qRole);
                String sql = "GRANT " + qPriv + " TO " + qRole;
                if (admin) sql += " WITH ADMIN OPTION";
                try { dbMapper.executeSql(sql); } catch (Exception e) {
                    String msg = e.getMessage();
                    if(msg!=null && msg.contains("授权者没有此授权权限")) msg="无权授予权限["+priv+"]";
                    errorMsgs.add(msg);
                }
            }
        }
        if (!errorMsgs.isEmpty()) return Result.error("部分权限更新失败:\n" + String.join("\n", errorMsgs));
        return Result.success("更新成功");
    }

    public Result<Object> updateRoleObjPrivs(String roleName, String schema, String table, List<Map<String, Object>> changes) {
        validateIdentifiers(roleName, schema, table);
        List<String> errorMsgs = new ArrayList<>();
        for (Map<String, Object> change : changes) {
            String priv = (String) change.get("priv");
            String action = (String) change.get("action");
            boolean grantOption = Boolean.parseBoolean(String.valueOf(change.get("admin")));
            if (!priv.matches("^[A-Z_\\s]+$")) continue;
            String qSchema = quote(schema);
            String qTable = quote(table);
            String qRole = quote(roleName);
            String revokeSql = String.format("REVOKE %s ON %s.%s FROM %s CASCADE", priv, qSchema, qTable, qRole);
            if ("REVOKE".equals(action)) { executeSqlQuietly(revokeSql); }
            else if ("GRANT".equals(action)) { executeSqlQuietly(revokeSql); String grantSql = String.format("GRANT %s ON %s.%s TO %s", priv, qSchema, qTable, qRole); if (grantOption) grantSql += " WITH GRANT OPTION"; try { dbMapper.executeSql(grantSql); } catch (Exception e) { errorMsgs.add("对象权限[" + priv + "]更新失败: " + e.getMessage()); } }
        }
        if (!errorMsgs.isEmpty()) return Result.error("部分对象权限更新失败:\n" + String.join("\n", errorMsgs));
        return Result.success("更新成功");
    }
}