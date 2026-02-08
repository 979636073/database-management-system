package com.example.dmdb.service;

import com.example.dmdb.common.Result;

public interface TablespaceService {
    Result<Object> list();
    Result<Object> getFiles(String tablespaceName);
    Result<Object> create(String name, String filePath, Integer sizeMb, Boolean autoExtend, Integer nextSizeMb, Integer maxSizeMb);
// [修改] 增加 tablespaceName 参数
    Result<Object> alterDatafile(String tablespaceName, String filePath, Boolean autoExtend, Integer nextSizeMb, Integer maxSizeMb);
    Result<Object> delete(String name);
}