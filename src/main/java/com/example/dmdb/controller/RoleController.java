package com.example.dmdb.controller;

import com.example.dmdb.common.Result;
import com.example.dmdb.service.impl.RoleServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/db") // 保持一致，也可以改为 /api/role 但需要改前端
@CrossOrigin
public class RoleController {

    @Autowired private RoleServiceImpl roleService;

    @GetMapping("/roles")
    public Result<List<Map<String, Object>>> getRoles() { return roleService.getRoles(); }

    @PostMapping("/role/create")
    public Result<Object> createRole(@RequestBody Map<String, String> payload) { return roleService.createRole(payload.get("roleName")); }

    @DeleteMapping("/role/delete")
    public Result<Object> deleteRole(@RequestParam String roleName) { return roleService.deleteRole(roleName); }

    @GetMapping("/role/detail")
    public Result<Map<String, Object>> getRoleDetail(@RequestParam String roleName) { return roleService.getRoleDetail(roleName); }

    @PostMapping("/role/role-privs")
    public Result<Object> updateRoleRolePrivs(@RequestBody Map<String, Object> payload) { return roleService.updateRoleRolePrivs((String) payload.get("roleName"), (List<Map<String, Object>>) payload.get("changes")); }

    @PostMapping("/role/sys-privs")
    public Result<Object> updateRoleSysPrivs(@RequestBody Map<String, Object> payload) { return roleService.updateRoleSysPrivs((String) payload.get("roleName"), (List<Map<String, Object>>) payload.get("changes")); }

    @PostMapping("/role/obj-privs")
    public Result<Object> updateRoleObjPrivs(@RequestBody Map<String, Object> payload) { return roleService.updateRoleObjPrivs((String) payload.get("roleName"), (String) payload.get("schema"), (String) payload.get("table"), (List<Map<String, Object>>) payload.get("changes")); }
}