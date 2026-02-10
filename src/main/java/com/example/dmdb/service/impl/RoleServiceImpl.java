package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.config.DynamicContext;
import com.example.dmdb.mapper.RoleMapper;
import com.example.dmdb.service.base.AbstractDbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoleServiceImpl extends AbstractDbService {

    private static final Logger log = LoggerFactory.getLogger(RoleServiceImpl.class);

    @Autowired
    private RoleMapper roleMapper;

    // 【新增】Oracle 不支持的达梦(DM)特有对象权限列表 (黑名单)
    private static final Set<String> DM_SPECIFIC_OBJ_PRIVS = new HashSet<>(Arrays.asList(
            "SELECT FOR DUMP", // 达梦特有的导出权限，Oracle 11g 遇到会报 ORA-00969
            "RESTORE"          // 达梦特有的还原权限
    ));

    public Result<List<Map<String, Object>>> getRoles() {
        try {
            List<Map<String, Object>> allRoles = roleMapper.getRoles();
            Set<String> allowList = new HashSet<>(Arrays.asList("DBA", "PUBLIC", "RESOURCE", "SOI", "SVI", "VTI"));
            List<Map<String, Object>> filteredRoles = allRoles.stream().filter(r -> {
                String roleName = (String) r.get("ROLE_NAME");
                if (roleName == null) return false;
                String upperName = roleName.toUpperCase();
                if (allowList.contains(upperName)) return true;
                if (upperName.startsWith("DB_")) return false;
                if (upperName.startsWith("SYS")) return false;
                if (upperName.equals("AUDITOR") || upperName.equals("SECADMIN") || upperName.equals("OPERATOR"))
                    return false;
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
            sqlMapper.executeSql("CREATE ROLE \"" + roleName + "\"");
            return Result.success("创建成功");
        } catch (Exception e) {
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    public Result<Object> deleteRole(String roleName) {
        validateIdentifiers(roleName);
        int count = roleMapper.countRoleUsers(roleName);
        if (count > 0) return Result.error("删除失败：关联了" + count + "个用户");
        try {
            sqlMapper.executeSql("DROP ROLE \"" + roleName + "\"");
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    public Result<Map<String, Object>> getRoleDetail(String roleName) {
        validateIdentifiers(roleName);
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("rolePrivs", roleMapper.getRoleRolePrivs(roleName));
            data.put("sysPrivs", roleMapper.getRoleSysPrivs(roleName));
            data.put("objPrivs", processResultList(roleMapper.getRoleObjPrivs(roleName)));
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
                try {
                    sqlMapper.executeSql(sql);
                } catch (Exception e) {
                    errorMsgs.add("授予角色[" + targetRole + "]失败: " + e.getMessage());
                }
            }
        }
        if (!errorMsgs.isEmpty()) return Result.error("部分角色授权失败:\n" + String.join("\n", errorMsgs));
        return Result.success("更新成功");
    }

    public Result<Object> updateRoleSysPrivs(String roleName, List<Map<String, Object>> changes) {
        validateIdentifiers(roleName);
        List<String> errorMsgs = new ArrayList<>();

        // 判断当前数据库类型
        boolean isOracle = "ORACLE".equalsIgnoreCase(DynamicContext.getCurrentDbType());

        for (Map<String, Object> change : changes) {
            String priv = (String) change.get("priv");
            String action = (String) change.get("action");
            boolean admin = Boolean.parseBoolean(String.valueOf(change.get("admin")));
            if (!priv.matches("^[A-Z_\\s]+$")) continue;

            // 跳过已知的危险或不支持的权限
            if ("GRANT ANY PRIVILEGE".equals(priv) || "CREATE ANY INDEX".equals(priv)) {
                continue;
            }

            // 【新增】如果是 Oracle，额外过滤掉只有达梦才有的特定系统权限
            // 这里可以根据后续测试补充，目前暂不需要额外过滤，因为 DBA_SYS_PRIVS 取出来的一般都是兼容的

            String qRole = quote(roleName);
            String qPriv = quoteSysPriv(priv);
            if ("REVOKE".equals(action)) {
                executeSqlQuietly("REVOKE " + qPriv + " FROM " + qRole);
            } else if ("GRANT".equals(action)) {
                executeSqlQuietly("REVOKE " + qPriv + " FROM " + qRole);
                String sql = "GRANT " + qPriv + " TO " + qRole;
                if (admin) sql += " WITH ADMIN OPTION";
                try {
                    sqlMapper.executeSql(sql);
                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("授权者没有此授权权限")) msg = "无权授予权限[" + priv + "]";
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

        // 1. 判断当前数据库类型
        boolean isOracle = "ORACLE".equalsIgnoreCase(DynamicContext.getCurrentDbType());

        // 【新增】定义 Oracle 角色不支持的权限列表
        // 原因: ORA-01931 cannot grant INDEX/REFERENCES to a role
        Set<String> oracleRoleUnsupported = new HashSet<>(Arrays.asList("INDEX", "REFERENCES"));

        for (Map<String, Object> change : changes) {
            String priv = (String) change.get("priv");
            String action = (String) change.get("action");
            boolean grantOption = Boolean.parseBoolean(String.valueOf(change.get("admin")));

            if (!priv.matches("^[A-Z_\\s]+$")) continue;

            // 【核心修复 1】兼容性检查：如果当前是 Oracle
            // 1. 过滤达梦特有权限 (SELECT FOR DUMP)
            // 2. 过滤 Oracle 角色不支持的权限 (INDEX, REFERENCES)
            if (isOracle) {
                if (DM_SPECIFIC_OBJ_PRIVS.contains(priv)) {
                    log.warn("Oracle 环境下自动忽略达梦特有权限: {}", priv);
                    continue;
                }
                if (oracleRoleUnsupported.contains(priv)) {
                    log.warn("Oracle 不支持将权限 [{}] 授予给角色，已自动忽略", priv);
                    continue;
                }
            }

            String qSchema = quote(schema);
            String qTable = quote(table);
            String qRole = quote(roleName);

            // 【核心修复 2】语法适配：REVOKE 语句
            // Oracle: REVOKE SELECT ON T FROM R (不支持 CASCADE)
            // DM: REVOKE SELECT ON T FROM R CASCADE (支持且通常建议)
            String revokeSql;
            if (isOracle) {
                // Oracle 语法：不带 CASCADE
                revokeSql = String.format("REVOKE %s ON %s.%s FROM %s", priv, qSchema, qTable, qRole);
            } else {
                // 达梦/其他 语法：带 CASCADE
                revokeSql = String.format("REVOKE %s ON %s.%s FROM %s CASCADE", priv, qSchema, qTable, qRole);
            }

            if ("REVOKE".equals(action)) {
                executeSqlQuietly(revokeSql);
            } else if ("GRANT".equals(action)) {
                // 授权前先尝试回收，保持幂等
                executeSqlQuietly(revokeSql);

                String grantSql = String.format("GRANT %s ON %s.%s TO %s", priv, qSchema, qTable, qRole);
                if (grantOption) grantSql += " WITH GRANT OPTION";
                try {
                    sqlMapper.executeSql(grantSql);
                } catch (Exception e) {
                    errorMsgs.add("对象权限[" + priv + "]更新失败: " + e.getMessage());
                }
            }
        }
        if (!errorMsgs.isEmpty()) return Result.error("部分对象权限更新失败:\n" + String.join("\n", errorMsgs));
        return Result.success("更新成功");
    }
}