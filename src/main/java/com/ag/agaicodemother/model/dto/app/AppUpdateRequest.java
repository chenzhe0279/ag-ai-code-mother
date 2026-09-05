package com.ag.agaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新应用请求
 */
@Data
public class AppUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用标签，逗号分隔，如 "游戏,工具"
     * 用户可以在编辑页修改自己应用的标签；不传表示不修改（整体覆盖式更新）
     */
    private String tags;

    /**
     * 可见范围：public（公开）/ private（私有）
     * 用户可以在编辑页切换自己应用的公开/私有状态；不传表示不修改
     */
    private String visibility;

    private static final long serialVersionUID = 1L;
}
