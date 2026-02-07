package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.service.base.AbstractDbService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.*;

@Service
public class TableDataServiceImpl extends AbstractDbService {

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
        if (isView) res.put("isSimpleView", analyzeViewComplexity(schema, tableName));
        return Result.success(res);
    }

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

    @Transactional(rollbackFor = Exception.class)
    public Result<Object> saveData(String schema, String tableName, Map<String, Object> row) {
        validateIdentifiers(schema, tableName);
        try {
            Object internalId = row.get("DB_INTERNAL_ID");
            if (internalId != null && !internalId.toString().isEmpty()) {
                Map<String, Object> data = new HashMap<>(row);
                data.remove("DB_INTERNAL_ID");
                if (data.isEmpty()) return Result.success("无数据变更");
                dbMapper.updateByRowId(schema, tableName, internalId.toString(), data);
            } else {
                if (row.containsKey("DB_INTERNAL_ID")) row.remove("DB_INTERNAL_ID");
                dbMapper.insertData(schema, tableName, row);
            }
            return Result.success("保存成功");
        } catch (Exception e) {
            log.error("Save data failed", e);
            Object pkVal = null;
            try {
                String pkCol = dbMapper.getPkColumn(schema, tableName);
                if (pkCol != null) {
                    Object rowId = row.get("DB_INTERNAL_ID");
                    if (rowId != null) {
                        Map<String, Object> oldRow = dbMapper.getDataByRowId(schema, tableName, rowId.toString());
                        if (oldRow != null) pkVal = oldRow.get(pkCol);
                    } else pkVal = row.get(pkCol);
                }
            } catch (Exception ex) { /* ignore */ }
            return analyzeConflict(e, schema, tableName, pkVal, row);
        }
    }

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
            log.error("Batch save failed", e);
            hasException = true;
            exceptionMsg = e.getMessage();
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

            List<Map<String, Object>> allRows = new ArrayList<>();
            if (insertList != null) allRows.addAll(insertList);
            if (updateList != null) allRows.addAll(updateList);

            for (Map<String, Object> row : allRows) {
                if (row == null) continue;
                Object pkVal = null;
                if (row.containsKey("DB_INTERNAL_ID")) {
                    try {
                        String pkCol = dbMapper.getPkColumn(schema, tableName);
                        if (pkCol != null) {
                            Map<String, Object> oldRow = dbMapper.getDataByRowId(schema, tableName, row.get("DB_INTERNAL_ID").toString());
                            if (oldRow != null) pkVal = oldRow.get(pkCol);
                        }
                    } catch (Exception ex) {}
                } else {
                    try {
                        String pkCol = dbMapper.getPkColumn(schema, tableName);
                        if (pkCol != null) pkVal = row.get(pkCol);
                    } catch (Exception ex) {}
                }

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
                        if ("MISSING".equals(cntVal)) agg.put("CNT", "MISSING");
                        else agg.put("CNT", (Integer) agg.getOrDefault("CNT", 0) + (Integer) cntVal);
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
            return analyzeConflict(e, schema, tableName, pkValue, null);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Object> deleteBatch(String schema, String tableName, List<String> rowIds) {
        validateIdentifiers(schema, tableName);
        Map<String, Map<String, Object>> aggregatedConflicts = new HashMap<>();
        boolean hasException = false;
        String exceptionMsg = "";
        int failIndex = -1;

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

        if (hasException) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            try {
                String pkCol = dbMapper.getPkColumn(schema, tableName);
                List<Map<String, String>> childTables = dbMapper.getAllChildTables(schema, tableName);
                if (pkCol != null && childTables != null && !childTables.isEmpty()) {
                    for (int i = failIndex; i < rowIds.size(); i++) {
                        String id = rowIds.get(i);
                        try {
                            Map<String, Object> row = dbMapper.getDataByRowId(schema, tableName, id);
                            if (row == null) continue;
                            Object pkVal = row.get(pkCol);
                            for (Map<String, String> child : childTables) {
                                String cTable = child.get("TABLE_NAME");
                                String cCol = child.get("COLUMN_NAME");
                                int count = dbMapper.countReference(schema, cTable, cCol, pkVal);
                                if (count > 0) {
                                    String key = cTable + "|" + cCol;
                                    aggregatedConflicts.putIfAbsent(key, new HashMap<>());
                                    Map<String, Object> agg = aggregatedConflicts.get(key);
                                    agg.put("TABLE_NAME", cTable);
                                    agg.put("COLUMN_NAME", cCol);
                                    agg.put("CNT", (Integer) agg.getOrDefault("CNT", 0) + count);
                                    List<Object> valList = (List<Object>) agg.getOrDefault("MY_VAL_LIST", new ArrayList<>());
                                    valList.add(pkVal);
                                    agg.put("MY_VAL_LIST", valList);
                                }
                            }
                        } catch (Exception ex) { }
                    }
                }
            } catch (Exception ex) { log.error("Batch delete conflict analysis error", ex); }

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
}