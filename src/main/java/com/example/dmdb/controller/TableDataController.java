package com.example.dmdb.controller;

import com.example.dmdb.common.Result;
import com.example.dmdb.config.DynamicContext;
import com.example.dmdb.service.impl.TableDataServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/db") // 保持一致
@CrossOrigin
public class TableDataController {

    @Autowired
    private TableDataServiceImpl tableDataService;

    @GetMapping("/data")
    public Result<Map<String, Object>> getData(@RequestParam String schema, @RequestParam String tableName, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int size) {
        return tableDataService.getData(schema, tableName, page, size);
    }

    @PostMapping("/filter")
    public Result<Map<String, Object>> filter(@RequestBody Map<String, Object> payload) {
        return tableDataService.filterData(payload);
    }

    @PostMapping("/save")
    public Result<Object> save(@RequestParam String schema, @RequestParam String tableName, @RequestBody Map<String, Object> row) {
        return tableDataService.saveData(schema, tableName, row);
    }

    @PostMapping("/save/batch")
    public Result<Object> saveBatch(@RequestParam String schema, @RequestParam String tableName, @RequestBody Map<String, List<Map<String, Object>>> payload) {
        return tableDataService.saveBatch(schema, tableName, payload);
    }

    @DeleteMapping("/delete")
    public Result<Object> delete(@RequestParam String schema, @RequestParam String tableName, @RequestParam Object internalId, @RequestParam(required = false) String pkValue) {
        return tableDataService.deleteData(schema, tableName, internalId, pkValue);
    }

    @PostMapping("/delete/batch")
    public Result<Object> deleteBatch(@RequestParam String schema, @RequestParam String tableName, @RequestBody List<String> rowIds) {
        return tableDataService.deleteBatch(schema, tableName, rowIds);
    }

    @PostMapping("/save/cascade")
    public Result<String> saveCascade() {
        return Result.error("暂不支持");
    }

    /**
     * 【新增】LOB 预览/下载接口
     * 注意：由于 img 标签和 window.open 无法携带 Header，必须允许通过 URL 参数传递 connId
     */
    @GetMapping("/lob/preview")
    public void previewLob(@RequestParam String schema,
                           @RequestParam String tableName,
                           @RequestParam String colName,
                           @RequestParam String rowId,
                           @RequestParam(defaultValue = "false") boolean download,
                           @RequestParam(required = false) String connId,
                           HttpServletResponse response) {
        // 如果 URL 中传递了 connId，手动设置到上下文中
        if (connId != null && !connId.isEmpty()) {
            DynamicContext.setKey(connId);
        }
        tableDataService.previewLob(schema, tableName, colName, rowId, download, response);
    }

    /**
     * 【新增】LOB 上传接口
     */
    @PostMapping("/lob/upload")
    public Result<Object> uploadLob(@RequestParam String schema,
                                    @RequestParam String tableName,
                                    @RequestParam String colName,
                                    @RequestParam String rowId,
                                    @RequestParam("file") MultipartFile file) {
        return tableDataService.uploadLob(schema, tableName, colName, rowId, file);
    }


}