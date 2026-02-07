package com.example.dmdb.controller;

import com.example.dmdb.common.Result;
import com.example.dmdb.service.impl.UserServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/db/users") // 前端请求路径前缀
public class UserController {

    @Autowired
    private UserServiceImpl userService;

    // 1. 获取用户列表 (包含表空间等元数据)
    @GetMapping("/list")
    public Result<Object> list() {
        return userService.getUserList();
    }

    // 2. 获取指定用户的详细权限 (系统权限、对象权限、角色)
    @GetMapping("/privileges")
    public Result<Object> getPrivileges(@RequestParam String username) {
        return userService.getUserPrivileges(username);
    }

    // 3. 创建用户
    @PostMapping("/create")
    public Result<Object> create(@RequestBody Map<String, Object> params) {
        return userService.createUser(params);
    }

    // 4. 修改用户 (锁定/解锁/改密/配额)
    @PostMapping("/alter")
    public Result<Object> alter(@RequestBody Map<String, Object> params) {
        return userService.alterUser(params);
    }

    // 5. 授权或回收 (核心权限操作)
    @PostMapping("/grant-revoke")
    public Result<Object> grantRevoke(@RequestBody Map<String, Object> params) {
        return userService.grantRevoke(params);
    }

    // 6. 删除用户
    @DeleteMapping("/delete")
    public Result<Object> delete(@RequestParam String username) {
        return userService.dropUser(username);
    }
}