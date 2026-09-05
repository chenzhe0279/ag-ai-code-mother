package com.ag.agaicodemother.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * app 封装类
 */
@Data
public class AppVO implements Serializable {

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
     * 应用初始化的 prompt
     */
    private String initPrompt;

    /**
     * 代码生成类型（枚举）
     */
    private String codeGenType;

    /**
     * 生成状态：not_start/generating/succeeded/failed（生成状态功能新增）
     * 前端据此展示"生成中"转圈动画、"生成成功/失败"提示，轮询可实时跟踪进度
     */
    private String genStatus;

    /**
     * 当前代码版本号（前端展示用，如"当前版本 v3"）
     */
    private Integer currentVersion;

    /**
     * 部署标识
     */
    private String deployKey;

    /**
     * 部署时间
     */
    private LocalDateTime deployedTime;

    /**
     * 部署状态：online（已上线）/ offline（已下线）（部署控制功能新增）
     * 前端据此展示"已上线/已下线"标签，并决定"查看作品"按钮是否可用
     */
    private String deployStatus;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 可见范围：public（公开）/ private（私有）
     * 前端据此展示"私有"标签、编辑页回显可见范围选项
     */
    private String visibility;

    /**
     * 创建用户id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建用户信息
     */
    private UserVO user;

    /**
     * 应用标签，逗号分隔字符串（标签系统新增）
     * 前端拿到后按逗号 split 即可渲染成标签列表
     */
    private String tags;

    private static final long serialVersionUID = 1L;
}
