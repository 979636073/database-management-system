package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.config.DynamicContext;
import com.example.dmdb.mapper.MetadataMapper; // 假设你有这个 Mapper
import com.example.dmdb.mapper.SqlMapper;
import com.example.dmdb.service.base.AbstractDbService;
// 如果实现了 MetadataService 接口，请加上 implements MetadataService
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Clob;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MetadataServiceImpl extends AbstractDbService {

    private static final Logger log = LoggerFactory.getLogger(MetadataServiceImpl.class);

    @Autowired
    private MetadataMapper metadataMapper;

    @Autowired
    private SqlMapper sqlMapper; // 【新增】用于执行会话配置参数

    public Result<List<String>> getSchemas() {
        return Result.success(metadataMapper.getAllSchemas());
    }

    public Result<List<Map<String, String>>> getTables(String schema) {
        validateIdentifiers(schema);
        return Result.success(metadataMapper.getTablesBySchema(schema));
    }

    public Result<List<Map<String, Object>>> getColumns(String schema, String tableName) {
        validateIdentifiers(schema, tableName);
        return Result.success(metadataMapper.getColumns(schema, tableName));
    }

    public Result<List<Map<String, Object>>> getViews(String schema) {
        validateIdentifiers(schema);
        // 【注意】这里配合 Mapper.xml 的修改，移除了 TEXT 字段，防止 Oracle 报错
        return Result.success(processResultList(metadataMapper.getViews(schema)));
    }

    public Result<List<Map<String, Object>>> getTriggers(String schema) {
        validateIdentifiers(schema);
        return Result.success(processResultList(metadataMapper.getTriggers(schema)));
    }

    // 【核心修改】getTableDDL
    // 加上 @Transactional 确保 setupSql 和 getDDL 在同一个数据库连接中执行，否则配置不生效
    @Transactional(readOnly = true)
    public Result<String> getTableDDL(String schema, String tableName) {
        validateIdentifiers(schema, tableName);
        try {
            String type = isViewObject(schema, tableName) ? "VIEW" : "TABLE";
            boolean isOracle = "ORACLE".equalsIgnoreCase(DynamicContext.getCurrentDbType());

            // 1. 如果是 Oracle，先配置 DBMS_METADATA 参数，使其输出为“脚本”样式
            if (isOracle) {
                try {
                    // 开启美化、添加分号、将约束拆分为 ALTER 语句
                    String setupSql = "BEGIN " +
                            "DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM, 'SQLTERMINATOR', TRUE); " +
                            "DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM, 'PRETTY', TRUE); " +
                            "DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM, 'CONSTRAINTS_AS_ALTER', TRUE); " +
                            "END;";
                    sqlMapper.executeSql(setupSql);
                } catch (Exception e) {
                    log.warn("设置 Oracle DDL 格式参数失败，将使用默认格式: {}", e.getMessage());
                }
            }

            // 2. 获取基础 DDL
            Object ddlObj = metadataMapper.getDDL(schema, tableName, type);
            String ddl = clobToString(ddlObj);

            StringBuilder sb = new StringBuilder();

            // 3. 手动拼接 DROP 语句 (Oracle GET_DDL 不会生成 DROP)
            if ("TABLE".equalsIgnoreCase(type)) {
                // DROP TABLE schema.table CASCADE CONSTRAINTS;
                sb.append(String.format("DROP TABLE \"%s\".\"%s\" CASCADE CONSTRAINTS;\n\n", schema, tableName));
            } else if ("VIEW".equalsIgnoreCase(type)) {
                sb.append(String.format("DROP VIEW \"%s\".\"%s\";\n\n", schema, tableName));
            }

            sb.append(ddl);

            // 4. 如果是 TABLE 类型，在 Java 层手动拼接注释 (保持原有的注释逻辑)
            if ("TABLE".equalsIgnoreCase(type)) {
                // 4.1 拼接表注释
                try {
                    List<Map<String, Object>> tabComments = metadataMapper.getTableComments(schema, Collections.singletonList(tableName));
                    if (tabComments != null && !tabComments.isEmpty()) {
                        String comment = (String) tabComments.get(0).get("COMMENTS");
                        if (comment != null && !comment.isEmpty()) {
                            String safeComment = comment.replace("'", "''");
                            sb.append(String.format("\nCOMMENT ON TABLE \"%s\".\"%s\" IS '%s';", schema, tableName, safeComment));
                        }
                    }
                } catch (Exception ignored) {}

                // 4.2 拼接列注释
                try {
                    List<Map<String, Object>> columns = metadataMapper.getColumns(schema, tableName);
                    if (columns != null) {
                        for (Map<String, Object> col : columns) {
                            String colName = (String) col.get("COLUMN_NAME");
                            String colComment = (String) col.get("COMMENTS");
                            if (colComment != null && !colComment.isEmpty()) {
                                String safeComment = colComment.replace("'", "''");
                                sb.append(String.format("\nCOMMENT ON COLUMN \"%s\".\"%s\".\"%s\" IS '%s';",
                                        schema, tableName, colName, safeComment));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 5. (可选) 清理配置，恢复默认 (虽然连接关闭后会自动重置，但在连接池环境下是个好习惯)
            if (isOracle) {
                try {
                    sqlMapper.executeSql("BEGIN DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM, 'DEFAULT'); END;");
                } catch (Exception ignored) {}
            }

            return Result.success(sb.toString());
        } catch (Exception e) {
            log.error("获取DDL失败", e);
            return Result.error("获取DDL失败: " + e.getMessage());
        }
    }
    public Result<List<Map<String, Object>>> getIndexes(String schema, String tableName) {
        validateIdentifiers(schema, tableName);
        return Result.success(processResultList(metadataMapper.getIndexes(schema, tableName)));
    }

    public Result<List<Map<String, Object>>> getForeignKeys(String schema, String tableName) {
        validateIdentifiers(schema, tableName);
        return Result.success(processResultList(metadataMapper.getForeignKeys(schema, tableName)));
    }

    // 【核心优化】getErData
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

        // 1. 获取所有列 (此时 IS_PK 全部为 0)
        List<Map<String, Object>> columns = tableList.isEmpty() ? new ArrayList<>() : metadataMapper.getColumnsForTables(schema, tableList);

        // 2. 【新增】批量获取这些表的主键信息 (单次查询，极快)
        if (!tableList.isEmpty()) {
            List<Map<String, Object>> pkCols = metadataMapper.getPkColumnsForTables(schema, tableList);

            // 3. 构建主键快速查找 Set: "TABLE_NAME.COLUMN_NAME"
            Set<String> pkSet = new HashSet<>();
            for (Map<String, Object> pk : pkCols) {
                pkSet.add(pk.get("TABLE_NAME") + "." + pk.get("COLUMN_NAME"));
            }

            // 4. 在内存中回填 IS_PK
            for (Map<String, Object> col : columns) {
                String key = col.get("TABLE_NAME") + "." + col.get("COLUMN_NAME");
                if (pkSet.contains(key)) {
                    col.put("IS_PK", 1); // 标记为主键
                }
            }
        }

        List<Map<String, Object>> comments = tableList.isEmpty() ? new ArrayList<>() : metadataMapper.getTableComments(schema, tableList);

        Map<String, Object> graph = buildGraphStructure(tableName, tableSet, relations, columns, comments, showAll, expandedTables);
        return Result.success(graph);
    }

    private Map<String, Object> buildGraphStructure(String centerTable, Set<String> tables,
                                                    List<Map<String, Object>> relations,
                                                    List<Map<String, Object>> columns,
                                                    List<Map<String, Object>> comments,
                                                    boolean showAll,
                                                    List<String> expandedTables) {
        Map<String, String> commentMap = new HashMap<>();
        for (Map<String, Object> c : comments) {
            commentMap.put((String) c.get("TABLE_NAME"), (String) c.get("COMMENTS"));
        }
        Map<String, List<Map<String, Object>>> colMap = new HashMap<>();
        for (Map<String, Object> c : columns) {
            colMap.computeIfAbsent((String) c.get("TABLE_NAME"), k -> new ArrayList<>()).add(c);
        }
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
            } else {
                n.put("columns", new ArrayList<>());
            }
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

    // 辅助方法：确保 Clob 正确转换 (如果父类已有可省略)
    public String clobToString(Object obj) {
        if (obj == null) return "";
        try {
            if (obj instanceof Clob) {
                Clob clob = (Clob) obj;
                return clob.getSubString(1, (int) clob.length());
            }
            return obj.toString();
        } catch (Exception e) {
            return "";
        }
    }
}