package com.ag.agaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用创建请求
 */
@Data
public class AppAddRequest implements Serializable {

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    /**
     * 应用标签，逗号分隔，如 "游戏,工具"
     * 创建时可选：不传则应用初始没有标签
     */
    private String tags;

    /**
     * 可见范围：public（公开）/ private（私有）
     * 创建时可选：不传则后端使用默认值"公开"
     */
    private String visibility;

    private static final long serialVersionUID = 1L;
}
