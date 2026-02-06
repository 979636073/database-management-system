package com.example.dmdb.controller;

import com.example.dmdb.common.Result;
import com.example.dmdb.service.ConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/connection")
@CrossOrigin
public class ConnectionController {

    @Autowired private ConnectionManager connectionManager;

    @PostMapping("/connect")
    public Result<String> connect(@RequestBody Map<String, String> config) {
        try {
            // 【修改】优先使用前端传来的 ID (用于刷新页面后重连)，如果没有则生成新 ID
            String connId = config.get("id");
            if (connId == null || connId.isEmpty()) {
                connId = UUID.randomUUID().toString();
            }

            connectionManager.addDataSource(
                    connId,
                    config.get("host"),
                    config.get("port"),
                    config.get("user"),
                    config.get("password")
            );

            return Result.success(connId);
        } catch (Exception e) {
            return Result.error("连接失败: " + e.getMessage());
        }
    }

    // 【新增】更新连接
    @PostMapping("/update")
    public Result<String> update(@RequestHeader("Conn-Id") String connId, @RequestBody Map<String, String> config) {
        try {
            // 先移除旧的
            connectionManager.removeDataSource(connId);
            // 再添加新的 (ID不变)
            addDs(connId, config);
            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    // 【新增】删除连接
    @DeleteMapping("/delete")
    public Result<String> delete(@RequestHeader("Conn-Id") String connId) {
        connectionManager.removeDataSource(connId);
        return Result.success("删除成功");
    }

    // 提取公共方法
    private void addDs(String id, Map<String, String> config) throws SQLException {
        connectionManager.addDataSource(
                id, config.get("host"), config.get("port"), config.get("user"), config.get("password")
        );
    }


}