package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.config.DynamicContext;
import com.example.dmdb.mapper.MetadataMapper;
import com.example.dmdb.mapper.TableDataMapper;
import com.example.dmdb.service.ConnectionManager;
import com.example.dmdb.service.base.AbstractDbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

@Service
public class TableDataServiceImpl extends AbstractDbService {

    private static final Logger log = LoggerFactory.getLogger(TableDataServiceImpl.class);

    @Autowired
    private TableDataMapper tableDataMapper;

    @Autowired
    private MetadataMapper metadataMapper;

    public Result<Map<String, Object>> getData(String schema, String tableName, int page, int size) {
        validateIdentifiers(schema, tableName);
        int offset = (page - 1) * size;
        long total = tableDataMapper.countData(schema, tableName);
        List<Map<String, Object>> list = processResultList(tableDataMapper.getDataPage(schema, tableName, size, offset));
        maskLobFields(schema, tableName, list);
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
        List<Map<String, Object>> list = processResultList(tableDataMapper.queryByConditionsPage(schema, tableName, conditions, logic, size, offset));
        maskLobFields(schema, tableName, list);
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

    @Transactional(rollbackFor = Exception.class)
    public Result<Object> saveBatch(String schema, String tableName, Map<String, List<Map<String, Object>>> payload) {
        validateIdentifiers(schema, tableName);
        List<Map<String, Object>> insertList = payload.get("insertList");
        List<Map<String, Object>> updateList = payload.get("updateList");
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

    public Result<Object> deleteRow(String schema, String tableName, String internalId, String pkValue) {
        return deleteData(schema, tableName, internalId, pkValue);
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
                        } catch (Exception ex) {}
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

    private void maskLobFields(String schema, String tableName, List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return;
        List<Map<String, Object>> columns = metadataMapper.getColumns(schema, tableName);
        if (columns == null || columns.isEmpty()) {
            columns = metadataMapper.getColumns(schema, tableName.toUpperCase());
        }
        Set<String> binaryLobCols = new HashSet<>();
        if (columns != null) {
            for (Map<String, Object> col : columns) {
                Object typeObj = col.get("DATA_TYPE");
                if (typeObj != null) {
                    String type = typeObj.toString().toUpperCase();
                    if (type.contains("BLOB") || type.contains("IMAGE") || type.contains("BINARY") ||
                            type.contains("VARBINARY") || type.contains("BFILE") || type.contains("GEOMETRY") ||
                            type.contains("RAW") || type.contains("BYTE")) {
                        binaryLobCols.add(((String) col.get("COLUMN_NAME")).toUpperCase());
                    }
                }
            }
        }
        for (Map<String, Object> row : list) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (val == null) continue;
                if (binaryLobCols.contains(key.toUpperCase())) {
                    entry.setValue("[BINARY 数据]");
                    continue;
                }
                if (val instanceof Clob) {
                    entry.setValue("[CLOB 数据]");
                } else if (val instanceof Blob) {
                    entry.setValue("[BLOB 数据]");
                } else if (val instanceof byte[]) {
                    entry.setValue("[BINARY 数据]");
                } else if (val instanceof InputStream) {
                    entry.setValue("[BINARY 数据]");
                }
            }
        }
    }

