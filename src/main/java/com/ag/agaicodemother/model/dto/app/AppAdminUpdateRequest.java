package com.ag.agaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppAdminUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用封面
     */
    private String cover;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 可见范围：public（公开）/ private（私有）
     * 管理员也可以修改应用的可见范围；不传表示不修改
     */
    private String visibility;

    private static final long serialVersionUID = 1L;
}
