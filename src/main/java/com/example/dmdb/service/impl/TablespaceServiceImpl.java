package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
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

        StringBuilder sql = new StringBuilder();
        // CREATE 语句中 SIZE 通常需要单位，保留 M
        sql.append(String.format("CREATE TABLESPACE \"%s\" DATAFILE '%s' SIZE %d", name, filePath, sizeMb));

        if (Boolean.TRUE.equals(autoExtend)) {
            sql.append(" AUTOEXTEND ON");

            // NEXT 必须 > 0。如果前端传 0，这里给一个最小默认值 1
            int next = (nextSizeMb != null && nextSizeMb > 0) ? nextSizeMb : 1;
            // [修改] 移除 M 单位，直接接数字
            sql.append(" NEXT ").append(next);

            if (maxSizeMb != null && maxSizeMb > 0) {
                // [修改] 移除 M 单位
                sql.append(" MAXSIZE ").append(maxSizeMb);
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

        StringBuilder sql = new StringBuilder();
        sql.append(String.format("ALTER TABLESPACE \"%s\" DATAFILE '%s'", tablespaceName, filePath));

        if (Boolean.TRUE.equals(autoExtend)) {
            sql.append(" AUTOEXTEND ON");

            // [修改] 如果前端传 0，默认为 1 (防止 NEXT 0 报错)
            int next = (nextSizeMb != null && nextSizeMb > 0) ? nextSizeMb : 1;
            // [修改] 移除 M 单位，直接接数字
            sql.append(" NEXT ").append(next);

            if (maxSizeMb != null && maxSizeMb > 0) {
                // [修改] 移除 M 单位
                sql.append(" MAXSIZE ").append(maxSizeMb);
            } else {
                sql.append(" MAXSIZE UNLIMITED");
            }
        } else {
            sql.append(" AUTOEXTEND OFF");
        }

        try {
            // 打印 SQL 用于调试
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