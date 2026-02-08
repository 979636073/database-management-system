package com.example.dmdb.controller;

import com.example.dmdb.common.Result;
import com.example.dmdb.service.TablespaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/db/tablespace")
public class TablespaceController {

    @Autowired
    private TablespaceService tablespaceService;

    @GetMapping("/list")
    public Result<Object> list() {
        return tablespaceService.list();
    }

    @GetMapping("/files")
    public Result<Object> files(@RequestParam String name) {
        return tablespaceService.getFiles(name);
    }

    @PostMapping("/create")
    public Result<Object> create(@RequestBody Map<String, Object> params) {
        String name = (String) params.get("name");
        String filePath = (String) params.get("filePath");
        Integer size = (Integer) params.get("size");
        Boolean autoExtend = (Boolean) params.get("autoExtend");
        Integer nextSize = params.get("nextSize") != null ? Integer.parseInt(params.get("nextSize").toString()) : 0;
        Integer maxSize = params.get("maxSize") != null ? Integer.parseInt(params.get("maxSize").toString()) : 0;

        return tablespaceService.create(name, filePath, size, autoExtend, nextSize, maxSize);
    }

    // [修改] 增加 tablespaceName 参数的接收
    @PostMapping("/alter")
    public Result<Object> alter(@RequestBody Map<String, Object> params) {
        String tablespaceName = (String) params.get("tablespaceName"); // 新增
        String filePath = (String) params.get("filePath");
        Boolean autoExtend = (Boolean) params.get("autoExtend");
        Integer nextSize = params.get("nextSize") != null ? Integer.parseInt(params.get("nextSize").toString()) : 0;
        Integer maxSize = params.get("maxSize") != null ? Integer.parseInt(params.get("maxSize").toString()) : 0;

        return tablespaceService.alterDatafile(tablespaceName, filePath, autoExtend, nextSize, maxSize);
    }

    @DeleteMapping("/delete")
    public Result<Object> delete(@RequestParam String name) {
        return tablespaceService.delete(name);
    }
}