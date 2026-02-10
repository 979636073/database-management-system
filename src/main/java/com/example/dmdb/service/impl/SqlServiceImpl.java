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
     * 兼容性说明：自动去除末尾分号，支持前端包装标准 SQL 分页
     */
    public Result<Object> executeSql(String sql, boolean manualCommit) {
        if (sql == null || sql.trim().isEmpty()) return Result.error("SQL不能为空");

        // 1. 清理 SQL
        String cleanSql = removeComments(sql).trim();

        // 2. 去除末尾分号 (Oracle/DM JDBC均建议去除)
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
                // 事务控制命令通用
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
            // 设置最大行数限制
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
     */
    private List<Map<String, Object>> processResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        List<Map<String, Object>> list = new ArrayList<>();

        while (rs.next()) {
            if (list.size() >= MAX_RESULT_ROWS) {
                break;
            }

            Map<String, Object> row = new LinkedHashMap<>(columns);
            for (int i = 1; i <= columns; ++i) {
                // Oracle 列名通常大写，这里保持原样
                row.put(md.getColumnLabel(i), rs.getObject(i));
            }

            // 1. 寻找 ROWID (Oracle/DM 均为 "ROWID")
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

                // 【适配修改】增加对 Oracle 驱动对象的兼容检测
                if (isLobOrVendorObject(val)) {
                    try {
                        String schema = md.getSchemaName(i);
                        String table = md.getTableName(i);

                        // 策略 A: 有 ROWID -> 生成操作句柄
                        if (rowId != null && table != null && !table.isEmpty()) {
                            // Oracle 如果 schema 为空通常意味着当前用户，或者驱动未返回
                            if (schema == null || schema.isEmpty()) schema = "CURRENT_SCHEMA";
                            String type = (val instanceof Clob) ? "TEXT" : "BINARY";
                            String refStr = String.format("[LOB_REF:schema=%s,table=%s,col=%s,rowId=%s,type=%s]",
                                    schema, table, md.getColumnName(i), rowId, type);
                            row.put(label, refStr);
                        }
                        // 策略 B: 无 ROWID -> 生成预览
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

    /**
     * 判断是否为 LOB 或 厂商特有 JDBC 对象
     */
    private boolean isLobOrVendorObject(Object val) {
        if (val instanceof Blob || val instanceof Clob || val instanceof byte[] || val instanceof InputStream) {
            return true;
        }
        String className = val.getClass().getName();
        // 【适配修改】同时检测达梦和 Oracle 的驱动包名
        return className.startsWith("dm.jdbc") || className.startsWith("oracle.jdbc") || className.startsWith("oracle.sql");
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
        // 【适配修改】通用占位符
        return "[DB Object: " + val.getClass().getSimpleName() + "]";
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
                if (cleanSql.endsWith(";")) {
                    cleanSql = cleanSql.substring(0, cleanSql.length() - 1).trim();
                }

                Map<String, Object> resultItem = new HashMap<>();
                resultItem.put("index", i + 1);
                resultItem.put("sql", sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
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

            if (manualCommit) {
                ConnectionManager.setDirty(connId, dirtyFlag);
            }

            Map<String, Object> res = new HashMap<>();
            res.put("results", executionResults);
            if (manualCommit) {
                res.put("dirty", dirtyFlag);
            }
            return Result.success(res);

        } catch (SQLException e) {
            return Result.error("脚本执行异常: " + e.getMessage());
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (!manualCommit && conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    public Result<Map<String, Object>> getErData(String schema, String tableName, boolean showAll, List<String> expandedTables) {
        return metadataMapper != null ? Result.success(new HashMap<>()) : Result.error("Service Unavailable");
    }

    private String removeComments(String sql) {
        if (sql == null) return "";
        Pattern r = Pattern.compile("/\\*.*?\\*/|--.*?(\n|$)", Pattern.DOTALL);
        return r.matcher(sql).replaceAll("\n").trim();
    }

    /**
     * 父类兼容方法
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
                // 【适配修改】防止达梦或Oracle驱动内部对象导致序列化失败
                else if (isLobOrVendorObject(val)) {
                    entry.setValue("[DB Object: " + val.getClass().getSimpleName() + "]");
                }
            }
        }
        return list;
    }
}