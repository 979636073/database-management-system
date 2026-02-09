package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.config.DynamicContext;
import com.example.dmdb.mapper.MetadataMapper;
import com.example.dmdb.mapper.SqlMapper;
import com.example.dmdb.service.ConnectionManager;
import com.example.dmdb.service.base.AbstractDbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
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

    /**
     * 执行 SQL (支持事务隔离与状态追踪)
     *
     * @param sql          SQL语句
     * @param manualCommit true=控制台模式(长连接/手动事务), false=普通模式(短连接/自动提交)
     */
    public Result<Object> executeSql(String sql, boolean manualCommit) {
        if (sql == null || sql.trim().isEmpty()) return Result.error("SQL不能为空");

        String cleanSql = removeComments(sql).trim();
        String upperSql = cleanSql.toUpperCase();

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            String connId = DynamicContext.getKey();
            if (connId == null) return Result.error("未获取到连接ID (Conn-Id)");

            // 1. 获取连接
            if (manualCommit) {
                // 控制台模式：获取长连接
                conn = ConnectionManager.getConsoleConnection(connId);
                if (conn == null || conn.isClosed()) {
                    return Result.error("连接已断开，请重新连接");
                }
                // 确保关闭自动提交
                if (conn.getAutoCommit()) {
                    conn.setAutoCommit(false);
                }
            } else {
                // 普通模式：获取短连接
                conn = ConnectionManager.getNewConnection(connId);
                if (conn == null) {
                    return Result.error("无法获取数据库连接");
                }
                // 确保开启自动提交
                if (!conn.getAutoCommit()) {
                    conn.setAutoCommit(true);
                }
            }

            // 2. 处理事务命令 (仅在控制台模式下生效)
            if (manualCommit) {
                // COMMIT
                if ("COMMIT".equals(upperSql) || "COMMIT;".equals(upperSql)) {
                    conn.commit();
                    // 标记为 Clean
                    ConnectionManager.setDirty(connId, false);
                    return buildDmlResult("事务已提交", 0, false);
                }

                // ROLLBACK
                if ("ROLLBACK".equals(upperSql) || "ROLLBACK;".equals(upperSql)) {
                    // 检查是否有未提交事务
                    if (!ConnectionManager.isDirty(connId)) {
                        return buildDmlResult("当前没有需要回滚的事务", 0, false);
                    }
                    conn.rollback();
                    // 标记为 Clean
                    ConnectionManager.setDirty(connId, false);
                    return buildDmlResult("事务已回滚", 0, false);
                }
            }

            // 3. 执行 SQL
            stmt = conn.createStatement();
            boolean hasResultSet = stmt.execute(sql);

            if (hasResultSet) {
                // 查询语句 (SELECT)
                rs = stmt.getResultSet();
                List<Map<String, Object>> resultList = convertResultSetToList(rs);

                // 【关键修复】处理结果集中的特殊对象，防止 Jackson 序列化报错
                processResultList(resultList);

                return Result.success(resultList);
            } else {
                // 非查询语句 (INSERT/UPDATE/DELETE/DDL)
                int affectedRows = stmt.getUpdateCount();
                String msgPrefix = manualCommit ? "执行成功 (事务未提交)" : "执行成功";
                boolean isDirty = false;

                if (manualCommit) {
                    // DML 执行成功，标记为 Dirty
                    ConnectionManager.setDirty(connId, true);
                    isDirty = true;
                }

                return buildDmlResult(msgPrefix, affectedRows, isDirty);
            }

        } catch (SQLException e) {
            return Result.error("SQL执行异常: " + e.getMessage());
        } finally {
            // 资源释放
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();

                // 关键点：普通连接必须关闭，控制台长连接不能关闭
                if (!manualCommit && conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 【新增】获取当前事务状态 (是否 Dirty)
     * 供 Controller 调用
     */
    public Result<Boolean> getTransactionStatus() {
        String connId = DynamicContext.getKey();
        boolean isDirty = ConnectionManager.isDirty(connId);
        return Result.success(isDirty);
    }

    /**
     * 构建包含 dirty 状态的 DML 返回结果
     */
    private Result<Object> buildDmlResult(String msg, int rows, boolean isDirty) {
        Map<String, Object> res = new HashMap<>();
        res.put("msg", msg);
        res.put("affectedRows", rows);
        res.put("dirty", isDirty);
        return Result.success(res);
    }

    /**
     * 批量执行 (默认使用短连接+独立事务)
     */
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
                processResultList(rawCols);
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

    private List<Map<String, Object>> convertResultSetToList(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        List<Map<String, Object>> list = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>(columns);
            for (int i = 1; i <= columns; ++i) {
                row.put(md.getColumnLabel(i), rs.getObject(i));
            }
            list.add(row);
        }
        return list;
    }

    /**
     * 【核心修改】脚本执行 (多条 SQL)
     * 支持多条 SELECT 返回多个结果集，且单条报错不影响后续执行
     */
    public Result<Object> executeScript(List<String> sqlList, boolean manualCommit) {
        if (sqlList == null || sqlList.isEmpty()) return Result.error("SQL列表不能为空");

        String connId = DynamicContext.getKey();
        if (connId == null) return Result.error("未获取到连接ID");

        Connection conn = null;
        Statement stmt = null;

        // 用于存放每条语句的执行结果
        List<Map<String, Object>> executionResults = new ArrayList<>();
        // 标记事务状态是否变脏
        boolean dirtyFlag = false;

        try {
            // 1. 获取连接
            if (manualCommit) {
                conn = ConnectionManager.getConsoleConnection(connId);
                if (conn == null || conn.isClosed()) return Result.error("连接已断开");
                if (conn.getAutoCommit()) conn.setAutoCommit(false);
                // 初始状态
                dirtyFlag = ConnectionManager.isDirty(connId);
            } else {
                conn = ConnectionManager.getNewConnection(connId);
                if (conn == null) return Result.error("无法获取连接");
                if (!conn.getAutoCommit()) conn.setAutoCommit(true);
            }

            stmt = conn.createStatement();

            // 2. 循环执行 (容错模式)
            for (int i = 0; i < sqlList.size(); i++) {
                String sql = sqlList.get(i);
                if (sql == null || sql.trim().isEmpty()) continue;
                String cleanSql = removeComments(sql).trim();

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
                        // === 情况A: 查询语句 (SELECT) ===
                        ResultSet rs = stmt.getResultSet();
                        List<Map<String, Object>> dataList = convertResultSetToList(rs);
                        rs.close(); // 及时关闭 ResultSet

                        // 【关键修正】这里也必须调用处理方法，否则执行脚本查询时依然会报错
                        processResultList(dataList);

                        resultItem.put("type", "QUERY");
                        resultItem.put("data", dataList); // 保存数据供前端展示
                        resultItem.put("rows", dataList.size());
                        resultItem.put("msg", "查询成功");
                    } else {
                        // === 情况B: 更新语句 (INSERT/UPDATE/DELETE/DDL) ===
                        int rows = stmt.getUpdateCount();
                        resultItem.put("type", "UPDATE");
                        resultItem.put("affectedRows", rows);
                        resultItem.put("msg", "执行成功，影响 " + rows + " 行");

                        if (manualCommit) dirtyFlag = true;
                    }

                } catch (SQLException e) {
                    // === 情况C: 执行报错 ===
                    long duration = System.currentTimeMillis() - startTs;
                    resultItem.put("duration", duration);
                    resultItem.put("success", false);
                    resultItem.put("type", "ERROR");
                    resultItem.put("msg", e.getMessage());
                }

                executionResults.add(resultItem);
            }

            // 更新事务状态
            if (manualCommit) {
                ConnectionManager.setDirty(connId, dirtyFlag);
            }

            // 返回结果列表
            Map<String, Object> res = new HashMap<>();
            res.put("results", executionResults); // 包含所有结果(数据或错误)
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

    /**
     * 【关键新增】处理结果集，将无法序列化的 Blob/Clob/Binary 对象转换为字符串占位符
     * 解决 Jackson 序列化 dm.jdbc.driver.DmdbBlob 报错的问题
     */
    public List<Map<String, Object>> processResultList(List<Map<String, Object>> list) {
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

    // 提取原 executeSql 的核心逻辑为 executeSingle，方便维护
    private Result<Object> executeSingle(String sql, boolean manualCommit) {
        String cleanSql = removeComments(sql).trim();
        String upperSql = cleanSql.toUpperCase();

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            String connId = DynamicContext.getKey();
            if (connId == null) return Result.error("未获取到连接ID");

            if (manualCommit) {
                conn = ConnectionManager.getConsoleConnection(connId);
                if (conn == null || conn.isClosed()) return Result.error("连接已断开");
                if (conn.getAutoCommit()) conn.setAutoCommit(false);
            } else {
                conn = ConnectionManager.getNewConnection(connId);
                if (conn == null) return Result.error("无法获取连接");
                if (!conn.getAutoCommit()) conn.setAutoCommit(true);
            }

            if (manualCommit) {
                if ("COMMIT".equals(upperSql) || "COMMIT;".equals(upperSql)) {
                    conn.commit();
                    ConnectionManager.setDirty(connId, false);
                    return buildDmlResult("事务已提交", 0, false);
                }
                if ("ROLLBACK".equals(upperSql) || "ROLLBACK;".equals(upperSql)) {
                    if (!ConnectionManager.isDirty(connId)) return buildDmlResult("当前没有需要回滚的事务", 0, false);
                    conn.rollback();
                    ConnectionManager.setDirty(connId, false);
                    return buildDmlResult("事务已回滚", 0, false);
                }
            }

            stmt = conn.createStatement();
            boolean hasResultSet = stmt.execute(sql);

            if (hasResultSet) {
                rs = stmt.getResultSet();
                List<Map<String, Object>> resultList = convertResultSetToList(rs);
                // 同样应用处理逻辑
                return Result.success(processResultList(resultList));
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
                if (!manualCommit && conn != null && !conn.isClosed()) conn.close();
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }
}