package com.example.dmdb.service.impl;

import com.example.dmdb.common.Result;
import com.example.dmdb.config.DynamicContext;
import com.example.dmdb.mapper.MetadataMapper;
import com.example.dmdb.mapper.TableDataMapper;
import com.example.dmdb.service.ConnectionManager;
import com.example.dmdb.service.base.AbstractDbService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
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
import java.io.Reader;
import java.io.Writer;
import java.net.URLEncoder;
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

        // 【修改】使用 PageHelper 开启分页 (自动识别 Oracle/DM 生成 ROWNUM SQL)
        PageHelper.startPage(page, size);

        // 【修改】调用 Mapper (不再传递 offset/limit)
        List<Map<String, Object>> rawList = tableDataMapper.getDataPage(schema, tableName);

        // 【修改】使用 PageInfo 获取总记录数 (PageHelper 会自动执行 count 查询)
        PageInfo<Map<String, Object>> pageInfo = new PageInfo<>(rawList);
        long total = pageInfo.getTotal();

        List<Map<String, Object>> list = processResultList(rawList);
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

        validateIdentifiers(schema, tableName);

        // 【修改】开启分页
        PageHelper.startPage(page, size);

        // 【修改】执行查询
        List<Map<String, Object>> rawList = tableDataMapper.queryByConditionsPage(schema, tableName, conditions, logic);

        // 【修改】获取总数
        PageInfo<Map<String, Object>> pageInfo = new PageInfo<>(rawList);
        long total = pageInfo.getTotal();

        List<Map<String, Object>> list = processResultList(rawList);
        maskLobFields(schema, tableName, list);

        Map<String, Object> res = new HashMap<>();
        res.put("total", total);
        res.put("list", list);
        res.put("isView", isViewObject(schema, tableName));
        return Result.success(res);
    }

    // --- 下面的代码保持原样，与分页无关 ---
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

        // 预处理 LOB 数据
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
            // 标记事务回滚
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

            // ========================= 修复开始：恢复冲突检测逻辑 =========================
            try {
                // 1. 判断是否为完整性约束错误 (兼容 Oracle/DM 的常见错误码/关键字)
                String msg = e.getMessage() != null ? e.getMessage() : "";
                boolean isIntegrityError = msg.contains("integrity constraint") ||
                        msg.contains("violation of foreign key") ||
                        msg.contains("-2291") || // ORA-02291 (父键未找到)
                        (msg.contains("违反") && msg.contains("约束"));

                // 2. 只有是约束错误才进行昂贵的扫描检查
                if (isIntegrityError) {
                    // 获取该表的所有外键定义
                    List<Map<String, Object>> fks = metadataMapper.getForeignKeys(schema, tableName);

                    if (fks != null && !fks.isEmpty()) {
                        // 遍历所有提交的数据行 (包含新增和修改)
                        for (Map<String, Object> row : allRows) {
                            for (Map<String, Object> fk : fks) {
                                String myCol = (String) fk.get("COLUMN_NAME");   // 本表字段
                                String pTable = (String) fk.get("R_TABLE_NAME"); // 父表表名
                                String pCol = (String) fk.get("R_COLUMN_NAME");  // 父表字段

                                // 如果当前行包含该外键列，且值不为空
                                if (row.containsKey(myCol)) {
                                    Object val = row.get(myCol);
                                    if (val != null && !String.valueOf(val).isEmpty()) {
                                        // 3. 核心检查：去数据库查父表是否存在这个值
                                        int exist = tableDataMapper.countReference(schema, pTable, pCol, val);

                                        // 如果不存在 (exist == 0)，说明这就是冲突点
                                        if (exist == 0) {
                                            // 使用 "列名_值" 作为Key去重，避免同一错误报多次
                                            String uniqueKey = myCol + "_" + val;

                                            if (!aggregatedConflicts.containsKey(uniqueKey)) {
                                                Map<String, Object> c = new HashMap<>();
                                                c.put("TABLE_NAME", pTable); // 告诉前端这是哪个父表
                                                c.put("COLUMN_NAME", pCol);  // 告诉前端缺哪个字段
                                                c.put("CNT", "MISSING");     // 标记类型为缺失
                                                c.put("MY_VAL", val);        // 冲突值
                                                c.put("MY_VAL_LIST", new ArrayList<Object>()); // 收集所有冲突值
                                                aggregatedConflicts.put(uniqueKey, c);
                                            }
                                            // 将值加入列表
                                            List<Object> valList = (List<Object>) aggregatedConflicts.get(uniqueKey).get("MY_VAL_LIST");
                                            if (!valList.contains(val)) {
                                                valList.add(val);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to analyze batch conflict detail: {}", ex.getMessage());
            }
            // ========================= 修复结束 =========================
        }

        if (hasException) {
            // 如果检测到了具体的冲突数据，返回 503 和数据，前端会弹出"智能解决"窗口
            if (!aggregatedConflicts.isEmpty()) {
                Result<Object> r = new Result<>();
                r.setCode(503);
                r.setMsg("批量保存失败：存在数据完整性冲突");
                r.setData(new ArrayList<>(aggregatedConflicts.values()));
                return r;
            }
            // 否则抛出原始错误
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
        for (int i = 0; i < rowIds.size(); i++) {
            try {
                tableDataMapper.deleteByRowId(schema, tableName, rowIds.get(i));
            } catch (Exception e) {
                hasException = true;
                exceptionMsg = e.getMessage();
                break;
            }
        }
        if (hasException) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
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

            String sql = String.format("SELECT \"%s\" FROM \"%s\".\"%s\" WHERE ROWID = '%s'", colName, schema, tableName, rowId);
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();

            if (rs.next()) {
                Object obj = rs.getObject(1);
                if (obj == null) return;

                if (obj instanceof Blob) {
                    Blob blob = (Blob) obj;
                    try (InputStream is = new BufferedInputStream(blob.getBinaryStream())) {
                        Map<String, String> fileInfo = detectFileType(is);
                        response.setContentType(fileInfo.get("mime"));
                        if (download) {
                            String filename = "blob_data." + fileInfo.get("ext");
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
                    Clob clob = (Clob) obj;
                    response.setContentType("text/plain; charset=UTF-8");
                    response.setCharacterEncoding("UTF-8");
                    if (download) {
                        response.setHeader("Content-Disposition", "attachment; filename=\"" + URLEncoder.encode("clob_data.txt", "UTF-8") + "\"");
                    }
                    try (Reader reader = clob.getCharacterStream(); Writer writer = response.getWriter()) {
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
                    byte[] bytes = (byte[]) obj;
                    String mime = "application/octet-stream";
                    String ext = "bin";
                    String hex = bytesToHex(bytes, 8);
                    if (hex.startsWith("FFD8FF")) { mime = "image/jpeg"; ext = "jpg"; }
                    else if (hex.startsWith("89504E47")) { mime = "image/png"; ext = "png"; }
                    else if (hex.startsWith("25504446")) { mime = "application/pdf"; ext = "pdf"; }

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
            try { response.sendError(500, "读取大字段失败: " + e.getMessage()); } catch (Exception ex) {}
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (Exception e) {}
        }
    }

    private Map<String, String> detectFileType(InputStream is) throws java.io.IOException {
        Map<String, String> info = new HashMap<>();
        info.put("mime", "application/octet-stream");
        info.put("ext", "bin");
        if (!is.markSupported()) return info;
        is.mark(16);
        byte[] header = new byte[8];
        int read = is.read(header);
        is.reset();
        if (read < 4) return info;
        String hex = bytesToHex(header, read).toUpperCase();
        if (hex.startsWith("FFD8FF")) { info.put("mime", "image/jpeg"); info.put("ext", "jpg"); }
        else if (hex.startsWith("89504E47")) { info.put("mime", "image/png"); info.put("ext", "png"); }
        else if (hex.startsWith("47494638")) { info.put("mime", "image/gif"); info.put("ext", "gif"); }
        else if (hex.startsWith("25504446")) { info.put("mime", "application/pdf"); info.put("ext", "pdf"); }
        else if (hex.startsWith("504B0304")) { info.put("mime", "application/zip"); info.put("ext", "zip"); }
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

    @Transactional(rollbackFor = Exception.class)
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