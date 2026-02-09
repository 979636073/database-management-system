package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.config.DynamicContext;
import com.example.dmdb.service.ConnectionManager;
import com.example.dmdb.service.base.AbstractDbService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

@Service
public class TableDataServiceImpl extends AbstractDbService {

    public Result<Map<String, Object>> getData(String schema, String tableName, int page, int size) {
        validateIdentifiers(schema, tableName);
        int offset = (page - 1) * size;
        long total = tableDataMapper.countData(schema, tableName);
        // 获取原始数据
        List<Map<String, Object>> list = processResultList(tableDataMapper.getDataPage(schema, tableName, size, offset));

        // 【新增】处理 LOB 字段，避免列表页加载过大内容
        maskLobFields(list);

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

    public Result<Map<String, Object>> filterData(Map<String, Object> payload) {
        String schema = (String) payload.get("schema");
        String tableName = (String) payload.get("tableName");
        String logic = (String) payload.get("logic");
        List<Map<String, String>> conditions = (List<Map<String, String>>) payload.get("conditions");

        int page = payload.get("page") != null ? Integer.parseInt(String.valueOf(payload.get("page"))) : 1;
        int size = payload.get("size") != null ? Integer.parseInt(String.valueOf(payload.get("size"))) : 50;
        int offset = (page - 1) * size;

        validateIdentifiers(schema, tableName);
        long total = tableDataMapper.countByConditions(schema, tableName, conditions, logic);
        // 获取原始数据
        List<Map<String, Object>> list = processResultList(tableDataMapper.queryByConditionsPage(schema, tableName, conditions, logic, size, offset));

        // 【新增】处理 LOB 字段
        maskLobFields(list);

        Map<String, Object> res = new HashMap<>();
        res.put("total", total);
        res.put("list", list);
        res.put("isView", isViewObject(schema, tableName));
        return Result.success(res);
    }

    // --------------------------------------------------------------------------------
    // 【核心修改】新增/修改保存逻辑，支持 Base64 -> BLOB 自动转换
    // --------------------------------------------------------------------------------

    /**
     * 单条数据保存 (新增/更新)
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Object> saveData(String schema, String tableName, Map<String, Object> row) {
        validateIdentifiers(schema, tableName);
        try {
            // 【修改】预处理 LOB 数据 (Base64 -> byte[])
            preprocessLobData(schema, tableName, Collections.singletonList(row));

            Object internalId = row.get("DB_INTERNAL_ID");
            if (internalId != null && !internalId.toString().isEmpty()) {
                Map<String, Object> data = new HashMap<>(row);
                data.remove("DB_INTERNAL_ID");
                if (data.isEmpty()) return Result.success("无数据变更");
                tableDataMapper.updateByRowId(schema, tableName, internalId.toString(), data);
            } else {
                if (row.containsKey("DB_INTERNAL_ID")) row.remove("DB_INTERNAL_ID");
                tableDataMapper.insertData(schema, tableName, row);
            }
            return Result.success("保存成功");
        } catch (Exception e) {
            log.error("Save data failed", e);
            Object pkVal = null;
            try {
                String pkCol = metadataMapper.getPkColumn(schema, tableName);
                if (pkCol != null) {
                    Object rowId = row.get("DB_INTERNAL_ID");
                    if (rowId != null) {
                        Map<String, Object> oldRow = tableDataMapper.getDataByRowId(schema, tableName, rowId.toString());
                        if (oldRow != null) pkVal = oldRow.get(pkCol);
                    } else pkVal = row.get(pkCol);
                }
            } catch (Exception ex) { /* ignore */ }
            return analyzeConflict(e, schema, tableName, pkVal, row);
        }
    }

    /**
     * 批量保存 (新增/更新)
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Object> saveBatch(String schema, String tableName, Map<String, List<Map<String, Object>>> payload) {
        validateIdentifiers(schema, tableName);
        List<Map<String, Object>> insertList = payload.get("insertList");
        List<Map<String, Object>> updateList = payload.get("updateList");

        // 【修改】预处理 LOB 数据
        List<Map<String, Object>> allRows = new ArrayList<>();
        if (insertList != null) allRows.addAll(insertList);
        if (updateList != null) allRows.addAll(updateList);
        if (!allRows.isEmpty()) {
            preprocessLobData(schema, tableName, allRows);
        }

        Map<String, Map<String, Object>> aggregatedConflicts = new HashMap<>();
        boolean hasException = false;
        String exceptionMsg = "";

        try {
            if (insertList != null) {
                for (Map<String, Object> row : insertList) {
                    if (row.containsKey("DB_INTERNAL_ID")) row.remove("DB_INTERNAL_ID");
                    tableDataMapper.insertData(schema, tableName, row);
                }
            }
            if (updateList != null) {
                for (Map<String, Object> row : updateList) {
                    String rowId = (String) row.get("DB_INTERNAL_ID");
                    if (rowId == null) continue;
                    Map<String, Object> data = new HashMap<>(row);
                    data.remove("DB_INTERNAL_ID");
                    tableDataMapper.updateByRowId(schema, tableName, rowId, data);
                }
            }
            return Result.success("批量保存成功");
        } catch (Exception e) {
            log.error("Batch save failed", e);
            hasException = true;
            exceptionMsg = e.getMessage();
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

            // 冲突分析逻辑 (保持不变)
            if (allRows.isEmpty() && insertList != null) allRows.addAll(insertList);
            if (allRows.isEmpty() && updateList != null) allRows.addAll(updateList);

            for (Map<String, Object> row : allRows) {
                if (row == null) continue;

                Object pkVal = null;
                if (row.containsKey("DB_INTERNAL_ID")) {
                    try {
                        String pkCol = metadataMapper.getPkColumn(schema, tableName);
                        if (pkCol != null) {
                            Map<String, Object> oldRow = tableDataMapper.getDataByRowId(schema, tableName, row.get("DB_INTERNAL_ID").toString());
                            if (oldRow != null) pkVal = oldRow.get(pkCol);
                        }
                    } catch (Exception ex) {}
                } else {
                    try {
                        String pkCol = metadataMapper.getPkColumn(schema, tableName);
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
                        Object currentCnt = agg.get("CNT");

                        if ("MISSING".equals(cntVal) || "MISSING".equals(currentCnt)) {
                            agg.put("CNT", "MISSING");
                        } else {
                            int existing = currentCnt != null ? (Integer) currentCnt : 0;
                            agg.put("CNT", existing + (Integer) cntVal);
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

    /**
     * 【新增】处理结果集中的 LOB 对象
     * 将 Blob/Clob 等对象转换为字符串占位符
     */
    private void maskLobFields(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return;
        for (Map<String, Object> row : list) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof Clob) {
                    entry.setValue("[CLOB 数据]");
                } else if (val instanceof Blob) {
                    entry.setValue("[BLOB 数据]");
                } else if (val instanceof byte[]) {
                    if (((byte[]) val).length > 100) {
                        entry.setValue("[BINARY 数据]");
                    }
                }
            }
        }
    }

    /**
     * 【新增】预处理 LOB 数据：将 Base64 字符串转换为 byte[]
     */
    private void preprocessLobData(String schema, String tableName, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return;

        // 获取列元数据，识别 BLOB 列
        List<Map<String, Object>> columns = metadataMapper.getColumns(schema, tableName);
        Set<String> blobColumns = new HashSet<>();

        for (Map<String, Object> col : columns) {
            String type = ((String) col.get("DATA_TYPE")).toUpperCase();
            if (type.contains("BLOB") || type.contains("IMAGE") || type.contains("BINARY") || type.contains("VARBINARY")) {
                blobColumns.add((String) col.get("COLUMN_NAME"));
            }
        }

        if (blobColumns.isEmpty()) return;

        for (Map<String, Object> row : rows) {
            for (String colName : blobColumns) {
                if (row.containsKey(colName)) {
                    Object val = row.get(colName);
                    // 只有当值是 String 类型且看似 Base64 时才转换
                    if (val instanceof String) {
                        String base64Str = (String) val;
                        // 占位符或空值不处理
                        if (base64Str.isEmpty() || "[BLOB 数据]".equals(base64Str) || "[BINARY 数据]".equals(base64Str)) {
                            if (row.containsKey("DB_INTERNAL_ID")) {
                                row.remove(colName); // 更新时不覆盖
                            } else {
                                row.put(colName, null); // 插入时置空
                            }
                        } else {
                            try {
                                // 处理 Data URI Scheme (e.g., "data:image/png;base64,AAAA...")
                                if (base64Str.contains(",")) {
                                    base64Str = base64Str.split(",")[1];
                                }
                                byte[] bytes = Base64.getDecoder().decode(base64Str);
                                row.put(colName, bytes); // 替换为 byte[]
                            } catch (IllegalArgumentException e) {
                                // 解析失败，可能是普通文本，忽略
                            }
                        }
                    }
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Object> deleteData(String schema, String tableName, Object internalId, String pkValue) {
        validateIdentifiers(schema, tableName);
        try {
            if (internalId == null || internalId.toString().trim().isEmpty()) {
                return Result.error("删除失败：无法获取行唯一标识 (ROWID)");
            }
            tableDataMapper.deleteByRowId(schema, tableName, internalId.toString());
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
                tableDataMapper.deleteByRowId(schema, tableName, rowIds.get(i));
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
                String pkCol = metadataMapper.getPkColumn(schema, tableName);
                List<Map<String, String>> childTables = metadataMapper.getAllChildTables(schema, tableName);
                if (pkCol != null && childTables != null && !childTables.isEmpty()) {
                    for (int i = failIndex; i < rowIds.size(); i++) {
                        String id = rowIds.get(i);
                        try {
                            Map<String, Object> row = tableDataMapper.getDataByRowId(schema, tableName, id);
                            if (row == null) continue;
                            Object pkVal = row.get(pkCol);
                            for (Map<String, String> child : childTables) {
                                String cTable = child.get("TABLE_NAME");
                                String cCol = child.get("COLUMN_NAME");
                                int count = tableDataMapper.countReference(schema, cTable, cCol, pkVal);
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

    /**
     * 【新增】LOB字段预览/下载
     * 直接向 HttpServletResponse 写入流，无需经过 MyBatis Mapper 映射，提高性能
     */
    public void previewLob(String schema, String tableName, String colName, String rowId, boolean download, HttpServletResponse response) {
        validateIdentifiers(schema, tableName);
        // colName 也需要校验，防止 SQL 注入
        if (!colName.matches("^[a-zA-Z0-9_]+$")) {
            try { response.sendError(400, "非法列名"); } catch (Exception e) {}
            return;
        }

        String connId = DynamicContext.getKey();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = ConnectionManager.getNewConnection(connId);
            // 使用 ROWID 定位效率最高
            String sql = String.format("SELECT \"%s\" FROM \"%s\".\"%s\" WHERE ROWID = ?", colName, schema, tableName);
            ps = conn.prepareStatement(sql);
            ps.setString(1, rowId);
            rs = ps.executeQuery();

            if (rs.next()) {
                Object obj = rs.getObject(1);

                if (obj == null) {
                    response.getWriter().write("NULL");
                    return;
                }

                if (obj instanceof Blob) {
                    Blob blob = (Blob) obj;
                    try (InputStream is = blob.getBinaryStream();
                         OutputStream os = response.getOutputStream()) {

                        if (download) {
                            response.setContentType("application/octet-stream");
                            response.setHeader("Content-Disposition", "attachment; filename=\"blob_data.bin\"");
                        } else {
                            // 默认按图片处理，浏览器无法显示的会自动下载
                            response.setContentType("image/jpeg");
                        }

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                } else if (obj instanceof Clob) {
                    Clob clob = (Clob) obj;
                    response.setContentType("text/plain; charset=UTF-8");
                    if (download) {
                        response.setHeader("Content-Disposition", "attachment; filename=\"clob_data.txt\"");
                    }
                    try (Reader reader = clob.getCharacterStream();
                         Writer writer = response.getWriter()) {
                        char[] buffer = new char[8192];
                        int charsRead;
                        while ((charsRead = reader.read(buffer)) != -1) {
                            writer.write(buffer, 0, charsRead);
                        }
                    }
                } else if (obj instanceof String) {
                    response.setContentType("text/plain; charset=UTF-8");
                    response.getWriter().write((String) obj);
                } else if (obj instanceof byte[]) {
                    response.setContentType("application/octet-stream");
                    if (download) {
                        response.setHeader("Content-Disposition", "attachment; filename=\"binary_data.bin\"");
                    }
                    response.getOutputStream().write((byte[]) obj);
                } else {
                    response.getWriter().write("不支持预览的数据类型: " + obj.getClass().getName());
                }
            } else {
                response.sendError(404, "未找到记录");
            }
        } catch (Exception e) {
            log.error("读取LOB失败", e);
            try { response.sendError(500, "读取大字段失败: " + e.getMessage()); } catch (Exception ex) {}
        } finally {
            try { if (rs != null) rs.close(); if (ps != null) ps.close(); if (conn != null) conn.close(); } catch (Exception e) {}
        }
    }

    /**
     * 【新增】LOB字段上传更新
     */
    public Result<Object> uploadLob(String schema, String tableName, String colName, String rowId, MultipartFile file) {
        validateIdentifiers(schema, tableName);
        if (!colName.matches("^[a-zA-Z0-9_]+$")) return Result.error("非法列名");

        String connId = DynamicContext.getKey();
        Connection conn = null;
        PreparedStatement ps = null;                                        

        try {
            conn = ConnectionManager.getNewConnection(connId);
            // 构造更新语句
            String sql = String.format("UPDATE \"%s\".\"%s\" SET \"%s\" = ? WHERE ROWID = ?", schema, tableName, colName);
            ps = conn.prepareStatement(sql);

            if (file.isEmpty()) {
                ps.setNull(1, java.sql.Types.BLOB);
            } else {
                // 通用流式写入，驱动会自动处理 BLOB/CLOB
                ps.setBinaryStream(1, file.getInputStream(), file.getSize());
            }

            ps.setString(2, rowId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                return Result.success("上传成功");
            } else {
                return Result.error("未找到记录或更新失败");
            }
        } catch (Exception e) {
            log.error("上传LOB失败", e);
            return Result.error("上传失败: " + e.getMessage());
        } finally {
            try { if (ps != null) ps.close(); if (conn != null) conn.close(); } catch (Exception e) {}
        }
    }
}