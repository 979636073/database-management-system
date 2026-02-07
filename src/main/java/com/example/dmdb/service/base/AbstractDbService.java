package com.example.dmdb.service.base;

import com.example.dmdb.common.Result;
import com.example.dmdb.mapper.DbMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Clob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public abstract class AbstractDbService {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    protected DbMapper dbMapper;

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z0-9_$#\\u4e00-\\u9fa5\\.\\s]+$");
    private static final Pattern SIMPLE_UPPER_IDENTIFIER = Pattern.compile("^[A-Z0-9_$]+$");

    protected String quote(String id) {
        if (id == null) return "";
        if (SIMPLE_UPPER_IDENTIFIER.matcher(id).matches()) return id;
        return "\"" + id + "\"";
    }

    protected String quoteSysPriv(String priv) {
        if (priv == null) return "";
        if (priv.trim().toUpperCase().startsWith("GRANT ")) return "\"" + priv + "\"";
        return priv;
    }

    protected void executeSqlQuietly(String sql) {
        try {
            log.info("Executing Quietly: {}", sql);
            dbMapper.executeSql(sql);
        } catch (Throwable e) {
            log.warn("Ignored error executing SQL [{}]: {}", sql, e.getMessage());
        }
    }

    protected void validateIdentifiers(String... identifiers) {
        for (String id : identifiers) {
            if (id == null || id.trim().isEmpty() || "*".equals(id)) continue;
            if (!SAFE_IDENTIFIER.matcher(id).matches()) {
                System.err.println("Warning: Identifier validation warning for " + id);
            }
        }
    }

    protected String clobToString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        if (obj instanceof Clob) {
            try {
                Clob clob = (Clob) obj;
                long len = clob.length();
                if (len == 0) return "";
                return clob.getSubString(1, (int) len);
            } catch (SQLException e) {
                e.printStackTrace();
                return "";
            }
        }
        return obj.toString();
    }

    protected List<Map<String, Object>> processResultList(List<Map<String, Object>> list) {
        if (list == null) return new ArrayList<>();
        for (Map<String, Object> row : list) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof Clob) entry.setValue(clobToString(val));
                if (val instanceof java.sql.RowId) entry.setValue(val.toString());
            }
        }
        return list;
    }

    protected boolean isPk(Object val) {
        if (val == null) return false;
        String s = val.toString();
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "Y".equalsIgnoreCase(s);
    }

    protected boolean isViewObject(String schema, String name) {
        try {
            List<Map<String, Object>> views = dbMapper.getViews(schema);
            if (views == null) return false;
            for (Map<String, Object> v : views) {
                Object vName = v.get("VIEW_NAME");
                if (vName != null && name.equals(vName.toString())) return true;
            }
            return false;
        } catch (Exception e) { return false; }
    }

    protected boolean analyzeViewComplexity(String schema, String viewName) {
        try {
            Object ddlObj = dbMapper.getDDL(schema, viewName, "VIEW");
            String ddl = clobToString(ddlObj);
            if (ddl == null || ddl.trim().isEmpty()) return false;
            String sql = ddl.toUpperCase();
            int asIndex = sql.indexOf(" AS ");
            String body = (asIndex != -1) ? sql.substring(asIndex + 4) : sql;
            return !(body.contains(" JOIN ") || body.contains(" DISTINCT ") || body.contains(" GROUP BY ") || body.contains(" UNION "));
        } catch (Exception e) { return false; }
    }

    protected Result<Object> analyzeConflict(Exception e, String schema, String tableName, Object pkValue, Map<String, Object> rowData) {
        String msg = (e != null) ? e.getMessage() : "";
        if (msg == null) msg = "";

        boolean isIntegrityError = (e == null) ||
                msg.contains("integrity constraint violation") ||
                msg.contains("violation of foreign key") ||
                msg.contains("-2291") ||
                msg.contains("-2292") ||
                msg.contains("引用") ||
                msg.contains("参考") ||
                msg.contains("约束") ||
                msg.contains("违反");

        if (!isIntegrityError) return Result.error("操作失败: " + msg);

        List<Map<String, Object>> conflicts = new ArrayList<>();

        try {
            // A. 删除/修改主键冲突
            if (pkValue != null) {
                List<Map<String, String>> childTables = dbMapper.getAllChildTables(schema, tableName);
                if (childTables != null) {
                    for (Map<String, String> child : childTables) {
                        String cTable = child.get("TABLE_NAME");
                        String cCol = child.get("COLUMN_NAME");
                        int count = dbMapper.countReference(schema, cTable, cCol, pkValue);
                        if (count > 0) {
                            Map<String, Object> c = new HashMap<>();
                            c.put("TABLE_NAME", cTable);
                            c.put("COLUMN_NAME", cCol);
                            c.put("CNT", count);
                            c.put("MY_VAL", pkValue);
                            conflicts.add(c);
                        }
                    }
                }
            }

            // B. 插入/更新外键冲突
            if (conflicts.isEmpty() && rowData != null) {
                List<Map<String, Object>> fks = processResultList(dbMapper.getForeignKeys(schema, tableName));
                if (fks != null) {
                    for (Map<String, Object> fk : fks) {
                        String myCol = (String) fk.get("COLUMN_NAME");
                        String pTable = (String) fk.get("R_TABLE_NAME");
                        String pCol = (String) fk.get("R_COLUMN_NAME");
                        if (myCol != null && rowData.containsKey(myCol)) {
                            Object val = rowData.get(myCol);
                            if (val != null && !val.toString().isEmpty()) {
                                int exist = dbMapper.countReference(schema, pTable, pCol, val);
                                if (exist == 0) {
                                    Map<String, Object> c = new HashMap<>();
                                    c.put("TABLE_NAME", pTable);
                                    c.put("COLUMN_NAME", pCol);
                                    c.put("CNT", "MISSING");
                                    c.put("MY_VAL", val);
                                    conflicts.add(c);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Conflict analysis failed", ex);
        }

        if (!conflicts.isEmpty()) {
            Result<Object> r = new Result<>();
            r.setCode(503);
            r.setMsg("存在数据完整性冲突");
            r.setData(conflicts);
            return r;
        }
        return Result.error("完整性约束校验失败: " + msg);
    }
}