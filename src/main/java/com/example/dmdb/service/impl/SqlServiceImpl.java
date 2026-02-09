package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.config.DynamicContext;
import com.example.dmdb.mapper.MetadataMapper;
import com.example.dmdb.mapper.SqlMapper;
import com.example.dmdb.service.ConnectionManager;
import com.example.dmdb.service.base.AbstractDbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SqlServiceImpl extends AbstractDbService {

    @Autowired
    private SqlMapper sqlMapper;

    @Autowired
    private MetadataMapper metadataMapper;

    // 单次查询最大返回行数限制（安全底线），防止 OOM
    private static final int MAX_RESULT_ROWS = 5000;

    /**
     * 执行 SQL
     * 支持分页的关键点：自动去除 SQL 末尾的分号，允许外层嵌套分页语句
     */
    public Result<Object> executeSql(String sql, boolean manualCommit) {
        if (sql == null || sql.trim().isEmpty()) return Result.error("SQL不能为空");

        // 1. 清理 SQL：去除注释
        String cleanSql = removeComments(sql).trim();

        // 2. 【关键】去除末尾分号，防止前端包装分页子查询时报错 (e.g. SELECT * FROM (SELECT * FROM T;) LIMIT 10)
        if (cleanSql.endsWith(";")) {
            cleanSql = cleanSql.substring(0, cleanSql.length() - 1).trim();
        }

        String upperSql = cleanSql.toUpperCase();

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            String connId = DynamicContext.getKey();
            if (connId == null) return Result.error("未获取到连接ID (Conn-Id)");

            if (manualCommit) {
                conn = ConnectionManager.getConsoleConnection(connId);
                if (conn == null || conn.isClosed()) {
                    return Result.error("连接已断开，请重新连接");
                }
                if (conn.getAutoCommit()) {
                    conn.setAutoCommit(false);
                }
            } else {
                conn = ConnectionManager.getNewConnection(connId);
                if (conn == null) {
                    return Result.error("无法获取数据库连接");
                }
                if (!conn.getAutoCommit()) {
                    conn.setAutoCommit(true);
                }
            }

            if (manualCommit) {
                // 处理事务控制命令，这里需要用原始 SQL 判断或者去分号后的判断均可
                if ("COMMIT".equals(upperSql) || "COMMIT".equals(cleanSql.toUpperCase())) {
                    conn.commit();
                    ConnectionManager.setDirty(connId, false);
                    return buildDmlResult("事务已提交", 0, false);
                }
                if ("ROLLBACK".equals(upperSql) || "ROLLBACK".equals(cleanSql.toUpperCase())) {
                    if (!ConnectionManager.isDirty(connId)) {
                        return buildDmlResult("当前没有需要回滚的事务", 0, false);
                    }
                    conn.rollback();
                    ConnectionManager.setDirty(connId, false);
                    return buildDmlResult("事务已回滚", 0, false);
                }
            }

            stmt = conn.createStatement();
            // 设置 JDBC 层面的最大行数限制
            // 注意：如果是前端发来的分页 SQL (LIMIT x OFFSET y)，只要 x <= 5000，这个限制就不会影响分页结果
            stmt.setMaxRows(MAX_RESULT_ROWS);

            boolean hasResultSet = stmt.execute(cleanSql);

            if (hasResultSet) {
                rs = stmt.getResultSet();
                List<Map<String, Object>> resultList = processResultSet(rs);
                return Result.success(resultList);
            } else {
                int affectedRows = stmt.getUpdateCount();
                String msgPrefix = manualCommit ? "执行成功 (事务未提交)" : "执行成功";
                boolean isDirty = false;

                if (manualCommit) {
                    ConnectionManager.setDirty(connId, true);
                    isDirty = true;
                }
                return buildDmlResult(msgPrefix, affectedRows, isDirty);
            }

        } catch (SQLException e) {
            return Result.error("SQL执行异常: " + e.getMessage());
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (!manualCommit && conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 处理 ResultSet
     * 1. 限制返回行数
     * 2. 识别 LOB 数据并生成引用或预览
     */
    private List<Map<String, Object>> processResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        List<Map<String, Object>> list = new ArrayList<>();

        while (rs.next()) {
            // 应用层硬限制
            if (list.size() >= MAX_RESULT_ROWS) {
                break;
            }

            Map<String, Object> row = new LinkedHashMap<>(columns);
            for (int i = 1; i <= columns; ++i) {
                row.put(md.getColumnLabel(i), rs.getObject(i));
            }

            // 1. 寻找 ROWID
            String rowId = null;
            for (String key : row.keySet()) {
                if ("ROWID".equalsIgnoreCase(key) || "DB_INTERNAL_ID".equalsIgnoreCase(key)) {
                    Object idVal = row.get(key);
                    if (idVal != null) rowId = idVal.toString();
                    break;
                }
            }

            // 2. 处理 LOB
            for (int i = 1; i <= columns; ++i) {
                String label = md.getColumnLabel(i);
                Object val = row.get(label);

                if (val == null) continue;

                if (val instanceof Blob || val instanceof Clob || val instanceof byte[] || val instanceof InputStream
                        || val.getClass().getName().startsWith("dm.jdbc")) {

                    try {
                        String schema = md.getSchemaName(i);
                        String table = md.getTableName(i);

                        // 策略 A: 有 ROWID -> 生成操作句柄
                        if (rowId != null && table != null && !table.isEmpty()) {
                            if (schema == null || schema.isEmpty()) schema = "SYSDBA";
                            String type = val instanceof Clob ? "TEXT" : "BINARY";
                            String refStr = String.format("[LOB_REF:schema=%s,table=%s,col=%s,rowId=%s,type=%s]",
                                    schema, table, md.getColumnName(i), rowId, type);
                            row.put(label, refStr);
                        }
                        // 策略 B: 无 ROWID -> 生成预览或提示
                        else {
                            row.put(label, convertLobToPreview(val));
                        }
                    } catch (Exception e) {
                        row.put(label, "[LOB Read Error]");
                    }
                }
            }
            list.add(row);
        }
        return list;
    }

    private String convertLobToPreview(Object val) {
        try {
            InputStream is = null;
            if (val instanceof Blob) {
                is = ((Blob) val).getBinaryStream();
            } else if (val instanceof Clob) {
                Reader reader = ((Clob) val).getCharacterStream();
                char[] buf = new char[2048];
                int len = reader.read(buf);
                if (len > 0) return new String(buf, 0, len) + (len == 2048 ? "..." : "");
                return "";
            } else if (val instanceof byte[]) {
                byte[] bytes = (byte[]) val;
                if (bytes.length > 20 * 1024) {
                    return "[LOB_TIP:type=BINARY,msg=数据过大,hint=请在SQL中添加 ROWID 列以支持预览和下载]";
                }
                return "[LOB_B64:data=" + Base64.getEncoder().encodeToString(bytes) + "]";
            } else if (val instanceof InputStream) {
                is = (InputStream) val;
            }

            if (is != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[1024];
                int totalRead = 0;
                while ((nRead = is.read(data, 0, data.length)) != -1 && totalRead < 20 * 1024) {
                    buffer.write(data, 0, nRead);
                    totalRead += nRead;
                }
                buffer.flush();

                if (totalRead >= 20 * 1024) {
                    return "[LOB_TIP:type=BINARY,msg=数据过大,hint=请在SQL中添加 ROWID 列以支持预览和下载]";
                }
                return "[LOB_B64:data=" + Base64.getEncoder().encodeToString(buffer.toByteArray()) + "]";
            }
        } catch (Exception e) {
            return "[LOB Error]";
        }
        // 处理达梦内部对象，防止序列化报错
        return "[DM Object: " + val.getClass().getSimpleName() + "]";
    }

    public Result<Boolean> getTransactionStatus() {
        String connId = DynamicContext.getKey();
        return Result.success(ConnectionManager.isDirty(connId));
    }

    private Result<Object> buildDmlResult(String msg, int rows, boolean isDirty) {
        Map<String, Object> res = new HashMap<>();
        res.put("msg", msg);
        res.put("affectedRows", rows);
        res.put("dirty", isDirty);
        return Result.success(res);
    }

    public Result<Object> executeBatchSql(List<String> sqlList) {
        Connection conn = null;
        Statement stmt = null;
        try {
            String connId = DynamicContext.getKey();
            conn = ConnectionManager.getNewConnection(connId);
            if (conn == null) return Result.error("连接失败");

            conn.setAutoCommit(false);

            stmt = conn.createStatement();
            int totalRows = 0;
            for (String sql : sqlList) {
                if (sql != null && !sql.trim().isEmpty()) {
                    stmt.addBatch(sql);
                }
            }
            int[] results = stmt.executeBatch();
            conn.commit();

            for (int r : results) {
                if (r >= 0) totalRows += r;
            }
            return Result.success("批量执行成功，累计影响行数: " + totalRows);

        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
            }
            return Result.error("批量执行失败: " + e.getMessage());
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 执行脚本 (多条 SQL)
     */
    public Result<Object> executeScript(List<String> sqlList, boolean manualCommit) {
        if (sqlList == null || sqlList.isEmpty()) return Result.error("SQL列表不能为空");

        String connId = DynamicContext.getKey();
        if (connId == null) return Result.error("未获取到连接ID");

        Connection conn = null;
        Statement stmt = null;
        List<Map<String, Object>> executionResults = new ArrayList<>();
        boolean dirtyFlag = false;

        try {
            if (manualCommit) {
                conn = ConnectionManager.getConsoleConnection(connId);
                if (conn == null || conn.isClosed()) return Result.error("连接已断开");
                if (conn.getAutoCommit()) conn.setAutoCommit(false);
                dirtyFlag = ConnectionManager.isDirty(connId);
            } else {
                conn = ConnectionManager.getNewConnection(connId);
                if (conn == null) return Result.error("无法获取连接");
                if (!conn.getAutoCommit()) conn.setAutoCommit(true);
            }

            stmt = conn.createStatement();
            stmt.setMaxRows(MAX_RESULT_ROWS);

            for (int i = 0; i < sqlList.size(); i++) {
                String sql = sqlList.get(i);
                if (sql == null || sql.trim().isEmpty()) continue;

                String cleanSql = removeComments(sql).trim();
                // 【核心修改】去除脚本中每条语句的分号，并将 cleanSql 返回给前端
                if (cleanSql.endsWith(";")) {
                    cleanSql = cleanSql.substring(0, cleanSql.length() - 1).trim();
                }

                Map<String, Object> resultItem = new HashMap<>();
                resultItem.put("index", i + 1);
                // 返回清理后的 SQL，方便前端做分页包装
                resultItem.put("sql", cleanSql);
                long startTs = System.currentTimeMillis();

                try {
                    boolean hasResultSet = stmt.execute(cleanSql);
                    long duration = System.currentTimeMillis() - startTs;
                    resultItem.put("duration", duration);
                    resultItem.put("success", true);

                    if (hasResultSet) {
                        ResultSet rs = stmt.getResultSet();
                        List<Map<String, Object>> dataList = processResultSet(rs);
                        rs.close();

                        resultItem.put("type", "QUERY");
                        resultItem.put("data", dataList);
                        resultItem.put("rows", dataList.size());

                        String truncMsg = dataList.size() >= MAX_RESULT_ROWS ? " (显示前" + MAX_RESULT_ROWS + "行)" : "";
                        resultItem.put("msg", "查询成功" + truncMsg);
                    } else {
                        int rows = stmt.getUpdateCount();
                        resultItem.put("type", "UPDATE");
                        resultItem.put("affectedRows", rows);
                        resultItem.put("msg", "执行成功，影响 " + rows + " 行");
                        if (manualCommit) dirtyFlag = true;
                    }

                } catch (SQLException e) {
                    long duration = System.currentTimeMillis() - startTs;
                    resultItem.put("duration", duration);
                    resultItem.put("success", false);
                    resultItem.put("type", "ERROR");
                    resultItem.put("msg", e.getMessage());
                }
                executionResults.add(resultItem);
            }

            if (manualCommit) ConnectionManager.setDirty(connId, dirtyFlag);

            Map<String, Object> res = new HashMap<>();
            res.put("results", executionResults);
            if (manualCommit) res.put("dirty", dirtyFlag);
            return Result.success(res);

        } catch (SQLException e) {
            return Result.error("脚本执行异常: " + e.getMessage());
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (!manualCommit && conn != null && !conn.isClosed()) conn.close();
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    public Result<Map<String, Object>> getErData(String schema, String tableName, boolean showAll, List<String> expandedTables) {
        validateIdentifiers(schema, tableName);
        List<Map<String, Object>> relations = metadataMapper.getTableRelations(schema, tableName);

        Set<String> tableSet = new HashSet<>();
        tableSet.add(tableName);
        for (Map<String, Object> r : relations) {
            if (r.get("SOURCE_TABLE") != null) tableSet.add((String) r.get("SOURCE_TABLE"));
            if (r.get("TARGET_TABLE") != null) tableSet.add((String) r.get("TARGET_TABLE"));
        }
        List<String> tableList = new ArrayList<>(tableSet);
        List<Map<String, Object>> columns = tableList.isEmpty() ? new ArrayList<>() : metadataMapper.getColumnsForTables(schema, tableList);
        List<Map<String, Object>> comments = tableList.isEmpty() ? new ArrayList<>() : metadataMapper.getTableComments(schema, tableList);

        Map<String, String> commentMap = new HashMap<>();
        for (Map<String, Object> c : comments) commentMap.put((String) c.get("TABLE_NAME"), (String) c.get("COMMENTS"));

        Map<String, List<Map<String, Object>>> colMap = new HashMap<>();
        for (Map<String, Object> c : columns)
            colMap.computeIfAbsent((String) c.get("TABLE_NAME"), k -> new ArrayList<>()).add(c);

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String t : tableSet) {
            Map<String, Object> n = new HashMap<>();
            n.put("id", t);
            n.put("label", t);
            n.put("tableComment", commentMap.getOrDefault(t, ""));
            n.put("isCenter", t.equals(tableName));
            List<Map<String, Object>> rawCols = colMap.get(t);
            if (rawCols != null) {
                // ER图不需要LOB预览，简单清洗防止报错即可
                for(Map<String,Object> col : rawCols) {
                    for(Map.Entry<String,Object> e : col.entrySet()){
                        if(e.getValue() instanceof byte[] || e.getValue() instanceof Blob) e.setValue("");
                    }
                }
                int totalCount = rawCols.size();
                n.put("totalColCount", totalCount);
                boolean isExpanded = showAll || (expandedTables != null && expandedTables.contains(t));
                if (!isExpanded && totalCount > 10) {
                    n.put("columns", rawCols.stream().limit(10).collect(Collectors.toList()));
                    n.put("isTruncated", true);
                } else {
                    n.put("columns", rawCols);
                    n.put("isTruncated", false);
                }
            } else n.put("columns", new ArrayList<>());
            nodes.add(n);
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> r : relations) {
            Map<String, Object> e = new HashMap<>();
            e.put("source", r.get("SOURCE_TABLE"));
            e.put("target", r.get("TARGET_TABLE"));
            e.put("sourceCol", r.get("SOURCE_COL"));
            e.put("targetCol", r.get("TARGET_COL"));
            e.put("label", r.get("SOURCE_COL") + " -> " + r.get("TARGET_COL"));
            edges.add(e);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("nodes", nodes);
        res.put("edges", edges);
        return Result.success(res);
    }

    private String removeComments(String sql) {
        if (sql == null) return "";
        String pattern = "/\\*.*?\\*/|--.*?(\n|$)";
        Pattern r = Pattern.compile(pattern, Pattern.DOTALL);
        Matcher m = r.matcher(sql);
        return m.replaceAll("\n").trim();
    }

    /**
     * 【修复】权限问题：保持 protected 以覆盖父类方法
     * 同时也作为一个兼容性方法，供非 SQL 执行流程（如 ER 图）调用
     */
    @Override
    protected List<Map<String, Object>> processResultList(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return list;

        for (Map<String, Object> row : list) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                Object val = entry.getValue();
                if (val == null) continue;

                if (val instanceof Clob) {
                    entry.setValue("[CLOB 数据]");
                } else if (val instanceof Blob) {
                    entry.setValue("[BLOB 数据]");
                } else if (val instanceof byte[]) {
                    entry.setValue("[BINARY 数据]");
                } else if (val instanceof InputStream) {
                    entry.setValue("[InputStream 数据]");
                }
                // 防止达梦驱动的其他内部对象导致序列化失败
                else if (val.getClass().getName().startsWith("dm.jdbc")) {
                    entry.setValue("[DM Object: " + val.getClass().getSimpleName() + "]");
                }
            }
        }
        return list;
    }
}