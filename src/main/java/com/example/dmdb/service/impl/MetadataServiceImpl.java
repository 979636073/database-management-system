package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.service.base.AbstractDbService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MetadataServiceImpl extends AbstractDbService {

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
        return Result.success(processResultList(metadataMapper.getViews(schema)));
    }

    public Result<List<Map<String, Object>>> getTriggers(String schema) {
        validateIdentifiers(schema);
        return Result.success(processResultList(metadataMapper.getTriggers(schema)));
    }

    public Result<String> getTableDDL(String schema, String tableName) {
        validateIdentifiers(schema, tableName);
        try {
            String type = isViewObject(schema, tableName) ? "VIEW" : "TABLE";
            return Result.success(clobToString(metadataMapper.getDDL(schema, tableName, type)));
        } catch (Exception e) {
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

    // 【补全】获取ER图数据
    public Result<Map<String, Object>> getErData(String schema, String tableName, boolean showAll, List<String> expandedTables) {
        validateIdentifiers(schema, tableName);

        // 1. 获取表关系
        List<Map<String, Object>> relations = metadataMapper.getTableRelations(schema, tableName);

        // 2. 收集所有相关表名
        Set<String> tableSet = new HashSet<>();
        tableSet.add(tableName);
        for (Map<String, Object> r : relations) {
            if (r.get("SOURCE_TABLE") != null) tableSet.add((String) r.get("SOURCE_TABLE"));
            if (r.get("TARGET_TABLE") != null) tableSet.add((String) r.get("TARGET_TABLE"));
        }

        // 3. 获取所有相关表的列信息和注释
        List<String> tableList = new ArrayList<>(tableSet);
        List<Map<String, Object>> columns = tableList.isEmpty() ? new ArrayList<>() : metadataMapper.getColumnsForTables(schema, tableList);
        List<Map<String, Object>> comments = tableList.isEmpty() ? new ArrayList<>() : metadataMapper.getTableComments(schema, tableList);

        // 4. 构建图结构
        Map<String, Object> graph = buildGraphStructure(tableName, tableSet, relations, columns, comments, showAll, expandedTables);
        return Result.success(graph);
    }

    // 【补全】构建图结构的私有辅助方法
    private Map<String, Object> buildGraphStructure(String centerTable, Set<String> tables,
                                                    List<Map<String, Object>> relations,
                                                    List<Map<String, Object>> columns,
                                                    List<Map<String, Object>> comments,
                                                    boolean showAll,
                                                    List<String> expandedTables) {

        // 处理表注释 Map
        Map<String, String> commentMap = new HashMap<>();
        for (Map<String, Object> c : comments) {
            commentMap.put((String) c.get("TABLE_NAME"), (String) c.get("COMMENTS"));
        }

        // 处理列信息 Map
        Map<String, List<Map<String, Object>>> colMap = new HashMap<>();
        for (Map<String, Object> c : columns) {
            colMap.computeIfAbsent((String) c.get("TABLE_NAME"), k -> new ArrayList<>()).add(c);
        }

        List<Map<String, Object>> nodes = new ArrayList<>();

        // 构建节点 (表)
        for (String t : tables) {
            Map<String, Object> n = new HashMap<>();
            n.put("id", t);
            n.put("label", t);
            n.put("tableComment", commentMap.getOrDefault(t, ""));
            n.put("isCenter", t.equals(centerTable));

            List<Map<String, Object>> rawCols = colMap.get(t);
            if (rawCols != null) {
                // 处理 Clob 等类型
                processResultList(rawCols);

                int totalCount = rawCols.size();
                n.put("totalColCount", totalCount);

                boolean isExpanded = showAll || (expandedTables != null && expandedTables.contains(t));

                // 如果列数过多且未展开，只显示前10列
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

        // 构建边 (关系)
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
}