package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.mapper.SqlMapper;
import com.example.dmdb.mapper.UserMapper;
import com.example.dmdb.service.base.AbstractDbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl extends AbstractDbService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SqlMapper sqlMapper;

    /**
     * 获取用户列表及元数据
     */
    public Result<Object> getUserList() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("users", userMapper.getUsers());
            data.put("tablespaces", userMapper.getTablespaces());
            // 同时返回所有系统权限列表，供前端缓存使用
            data.put("allSysPrivs", userMapper.getAllSysPrivileges());
            return Result.success(data);
        } catch (Exception e) {
            log.error("获取用户列表失败", e);
            return Result.error(getCleanError(e));
        }
    }

    /**
     * 获取指定用户的详细权限信息（系统权限、对象权限、角色）
     */
    public Result<Object> getUserPrivileges(String username) {
        try {
            Map<String, Object> data = new HashMap<>();
            // 1. 系统权限
            data.put("sysPrivs", userMapper.getUserSysPrivs(username));
            // 2. 对象权限
            data.put("objPrivs", userMapper.getUserObjPrivs(username));
            // 3. 已拥有的角色
            data.put("roles", userMapper.getUserRoles(username));

            // 4. 辅助数据：系统所有角色列表 (供授权选择)
            data.put("allRoles", userMapper.getAllRoles());
            // 5. 辅助数据：系统所有权限列表
            data.put("allSysPrivs", userMapper.getAllSysPrivileges());

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(getCleanError(e));
        }
    }

    /**
     * 创建用户
     */
    @Transactional
    public Result<Object> createUser(Map<String, Object> params) {
        String username = (String) params.get("username");
        String password = (String) params.get("password");
        String tablespace = (String) params.get("tablespace");

        validateIdentifiers(username);

        try {
            // 构造 SQL: CREATE USER "NAME" IDENTIFIED BY "PWD" [DEFAULT TABLESPACE "TS"]
            String sql = String.format("CREATE USER \"%s\" IDENTIFIED BY \"%s\"", username, password);
            if (tablespace != null && !tablespace.isEmpty()) {
                sql += String.format(" DEFAULT TABLESPACE \"%s\"", tablespace);
            }
            sqlMapper.executeSql(sql);
            return Result.success("用户创建成功");
        } catch (Exception e) {
            return Result.error(getCleanError(e));
        }
    }

    /**
     * 修改用户 (锁定/解锁/改密/配额)
     */
    @Transactional
    public Result<Object> alterUser(Map<String, Object> params) {
        String username = (String) params.get("username");
        String type = (String) params.get("type"); // LOCK, UNLOCK, PWD, QUOTA

        validateIdentifiers(username);
        String sql = "";

        try {
            switch (type) {
                case "LOCK":
                    sql = String.format("ALTER USER \"%s\" ACCOUNT LOCK", username);
                    break;
                case "UNLOCK":
                    sql = String.format("ALTER USER \"%s\" ACCOUNT UNLOCK", username);
                    break;
                case "PWD":
                    String newPwd = (String) params.get("password");
                    sql = String.format("ALTER USER \"%s\" IDENTIFIED BY \"%s\"", username, newPwd);
                    break;
                case "QUOTA":
                    String quota = (String) params.get("quota"); // e.g. "100M" or "UNLIMITED"
                    String ts = (String) params.get("tablespace");
                    sql = String.format("ALTER USER \"%s\" QUOTA %s ON \"%s\"", username, quota, ts);
                    break;
                default:
                    return Result.error("不支持的操作类型");
            }
            sqlMapper.executeSql(sql);
            return Result.success("修改成功");
        } catch (Exception e) {
            return Result.error(getCleanError(e));
        }
    }

    /**
     * 授予或回收权限/角色 (核心权限操作)
     * 支持 WITH ADMIN OPTION 和 WITH GRANT OPTION
     */
    @Transactional
    public Result<Object> grantRevoke(Map<String, Object> params) {
        String username = (String) params.get("username");
        String action = (String) params.get("action"); // GRANT 或 REVOKE
        String type = (String) params.get("type");     // SYS (系统权限), OBJ (对象权限), ROLE (角色)
        String item = (String) params.get("privilege"); // 权限名 或 角色名
        Boolean adminOption = (Boolean) params.get("adminOption"); // 是否带转授选项

        validateIdentifiers(username);

        String sql = "";
        try {
            if ("SYS".equals(type)) {
                // 系统权限: GRANT CREATE TABLE TO "USER" [WITH ADMIN OPTION]
                sql = String.format("%s %s %s \"%s\"", action, item, "GRANT".equals(action) ? "TO" : "FROM", username);
                if ("GRANT".equals(action) && Boolean.TRUE.equals(adminOption)) {
                    sql += " WITH ADMIN OPTION";
                }
            }
            else if ("OBJ".equals(type)) {
                // 对象权限: GRANT SELECT ON "SCHEMA"."TABLE" TO "USER" [WITH GRANT OPTION]
                String objectName = (String) params.get("objectName"); // 前端需传 "SCHEMA"."TABLE"
                sql = String.format("%s %s ON %s %s \"%s\"", action, item, objectName, "GRANT".equals(action) ? "TO" : "FROM", username);
                if ("GRANT".equals(action) && Boolean.TRUE.equals(adminOption)) {
                    sql += " WITH GRANT OPTION";
                }
            }
            else if ("ROLE".equals(type)) {
                // 角色授予: GRANT "ROLE_NAME" TO "USER" [WITH ADMIN OPTION]
                sql = String.format("%s \"%s\" %s \"%s\"", action, item, "GRANT".equals(action) ? "TO" : "FROM", username);
                if ("GRANT".equals(action) && Boolean.TRUE.equals(adminOption)) {
                    sql += " WITH ADMIN OPTION";
                }
            }

            sqlMapper.executeSql(sql);
            return Result.success(action + " 成功");
        } catch (Exception e) {
            return Result.error(getCleanError(e));
        }
    }

    /**
     * 删除用户
     */
    @Transactional
    public Result<Object> dropUser(String username) {
        validateIdentifiers(username);
        try {
            // CASCADE 会同时删除该用户模式下的所有对象
            sqlMapper.executeSql(String.format("DROP USER \"%s\" CASCADE", username));
            return Result.success("用户删除成功");
        } catch (Exception e) {
            return Result.error(getCleanError(e));
        }
    }

    /**
     * 辅助方法：提取底层异常信息并清洗干扰字符
     */
    private String getCleanError(Exception e) {
        Throwable root = e;
        // 递归寻找 Root Cause
        while (root.getCause() != null) {
            root = root.getCause();
        }

        String msg = root.getMessage();
        if (msg == null) return "未知错误";

        // 1. 针对“对象已存在”错误的友好提示 (解决用户/角色重名困惑)
        if (msg.contains("[-2501]") || (msg.contains("数据库对象") && msg.contains("已存在"))) {
            return "创建失败：该名称已被现有的【用户】或【角色】占用，请更换用户名或检查角色列表。";
        }

        // 2. 去除达梦错误码前缀 [-xxxx]:
        msg = msg.replaceAll("^\\[-?\\d+\\]:\\s*", "");

        // 3. 去除 "第x行附近出现错误:" 这种定位信息
        msg = msg.replaceAll("第\\d+\\s*行附近出现错误:\\s*", "");

        return msg.trim();
    }
}