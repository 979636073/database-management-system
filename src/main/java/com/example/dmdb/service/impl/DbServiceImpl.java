package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.mapper.DbMapper;
import com.example.dmdb.service.DbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.sql.Clob;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DbServiceImpl implements DbService {

    private static final Logger log = LoggerFactory.getLogger(DbServiceImpl.class);

    @Autowired
    private DbMapper dbMapper;

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z0-9_$#\\u4e00-\\u9fa5\\.\\s]+$");
    private static final Pattern SIMPLE_UPPER_IDENTIFIER = Pattern.compile("^[A-Z0-9_$]+$");

    // ==========================================
    // 核心辅助方法
    // ==========================================

    private String quote(String id) {
        if (id == null) return "";
        if (SIMPLE_UPPER_IDENTIFIER.matcher(id).matches()) {
            return id;
        }
        return "\"" + id + "\"";
    }

    private String quoteSysPriv(String priv) {
        if (priv == null) return "";
        if (priv.trim().toUpperCase().startsWith("GRANT ")) {
            return "\"" + priv + "\"";
        }
        return priv;
    }

    private void executeSqlQuietly(String sql) {
        try {
            log.info("Executing Quietly: {}", sql);
            dbMapper.executeSql(sql);
        } catch (Throwable e) {
            log.warn("Ignored error executing SQL [{}]: {}", sql, e.getMessage());
        }
    }

    private void validateIdentifiers(String... identifiers) {
        for (String id : identifiers) {
            if (id == null || id.trim().isEmpty() || "*".equals(id)) continue;
            if (!SAFE_IDENTIFIER.matcher(id).matches()) {
                System.err.println("Warning: Identifier validation warning for " + id);
            }
        }
    }

    private String clobToString(Object obj) {
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

    private List<Map<String, Object>> processResultList(List<Map<String, Object>> list) {
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

    // ==========================================
    // 【核心修复】智能冲突检测 - 强健壮性版本
    // ==========================================
    private Result<Object> analyzeConflict(Exception e, String schema, String tableName, Object pkValue, Map<String, Object> rowData) {
        String msg = (e != null) ? e.getMessage() : "";
        if (msg == null) msg = "";

        // 宽松判断是否为约束错误 (e 为 null 时跳过校验，直接强制分析)
        // 增加对 "违反引用约束" 等中文关键词的支持
        boolean isIntegrityError = (e == null) ||
                msg.contains("integrity constraint violation") ||
                msg.contains("violation of foreign key") ||
                msg.contains("-2291") ||
                msg.contains("-2292") ||
                msg.contains("引用") ||
                msg.contains("参考") ||
                msg.contains("约束") ||
                msg.contains("违反");

        if (!isIntegrityError) {
            return Result.error("操作失败: " + msg);
        }

        List<Map<String, Object>> conflicts = new ArrayList<>();

        try {
            // A. 删除/修改主键冲突：检查子表引用
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

            // B. 插入/更新外键冲突：检查父表是否存在
            if (conflicts.isEmpty() && rowData != null) {
                List<Map<String, Object>> fks = processResultList(dbMapper.getForeignKeys(schema, tableName));
                if (fks != null) {
                    for (Map<String, Object> fk : fks) {
                        if (fk == null) continue;

                        String myCol = (String) fk.get("COLUMN_NAME");
                        String pTable = (String) fk.get("R_TABLE_NAME");
                        String pCol = (String) fk.get("R_COLUMN_NAME");

                        if (myCol != null && rowData.containsKey(myCol)) {
                            Object val = rowData.get(myCol);
                            // 只有值不为空时才检查引用
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

    // ... (保留 View, ER, Data 等基础查询方法，保持不变) ...
    private boolean isViewObject(String schema, String name) {
        try {
            List<Map<String, Object>> views = dbMapper.getViews(schema);
            if (views == null) return false;
            for (Map<String, Object> v : views) {
                Object vName = v.get("VIEW_NAME");
                if (vName != null && name.equals(vName.toString())) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean analyzeViewComplexity(String schema, String viewName) {
        try {
            Object ddlObj = dbMapper.getDDL(schema, viewName, "VIEW");
            String ddl = clobToString(ddlObj);
            if (ddl == null || ddl.trim().isEmpty()) return false;
            String sql = ddl.toUpperCase();
            int asIndex = sql.indexOf(" AS ");
            String body = (asIndex != -1) ? sql.substring(asIndex + 4) : sql;
            if (body.contains(" JOIN ") || body.contains(" LEFT ") || body.contains(" RIGHT ")) return false;
            int fromIdx = body.lastIndexOf(" FROM ");
            if (fromIdx != -1) {
                String fromPart = body.substring(fromIdx + 6);
                int endIdx = fromPart.length();
                int whereIdx = fromPart.indexOf(" WHERE ");
                if (whereIdx != -1 && whereIdx < endIdx) endIdx = whereIdx;
                String tablePart = fromPart.substring(0, endIdx);
                if (tablePart.contains(",")) return false;
            }
            if (body.contains(" DISTINCT ") || body.contains(" GROUP BY ") || body.contains(" HAVING ") || body.contains(" UNION "))
                return false;
            Pattern aggPattern = Pattern.compile("\\b(AVG|SUM|COUNT|MIN|MAX)\\s*\\(");
            if (aggPattern.matcher(body).find()) return false;
            Pattern fromTablePattern = Pattern.compile("FROM\\s+[\"]?([a-zA-Z0-9_$#\\u4e00-\\u9fa5]+)[\"]?");
            Matcher m = fromTablePattern.matcher(body);
            if (m.find()) {
                String baseTableName = m.group(1);
                List<Map<String, Object>> baseCols = dbMapper.getColumns(schema, baseTableName);
                List<String> basePks = baseCols.stream().filter(c -> isPk(c.get("IS_PK"))).map(c -> (String) c.get("COLUMN_NAME")).collect(Collectors.toList());
                if (basePks.isEmpty()) return false;
                List<Map<String, Object>> viewCols = dbMapper.getColumns(schema, viewName);
                Set<String> viewColNames = viewCols.stream().map(c -> (String) c.get("COLUMN_NAME")).collect(Collectors.toSet());
                for (String pk : basePks) {
                    if (!viewColNames.contains(pk)) return false;
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isPk(Object val) {
        if (val == null) return false;
        String s = val.toString();
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "Y".equalsIgnoreCase(s);
    }

    private Map<String, Object> buildGraphStructure(String centerTable, Set<String> tables, List<Map<String, Object>> relations, List<Map<String, Object>> columns, List<Map<String, Object>> comments, boolean showAll, List<String> expandedTables) {
        Map<String, String> commentMap = new HashMap<>();
        for (Map<String, Object> c : comments) commentMap.put((String) c.get("TABLE_NAME"), (String) c.get("COMMENTS"));
        Map<String, List<Map<String, Object>>> colMap = new HashMap<>();
        for (Map<String, Object> c : columns)
            colMap.computeIfAbsent((String) c.get("TABLE_NAME"), k -> new ArrayList<>()).add(c);
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String t : tables) {
            Map<String, Object> n = new HashMap<>();
            n.put("id", t);
            n.put("label", t);
            n.put("tableComment", commentMap.getOrDefault(t, ""));
            n.put("isCenter", t.equals(centerTable));
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
        return res;
    }

    @Override
    public Result<List<String>> getSchemas() {
        return Result.success(dbMapper.getAllSchemas());
    }

    @Override
    public Result<List<Map<String, String>>> getTables(String schema) {
        validateIdentifiers(schema);
        return Result.success(dbMapper.getTablesBySchema(schema));
    }

    @Override
    public Result<List<Map<String, Object>>> getColumns(String schema, String tableName) {
        validateIdentifiers(schema, tableName);
        return Result.success(dbMapper.getColumns(schema, tableName));
    }

    @Override
    public Result<List<Map<String, Object>>> getViews(String schema) {
        validateIdentifiers(schema);
        return Result.success(processResultList(dbMapper.getViews(schema)));
    }

    @Override
    public Result<List<Map<String, Object>>> getTriggers(String schema) {
        validateIdentifiers(schema);
        return Result.success(processResultList(dbMapper.getTriggers(schema)));
    }

    @Override
    public Result<Map<String, Object>> getData(String schema, String tableName, int page, int size) {
        validateIdentifiers(schema, tableName);
        int offset = (page - 1) * size;
        long total = dbMapper.countData(schema, tableName);
        List<Map<String, Object>> list = processResultList(dbMapper.getDataPage(schema, tableName, size, offset));
        Map<String, Object> res = new HashMap<>();
        res.put("total", total);
        res.put("list", list);
        boolean isView = isViewObject(schema, tableName);
        res.put("isView", isView);
        if (isView) {
            res.put("isSimpleView", analyzeViewComplexity(schema, tableName));
        }
        return Result.success(res);
    }

    @Override
    public Result<Map<String, Object>> filterData(Map<String, Object> payload) {
        String schema = (String) payload.get("schema");
        String tableName = (String) payload.get("tableName");
        String logic = (String) payload.get("logic");
        List<Map<String, String>> conditions = (List<Map<String, String>>) payload.get("conditions");
        int page = payload.get("page") != null ? Integer.parseInt(String.valueOf(payload.get("page"))) : 1;
        int size = payload.get("size") != null ? Integer.parseInt(String.valueOf(payload.get("size"))) : 50;
        int offset = (page - 1) * size;
        validateIdentifiers(schema, tableName);
        long total = dbMapper.countByConditions(schema, tableName, conditions, logic);
        List<Map<String, Object>> list = processResultList(dbMapper.queryByConditionsPage(schema, tableName, conditions, logic, size, offset));
        Map<String, Object> res = new HashMap<>();
        res.put("total", total);
        res.put("list", list);
        res.put("isView", isViewObject(schema, tableName));
        return Result.success(res);
    }

    @Override
    public Result<Object> executeSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) return Result.error("SQL不能为空");
        if (sql.trim().toUpperCase().startsWith("SELECT") || sql.trim().toUpperCase().startsWith("WITH")) {
            return Result.success(processResultList(dbMapper.runQuery(sql)));
        }
        dbMapper.executeSql(sql);
        return Result.success("执行成功");
    }

    // ==========================================
    // 【修复 & 增强】数据操作方法
    // ==========================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Object> saveData(String schema, String tableName, Map<String, Object> row) {
        validateIdentifiers(schema, tableName);
        try {
            Object internalId = row.get("DB_INTERNAL_ID");

            if (internalId != null && !internalId.toString().isEmpty()) {
                // Update
                Map<String, Object> data = new HashMap<>(row);
                data.remove("DB_INTERNAL_ID");
                if (data.isEmpty()) return Result.success("无数据变更");
                dbMapper.updateByRowId(schema, tableName, internalId.toString(), data);
            } else {
                // Insert
                if (row.containsKey("DB_INTERNAL_ID")) row.remove("DB_INTERNAL_ID");
                dbMapper.insertData(schema, tableName, row);
            }
            return Result.success("保存成功");
        } catch (Exception e) {
            log.error("Save data failed for {}.{}", schema, tableName, e);

            // 尝试获取旧主键 (Update 场景)
            Object pkVal = null;
            try {
                String pkCol = dbMapper.getPkColumn(schema, tableName);
                if (pkCol != null) {
                    Object rowId = row.get("DB_INTERNAL_ID");
                    if (rowId != null) {
                        Map<String, Object> oldRow = dbMapper.getDataByRowId(schema, tableName, rowId.toString());
                        if (oldRow != null) pkVal = oldRow.get(pkCol);
                    } else {
                        // Insert 场景：没有旧数据，只能拿新数据 (此时 Scene A 不会触发，只会触发 Scene B)
                        pkVal = row.get(pkCol);
                    }
                }
            } catch (Exception ex) { /* ignore */ }

            return analyzeConflict(e, schema, tableName, pkVal, row);
        }
    }

    // 【核心修复】批量保存 + 冲突聚合 + 安全处理
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Object> saveBatch(String schema, String tableName, Map<String, List<Map<String, Object>>> payload) {
        validateIdentifiers(schema, tableName);
        List<Map<String, Object>> insertList = payload.get("insertList");
        List<Map<String, Object>> updateList = payload.get("updateList");

        Map<String, Map<String, Object>> aggregatedConflicts = new HashMap<>();
        boolean hasException = false;
        String exceptionMsg = "";

        try {
            if (insertList != null) {
                for (Map<String, Object> row : insertList) {
                    if (row.containsKey("DB_INTERNAL_ID")) row.remove("DB_INTERNAL_ID");
                    dbMapper.insertData(schema, tableName, row);
                }
            }
            if (updateList != null) {
                for (Map<String, Object> row : updateList) {
                    String rowId = (String) row.get("DB_INTERNAL_ID");
                    if (rowId == null) continue;
                    Map<String, Object> data = new HashMap<>(row);
                    data.remove("DB_INTERNAL_ID");
                    dbMapper.updateByRowId(schema, tableName, rowId, data);
                }
            }
            return Result.success("批量保存成功");
        } catch (Exception e) {
            log.error("Batch save failed for {}.{}", schema, tableName, e);
            hasException = true;
            exceptionMsg = e.getMessage();

            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

            // 2. 遍历所有行进行聚合分析
            List<Map<String, Object>> allRows = new ArrayList<>();
            if (insertList != null) allRows.addAll(insertList);
            if (updateList != null) allRows.addAll(updateList);

            for (Map<String, Object> row : allRows) {
                if (row == null) continue; // 防御

                Object pkVal = null;
                // 尝试获取旧主键 (Update 场景)
                if (row.containsKey("DB_INTERNAL_ID")) {
                    try {
                        String pkCol = dbMapper.getPkColumn(schema, tableName);
                        if (pkCol != null) {
                            Map<String, Object> oldRow = dbMapper.getDataByRowId(schema, tableName, row.get("DB_INTERNAL_ID").toString());
                            if (oldRow != null) pkVal = oldRow.get(pkCol);
                        }
                    } catch (Exception ex) {}
                } else {
                    // Insert 场景：尝试获取用户填写的 PK (虽然 Scene A 不会触发，但逻辑统一)
                    try {
                        String pkCol = dbMapper.getPkColumn(schema, tableName);
                        if (pkCol != null) pkVal = row.get(pkCol);
                    } catch (Exception ex) {}
                }

                // 调用单行分析 (传入 null exception 强制检查)
                Result<Object> res = analyzeConflict(null, schema, tableName, pkVal, row);

                if (res.getCode() == 503 && res.getData() != null) {
                    List<Map<String, Object>> rowConflicts = (List<Map<String, Object>>) res.getData();
                    for (Map<String, Object> c : rowConflicts) {
                        String key = c.get("TABLE_NAME") + "|" + c.get("COLUMN_NAME");
                        aggregatedConflicts.putIfAbsent(key, new HashMap<>());
                        Map<String, Object> agg = aggregatedConflicts.get(key);

                        agg.put("TABLE_NAME", c.get("TABLE_NAME"));
                        agg.put("COLUMN_NAME", c.get("COLUMN_NAME"));
                        Object cntVal = c.get("CNT");
                        if ("MISSING".equals(cntVal)) {
                            agg.put("CNT", "MISSING");
                        } else {
                            agg.put("CNT", (Integer) agg.getOrDefault("CNT", 0) + (Integer) cntVal);
                        }

                        List<Object> valList = (List<Object>) agg.getOrDefault("MY_VAL_LIST", new ArrayList<>());
                        valList.add(c.get("MY_VAL"));
                        agg.put("MY_VAL_LIST", valList);
                    }
                }
            }
        }

        if (hasException) {
            if (!aggregatedConflicts.isEmpty()) {
                Result<Object> r = new Result<>();
                r.setCode(503);
                r.setMsg("批量保存失败：存在数据完整性冲突");
                r.setData(new ArrayList<>(aggregatedConflicts.values()));
                return r;
            }
            throw new RuntimeException("批量保存失败: " + exceptionMsg);
        }

        return Result.success("批量保存成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Object> deleteData(String schema, String tableName, Object internalId, String pkValue) {
        validateIdentifiers(schema, tableName);
        try {
            if (internalId == null || internalId.toString().trim().isEmpty()) {
                return Result.error("删除失败：无法获取行唯一标识 (ROWID)");
            }
            dbMapper.deleteByRowId(schema, tableName, internalId.toString());
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("Delete failed", e);
            // 删除时前端传来的 pkValue 已经是旧值，直接用
            return analyzeConflict(e, schema, tableName, pkValue, null);
        }
    }

    // 【核心新增】批量删除 + 全量冲突分析
    // 【核心修复】批量删除 + 全量冲突聚合
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Object> deleteBatch(String schema, String tableName, List<String> rowIds) {
        validateIdentifiers(schema, tableName);

        // 聚合 Map：Key = "TableName|ColumnName"
        Map<String, Map<String, Object>> aggregatedConflicts = new HashMap<>();

        boolean hasException = false;
        String exceptionMsg = "";
        int failIndex = -1;

        // 1. 尝试执行删除
        for (int i = 0; i < rowIds.size(); i++) {
            try {
                dbMapper.deleteByRowId(schema, tableName, rowIds.get(i));
            } catch (Exception e) {
                hasException = true;
                exceptionMsg = e.getMessage();
                failIndex = i;
                break;
            }
        }

        // 2. 如果失败，回滚并开始聚合分析
        if (hasException) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

            try {
                String pkCol = dbMapper.getPkColumn(schema, tableName);
                List<Map<String, String>> childTables = dbMapper.getAllChildTables(schema, tableName);

                if (pkCol != null && childTables != null && !childTables.isEmpty()) {
                    // 遍历所有可能的失败行（从 failIndex 开始）
                    for (int i = failIndex; i < rowIds.size(); i++) {
                        String id = rowIds.get(i);
                        try {
                            // 查询该行数据（虽然回滚，但数据本身还在）
                            Map<String, Object> row = dbMapper.getDataByRowId(schema, tableName, id);
                            if (row == null) continue;
                            Object pkVal = row.get(pkCol);

                            // 检查每个子表
                            for (Map<String, String> child : childTables) {
                                String cTable = child.get("TABLE_NAME");
                                String cCol = child.get("COLUMN_NAME");
                                int count = dbMapper.countReference(schema, cTable, cCol, pkVal);
                                if (count > 0) {
                                    String key = cTable + "|" + cCol;
                                    aggregatedConflicts.putIfAbsent(key, new HashMap<>());
                                    Map<String, Object> agg = aggregatedConflicts.get(key);

                                    // 初始化或更新聚合信息
                                    agg.put("TABLE_NAME", cTable);
                                    agg.put("COLUMN_NAME", cCol);
                                    agg.put("CNT", (Integer) agg.getOrDefault("CNT", 0) + count); // 累加冲突数

                                    // 收集冲突值列表
                                    List<Object> valList = (List<Object>) agg.getOrDefault("MY_VAL_LIST", new ArrayList<>());
                                    valList.add(pkVal);
                                    agg.put("MY_VAL_LIST", valList);
                                }
                            }
                        } catch (Exception ex) { /* ignore single row check error */ }
                    }
                }
            } catch (Exception ex) {
                log.error("Batch delete conflict analysis error", ex);
            }

            if (!aggregatedConflicts.isEmpty()) {
                Result<Object> r = new Result<>();
                r.setCode(503);
                r.setMsg("批量删除失败：检测到关联引用");
                r.setData(new ArrayList<>(aggregatedConflicts.values()));
                return r;
            }

            throw new RuntimeException("批量删除失败: " + exceptionMsg);
        }

        return Result.success("批量删除成功");
    }

    // ... (后续方法保持不变：saveCascade, getErData, getTableDDL, executeBatchSql, getIndexes, getForeignKeys, 角色管理等) ...
    @Override
    public Result<String> saveCascade(String schema, String tableName, Map<String, Object> payload) {
        return Result.error("暂不支持");
    }

    @Override
    public Result<Map<String, Object>> getErData(String schema, String tableName, boolean showAll, List<String> expandedTables) {
        validateIdentifiers(schema, tableName);
        List<Map<String, Object>> relations = dbMapper.getTableRelations(schema, tableName);
        Set<String> tableSet = new HashSet<>();
        tableSet.add(tableName);
        for (Map<String, Object> r : relations) {
            if (r.get("SOURCE_TABLE") != null) tableSet.add((String) r.get("SOURCE_TABLE"));
            if (r.get("TARGET_TABLE") != null) tableSet.add((String) r.get("TARGET_TABLE"));
        }
        List<String> tableList = new ArrayList<>(tableSet);
        List<Map<String, Object>> columns = tableList.isEmpty() ? new ArrayList<>() : dbMapper.getColumnsForTables(schema, tableList);
        List<Map<String, Object>> comments = tableList.isEmpty() ? new ArrayList<>() : dbMapper.getTableComments(schema, tableList);
        Map<String, Object> graph = buildGraphStructure(tableName, tableSet, relations, columns, comments, showAll, expandedTables);
        return Result.success(graph);
    }

    @Override
    public Result<String> getTableDDL(String schema, String tableName) {
        validateIdentifiers(schema, tableName);
        try {
            String type = isViewObject(schema, tableName) ? "VIEW" : "TABLE";
            return Result.success(clobToString(dbMapper.getDDL(schema, tableName, type)));
        } catch (Exception e) {
            return Result.error("获取DDL失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Object> executeBatchSql(List<String> sqlList) {
        try {
            for (String sql : sqlList) if (sql != null && !sql.trim().isEmpty()) dbMapper.executeSql(sql);
            return Result.success("执行成功");
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public Result<List<Map<String, Object>>> getIndexes(String schema, String tableName) {
        validateIdentifiers(schema, tableName);
        return Result.success(processResultList(dbMapper.getIndexes(schema, tableName)));
    }

    @Override
    public Result<List<Map<String, Object>>> getForeignKeys(String schema, String tableName) {
        validateIdentifiers(schema, tableName);
        return Result.success(processResultList(dbMapper.getForeignKeys(schema, tableName)));
    }

    // ================== 【角色管理】 ==================
    @Override
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
                if (upperName.equals("AUDITOR") || upperName.equals("SECADMIN") || upperName.equals("OPERATOR"))
                    return false;
                return true;
            }).collect(Collectors.toList());
            return Result.success(filteredRoles);
        } catch (Exception e) {
            return Result.error("获取角色失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Object> createRole(String roleName) {
        validateIdentifiers(roleName);
        try {
            dbMapper.executeSql("CREATE ROLE \"" + roleName + "\"");
            return Result.success("创建成功");
        } catch (Exception e) {
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    @Override
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

    @Override
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

    @Override
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
                    dbMapper.executeSql(sql);
                } catch (Exception e) {
                    log.error("GRANT ROLE failed: " + sql, e);
                    errorMsgs.add("授予角色[" + targetRole + "]失败: " + e.getMessage());
                }
            }
        }
        if (!errorMsgs.isEmpty()) return Result.error("部分角色授权失败:\n" + String.join("\n", errorMsgs));
        return Result.success("更新成功");
    }

    @Override
    public Result<Object> updateRoleSysPrivs(String roleName, List<Map<String, Object>> changes) {
        validateIdentifiers(roleName);
        List<String> errorMsgs = new ArrayList<>();
        for (Map<String, Object> change : changes) {
            String priv = (String) change.get("priv");
            String action = (String) change.get("action");
            boolean admin = Boolean.parseBoolean(String.valueOf(change.get("admin")));

            // 权限名称校验正则
            if (!priv.matches("^[A-Z_\\s]+$")) continue;

            // 【新增】直接过滤掉这两个有问题的权限
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
                try {
                    dbMapper.executeSql(sql);
                } catch (Exception e) {
                    log.error("GRANT SYS PRIV failed: " + sql, e);
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("授权者没有此授权权限")) {
                        msg = "无权授予系统权限: [" + priv + "]";
                    } else if (msg != null && msg.contains("语法分析出错")) {
                        msg = "语法错误: [" + priv + "]";
                    } else {
                        msg = "失败 [" + priv + "]: " + (msg != null && msg.contains("\n") ? msg.split("\n")[0] : msg);
                    }
                    errorMsgs.add(msg);
                }
            }
        }
        if (!errorMsgs.isEmpty()) return Result.error("部分权限更新失败 (这通常是因为当前账号权限不足):\n" + String.join("\n", errorMsgs));
        return Result.success("更新成功");
    }

    @Override
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
            if ("REVOKE".equals(action)) {
                executeSqlQuietly(revokeSql);
            } else if ("GRANT".equals(action)) {
                executeSqlQuietly(revokeSql);
                String grantSql = String.format("GRANT %s ON %s.%s TO %s", priv, qSchema, qTable, qRole);
                if (grantOption) grantSql += " WITH GRANT OPTION";
                try {
                    dbMapper.executeSql(grantSql);
                } catch (Exception e) {
                    log.error("GRANT OBJ PRIV failed: " + grantSql, e);
                    errorMsgs.add("对象权限[" + priv + "]更新失败: " + e.getMessage().split("\n")[0]);
                }
            }
        }
        if (!errorMsgs.isEmpty()) return Result.error("部分对象权限更新失败:\n" + String.join("\n", errorMsgs));
        return Result.success("更新成功");
    }
}