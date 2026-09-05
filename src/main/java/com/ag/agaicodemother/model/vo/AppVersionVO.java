package com.ag.agaicodemother.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用版本信息 VO（版本列表接口的返回元素）
 * 用于前端渲染版本管理面板：展示每个历史版本号、是否为当前版本、生成时间
 */
@Data
public class AppVersionVO implements Serializable {

    /**
     * 版本号（如 1、2、3），从版本目录名 v{n} 解析而来
     */
    private Integer version;

    /**
     * 是否为当前生效版本
     * true 表示该版本目录会被部署、会被默认预览地址访问
     */
    private Boolean isCurrent;

    /**
     * 版本创建时间
     * 取版本目录在文件系统上的最后修改时间（AI 生成完成保存文件的时间）
     */
    private LocalDateTime createTime;

    // 序列化版本号，保证对象序列化/反序列化的兼容性
    private static final long serialVersionUID = 1L;
}