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
    public Result<String> connect(@RequestBody Map<String, String> params) {
        String host = params.get("host");
        String port = params.get("port");
        String user = params.get("user");
        String password = params.get("password");

        // 获取数据库类型，默认为 DM
        String dbType = params.get("dbType");
        if (dbType == null || dbType.trim().isEmpty()) {
            dbType = "DM";
        }

        // 获取服务名 (Oracle专用)
        String serviceName = params.get("serviceName");
        if (serviceName == null) {
            serviceName = "";
        }

        if (host == null || port == null || user == null || password == null) {
            return Result.error("缺少必要连接参数");
        }

        // 【关键修改】优先使用前端传入的 id (用于页面刷新后的重连)
        String connId = params.get("id");
        if (connId == null || connId.trim().isEmpty()) {
            // 如果前端没传，则生成新的 ID (用于新建连接)
            connId = UUID.randomUUID().toString();
        }

        try {
            // 调用 ConnectionManager 创建连接
            connectionManager.addDataSource(connId, host, port, user, password, dbType, serviceName);
            return Result.success(connId);
        } catch (Exception e) {
            e.printStackTrace();
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
        // 【新增】获取数据库类型，默认为 DM
        String dbType = config.getOrDefault("dbType", "DM");
        // 【新增】获取服务名 (Oracle需要)，默认为空
        String serviceName = config.getOrDefault("serviceName", "");

        connectionManager.addDataSource(
                id,
                config.get("host"),
                config.get("port"),
                config.get("user"),
                config.get("password"),
                dbType,        // 传入 dbType
                serviceName    // 传入 serviceName
        );
    }
}