package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.service.base.AbstractDbService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MetadataServiceImpl extends AbstractDbService {

    public Result<List<String>> getSchemas() { return Result.success(dbMapper.getAllSchemas()); }

    public Result<List<Map<String, String>>> getTables(String schema) {
        validateIdentifiers(schema);
        return Result.success(dbMapper.getTablesBySchema(schema));
    }

    public Result<List<Map<String, Object>>> getColumns(String schema, String tableName) {
        validateIdentifiers(schema, tableName);
        return Result.success(dbMapper.getColumns(schema, tableName));
    }

    public Result<List<Map<String, Object>>> getViews(String schema) {
        validateIdentifiers(schema);
        return Result.success(processResultList(dbMapper.getViews(schema)));
    }

    public Result<List<Map<String, Object>>> getTriggers(String schema) {
        validateIdentifiers(schema);
        return Result.success(processResultList(dbMapper.getTriggers(schema)));
    }

    public Result<String> getTableDDL(String schema, String tableName) {
        validateIdentifiers(schema, tableName);
        try {
            String type = isViewObject(schema, tableName) ? "VIEW" : "TABLE";
            return Result.success(clobToString(dbMapper.getDDL(schema, tableName, type)));
        } catch (Exception e) { return Result.error("获取DDL失败: " + e.getMessage()); }
    }

    public Result<List<Map<String, Object>>> getIndexes(String schema, String tableName) {
        validateIdentifiers(schema, tableName);
        // 使用 XML 中修复后的 getIndexes，自动过滤系统索引
        return Result.success(processResultList(dbMapper.getIndexes(schema, tableName)));
    }

    public Result<List<Map<String, Object>>> getForeignKeys(String schema, String tableName) {
        validateIdentifiers(schema, tableName);
        return Result.success(processResultList(dbMapper.getForeignKeys(schema, tableName)));
    }

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

        Map<String, String> commentMap = new HashMap<>();
        for (Map<String, Object> c : comments) commentMap.put((String) c.get("TABLE_NAME"), (String) c.get("COMMENTS"));

        Map<String, List<Map<String, Object>>> colMap = new HashMap<>();
        for (Map<String, Object> c : columns) colMap.computeIfAbsent((String) c.get("TABLE_NAME"), k -> new ArrayList<>()).add(c);

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
}