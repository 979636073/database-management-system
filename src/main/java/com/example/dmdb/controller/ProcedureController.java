package com.example.dmdb.controller;

import com.example.dmdb.common.Result;
import com.example.dmdb.service.ProcedureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/db/proc")
public class ProcedureController {

    @Autowired
    private ProcedureService procedureService;

    @GetMapping("/list")
    public Result<Object> list(@RequestParam String schema) {
        return procedureService.list(schema);
    }

    @GetMapping("/detail")
    public Result<Object> detail(@RequestParam String schema,
                                 @RequestParam String name,
                                 @RequestParam String type) {
        return procedureService.getDetail(schema, name, type);
    }

    @PostMapping("/compile")
    public Result<Object> compile(@RequestBody Map<String, String> params) {
        return procedureService.compile(params.get("sql"));
    }

    @DeleteMapping("/delete")
    public Result<Object> delete(@RequestParam String schema,
                                 @RequestParam String name,
                                 @RequestParam String type) {
        return procedureService.delete(schema, name, type);
    }
}