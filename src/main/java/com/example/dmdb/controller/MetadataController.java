package com.example.dmdb.controller;

import com.example.dmdb.common.Result;
import com.example.dmdb.service.impl.MetadataServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/db") // 保持与原来一致
@CrossOrigin
public class MetadataController {

    @Autowired private MetadataServiceImpl metadataService;

    @GetMapping("/schemas")
    public Result<List<String>> getSchemas() { return metadataService.getSchemas(); }

    @GetMapping("/tables")
    public Result<List<Map<String, String>>> getTables(@RequestParam String schema) { return metadataService.getTables(schema); }

    @GetMapping("/columns")
    public Result<List<Map<String, Object>>> getColumns(@RequestParam String schema, @RequestParam String tableName) { return metadataService.getColumns(schema, tableName); }

    @GetMapping("/views")
    public Result<List<Map<String, Object>>> getViews(@RequestParam String schema) { return metadataService.getViews(schema); }

    @GetMapping("/triggers")
    public Result<List<Map<String, Object>>> getTriggers(@RequestParam String schema) { return metadataService.getTriggers(schema); }

    @GetMapping("/ddl")
    public Result<String> getDDL(@RequestParam String schema, @RequestParam String tableName) { return metadataService.getTableDDL(schema, tableName); }

    @GetMapping("/indexes")
    public Result<List<Map<String, Object>>> getIndexes(@RequestParam String schema, @RequestParam String tableName) { return metadataService.getIndexes(schema, tableName); }

    @GetMapping("/foreign-keys")
    public Result<List<Map<String, Object>>> getForeignKeys(@RequestParam String schema, @RequestParam String tableName) { return metadataService.getForeignKeys(schema, tableName); }

    @GetMapping("/er-data")
    public Result<Map<String, Object>> getErData(@RequestParam String schema, @RequestParam String tableName, @RequestParam(defaultValue = "false") boolean showAll, @RequestParam(required = false) List<String> expandedTables) { return metadataService.getErData(schema, tableName, showAll, expandedTables); }
}