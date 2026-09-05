package com.ag.agaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用版本回退请求 DTO
 * 前端调用 POST /app/version/rollback 时传入的 JSON 请求体对应的类
 * 示例 JSON：{ "appId": 123, "targetVersion": 2 }
 */
@Data
public class AppRollbackRequest implements Serializable {

    /**
     * 应用 id（要回退哪个应用）
     */
    private Long appId;

    /**
     * 目标版本号（希望回退到的历史版本，如 2 表示回退到 v2）
     * 注意：回退不是把指针指回 v2，而是把 v2 的内容复制成一个全新版本，
     * 例如当前是 v5，回退到 v2 后会生成内容与 v2 相同的 v6
     */
    private Integer targetVersion;

    // 序列化版本号，保证对象序列化/反序列化的兼容性
    private static final long serialVersionUID = 1L;
}