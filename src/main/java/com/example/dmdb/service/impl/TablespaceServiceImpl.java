package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.config.DynamicContext;
import com.example.dmdb.mapper.SqlMapper;
import com.example.dmdb.mapper.TablespaceMapper;
import com.example.dmdb.service.TablespaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TablespaceServiceImpl implements TablespaceService {

    @Autowired
    private TablespaceMapper tablespaceMapper;

    @Autowired
    private SqlMapper sqlMapper;

    @Override
    public Result<Object> list() {
        return Result.success(tablespaceMapper.listTablespaces());
    }

    @Override
    public Result<Object> getFiles(String tablespaceName) {
        return Result.success(tablespaceMapper.listDatafiles(tablespaceName));
    }

    @Override
    public Result<Object> create(String name, String filePath, Integer sizeMb, Boolean autoExtend, Integer nextSizeMb, Integer maxSizeMb) {
        if (name == null || filePath == null || sizeMb == null) {
            return Result.error("基本参数不完整");
        }

        // 【新增】判断当前数据库是否为 Oracle
        boolean isOracle = "ORACLE".equalsIgnoreCase(DynamicContext.getCurrentDbType());

        StringBuilder sql = new StringBuilder();

        // 【分流逻辑 1】SIZE 处理
        // Oracle: 必须加 "M" (否则是字节) -> SIZE 100M
        // DM: 不加单位 (默认为MB) -> SIZE 100
        String sizeStr = isOracle ? sizeMb + "M" : String.valueOf(sizeMb);

        sql.append(String.format("CREATE TABLESPACE \"%s\" DATAFILE '%s' SIZE %s", name, filePath, sizeStr));

        if (Boolean.TRUE.equals(autoExtend)) {
            sql.append(" AUTOEXTEND ON");

            int next = (nextSizeMb != null && nextSizeMb > 0) ? nextSizeMb : 1;
            // 【分流逻辑 2】NEXT 处理
            String nextStr = isOracle ? next + "M" : String.valueOf(next);
            sql.append(" NEXT ").append(nextStr);

            if (maxSizeMb != null && maxSizeMb > 0) {
                // 【分流逻辑 3】MAXSIZE 处理
                String maxStr = isOracle ? maxSizeMb + "M" : String.valueOf(maxSizeMb);
                sql.append(" MAXSIZE ").append(maxStr);
            } else {
                sql.append(" MAXSIZE UNLIMITED");
            }
        } else {
            sql.append(" AUTOEXTEND OFF");
        }

        try {
            sqlMapper.executeSql(sql.toString());
            return Result.success("表空间创建成功");
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return Result.error("创建失败: " + msg);
        }
    }

    @Override
    public Result<Object> alterDatafile(String tablespaceName, String filePath, Boolean autoExtend, Integer nextSizeMb, Integer maxSizeMb) {
        if (tablespaceName == null || filePath == null) {
            return Result.error("表空间名或文件路径不能为空");
        }

        // 【新增】判断当前数据库是否为 Oracle
        boolean isOracle = "ORACLE".equalsIgnoreCase(DynamicContext.getCurrentDbType());

        StringBuilder sql = new StringBuilder();
        sql.append(String.format("ALTER TABLESPACE \"%s\" DATAFILE '%s'", tablespaceName, filePath));

        if (Boolean.TRUE.equals(autoExtend)) {
            sql.append(" AUTOEXTEND ON");

            int next = (nextSizeMb != null && nextSizeMb > 0) ? nextSizeMb : 1;
            // 【分流逻辑 4】NEXT 处理
            String nextStr = isOracle ? next + "M" : String.valueOf(next);
            sql.append(" NEXT ").append(nextStr);

            if (maxSizeMb != null && maxSizeMb > 0) {
                // 【分流逻辑 5】MAXSIZE 处理
                String maxStr = isOracle ? maxSizeMb + "M" : String.valueOf(maxSizeMb);
                sql.append(" MAXSIZE ").append(maxStr);
            } else {
                sql.append(" MAXSIZE UNLIMITED");
            }
        } else {
            sql.append(" AUTOEXTEND OFF");
        }

        try {
            System.out.println("Executing SQL: " + sql.toString());
            sqlMapper.executeSql(sql.toString());
            return Result.success("修改成功");
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return Result.error("修改失败: " + msg);
        }
    }

    @Override
    public Result<Object> delete(String name) {
        try {
            String sql = String.format("DROP TABLESPACE \"%s\"", name);
            sqlMapper.executeSql(sql);
            return Result.success("删除成功");
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return Result.error("删除失败: " + msg);
        }
    }
}