    private void preprocessLobData(String schema, String tableName, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return;
        List<Map<String, Object>> columns = metadataMapper.getColumns(schema, tableName);
        if (columns == null || columns.isEmpty()) {
            columns = metadataMapper.getColumns(schema, tableName.toUpperCase());
        }
        Set<String> blobColumns = new HashSet<>();
        if (columns != null) {
            for (Map<String, Object> col : columns) {
                String type = ((String) col.get("DATA_TYPE")).toUpperCase();
                if (type.contains("BLOB") || type.contains("IMAGE") || type.contains("BINARY") ||
                        type.contains("VARBINARY") || type.contains("RAW")) {
                    blobColumns.add((String) col.get("COLUMN_NAME"));
                }
            }
        }
        if (blobColumns.isEmpty()) return;
        Map<String, String> colNameMap = new HashMap<>();
        for(String c : blobColumns) {
            colNameMap.put(c.toUpperCase(), c);
        }
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> entry : new HashSet<>(row.entrySet())) {
                String key = entry.getKey();
                if (colNameMap.containsKey(key.toUpperCase())) {
                    Object val = entry.getValue();
                    if (val instanceof String) {
                        String base64Str = (String) val;
                        if ("[BLOB 数据]".equals(base64Str) || "[BINARY 数据]".equals(base64Str)) {
                            if (row.containsKey("DB_INTERNAL_ID")) {
                                row.remove(key);
                            } else {
                                row.put(key, null);
                            }
                        } else if (base64Str.isEmpty()) {
                            row.put(key, null);
                        } else {
                            try {
                                if (base64Str.contains(",")) {
                                    base64Str = base64Str.split(",")[1];
                                }
                                byte[] bytes = Base64.getDecoder().decode(base64Str);
                                row.put(key, bytes);
                            } catch (IllegalArgumentException e) {}
                        }
                    }
                }
            }
        }
    }

    /**
     * 【修正】LOB字段预览/下载
     * 功能增强：
     * 1. 修复 ROWID 绑定错误 (NumberFormatException)
     * 2. 增加文件魔数检测，自动识别 MIME 类型和扩展名
     */
    public void previewLob(String schema, String tableName, String colName, String rowId, boolean download, HttpServletResponse response) {
        validateIdentifiers(schema, tableName);
        if (!colName.matches("^[a-zA-Z0-9_]+$")) return;
        if (!isValidRowId(rowId)) return;

        response.reset();

        String connId = DynamicContext.getKey();
        if (connId == null) {
            try { response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing Connection ID"); } catch (Exception e) {}
            return;
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = ConnectionManager.getNewConnection(connId);
            if (conn == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Connection ID or Session Expired");
                return;
            }

            // 【关键修改】使用拼接 SQL 避免驱动绑定错误
            String sql = String.format("SELECT \"%s\" FROM \"%s\".\"%s\" WHERE ROWID = '%s'", colName, schema, tableName, rowId);
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();

            if (rs.next()) {
                Object obj = rs.getObject(1);
                if (obj == null) return;

                if (obj instanceof Blob) {
                    Blob blob = (Blob) obj;

                    // 使用 BufferedInputStream 包装以便支持 mark/reset 操作
                    try (InputStream is = new BufferedInputStream(blob.getBinaryStream())) {

                        // 【新增】智能识别文件类型
                        Map<String, String> fileInfo = detectFileType(is);
                        String mimeType = fileInfo.get("mime");
                        String ext = fileInfo.get("ext");

                        response.setContentType(mimeType);

                        if (download) {
                            String filename = "blob_data." + ext;
                            response.setHeader("Content-Disposition", "attachment; filename=\"" + URLEncoder.encode(filename, "UTF-8") + "\"");
                        }

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            response.getOutputStream().write(buffer, 0, bytesRead);
                        }
                        response.getOutputStream().flush();
                    }
                } else if (obj instanceof Clob) {
                    // Clob 处理保持不变
                    Clob clob = (Clob) obj;
                    response.setContentType("text/plain; charset=UTF-8");
                    response.setCharacterEncoding("UTF-8");
                    if (download) {
                        response.setHeader("Content-Disposition", "attachment; filename=\"" + URLEncoder.encode("clob_data.txt", "UTF-8") + "\"");
                    }
                    try (Reader reader = clob.getCharacterStream();
                         Writer writer = response.getWriter()) {
                        char[] buffer = new char[8192];
                        int charsRead;
                        while ((charsRead = reader.read(buffer)) != -1) {
                            writer.write(buffer, 0, charsRead);
                        }
                        writer.flush();
                    }
                } else if (obj instanceof String) {
                    response.setContentType("text/plain; charset=UTF-8");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write((String) obj);
                } else if (obj instanceof byte[]) {
                    // 处理 byte[] 类型的检测
                    byte[] bytes = (byte[]) obj;
                    String mime = "application/octet-stream";
                    String ext = "bin";

                    // 简单的魔数检测 (byte array)
                    String hex = bytesToHex(bytes, 8); // 只读前8字节
                    if (hex.startsWith("FFD8FF")) { mime = "image/jpeg"; ext = "jpg"; }
                    else if (hex.startsWith("89504E47")) { mime = "image/png"; ext = "png"; }
                    else if (hex.startsWith("47494638")) { mime = "image/gif"; ext = "gif"; }
                    else if (hex.startsWith("25504446")) { mime = "application/pdf"; ext = "pdf"; }
                    else if (hex.startsWith("504B0304")) { mime = "application/zip"; ext = "zip"; }

                    response.setContentType(mime);
                    if (download) {
                        response.setHeader("Content-Disposition", "attachment; filename=\"binary_data." + ext + "\"");
                    }
                    response.getOutputStream().write(bytes);
                } else {
                    response.setContentType("text/plain; charset=UTF-8");
                    response.getWriter().write("不支持预览的数据类型: " + obj.getClass().getName());
                }
            } else {
                response.sendError(404, "Not Found");
            }
        } catch (Exception e) {
            log.error("读取LOB失败", e);
            try {
                response.sendError(500, "读取大字段失败: " + e.getMessage());
            } catch (Exception ex) {}
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (Exception e) {}
        }
    }

    /**
     * 【新增】通过文件流魔数检测文件类型
     */
    private Map<String, String> detectFileType(InputStream is) throws java.io.IOException {
        Map<String, String> info = new HashMap<>();
        info.put("mime", "application/octet-stream"); // 默认
        info.put("ext", "bin");

        if (!is.markSupported()) return info;

        is.mark(16); // 标记位置
        byte[] header = new byte[8];
        int read = is.read(header);
        is.reset(); // 重置流，不影响后续读取

        if (read < 4) return info;

        String hex = bytesToHex(header, read).toUpperCase();

        if (hex.startsWith("FFD8FF")) { info.put("mime", "image/jpeg"); info.put("ext", "jpg"); }
        else if (hex.startsWith("89504E47")) { info.put("mime", "image/png"); info.put("ext", "png"); }
        else if (hex.startsWith("47494638")) { info.put("mime", "image/gif"); info.put("ext", "gif"); }
        else if (hex.startsWith("25504446")) { info.put("mime", "application/pdf"); info.put("ext", "pdf"); }
        else if (hex.startsWith("504B0304")) { info.put("mime", "application/zip"); info.put("ext", "zip"); } // Zip, Docx, Xlsx
        else if (hex.startsWith("424D")) { info.put("mime", "image/bmp"); info.put("ext", "bmp"); }

        return info;
    }

    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, length); i++) {
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }

    public Result<Object> uploadLob(String schema, String tableName, String colName, String rowId, MultipartFile file) {
        validateIdentifiers(schema, tableName);
        if (!colName.matches("^[a-zA-Z0-9_]+$")) return Result.error("非法列名");
        if (!isValidRowId(rowId)) return Result.error("非法 ROWID 格式");

        String connId = DynamicContext.getKey();
        if (connId == null) return Result.error("丢失连接信息");

        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = ConnectionManager.getNewConnection(connId);
            if (conn == null) return Result.error("无效的连接或会话已过期");

            String sql = String.format("UPDATE \"%s\".\"%s\" SET \"%s\" = ? WHERE ROWID = '%s'", schema, tableName, colName, rowId);
            ps = conn.prepareStatement(sql);

            if (file == null || file.isEmpty()) {
                ps.setNull(1, Types.BLOB);
            } else {
                ps.setBinaryStream(1, file.getInputStream(), file.getSize());
            }

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
            try {
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (Exception e) {}
        }
    }

    private boolean isValidRowId(String rowId) {
        if (rowId == null || rowId.isEmpty()) return false;
        return rowId.matches("^[a-zA-Z0-9+/=]+$");
    }
}