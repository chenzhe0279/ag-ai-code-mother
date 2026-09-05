package com.ag.agaicodemother.model.dto.app;

import com.ag.agaicodemother.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class AppQueryRequest extends PageRequest implements Serializable {

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
     * 部署标识
     */
    private String deployKey;

    /**
     * 部署状态：online（已上线）/ offline（已下线）（部署控制功能新增）
     * 查询过滤条件：供管理后台按部署状态筛选应用
     */
    private String deployStatus;

    /**
     * 生成状态：not_start/generating/succeeded/failed（生成状态功能新增）
     * 查询过滤条件：供管理后台按生成状态筛选应用
     */
    private String genStatus;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 创建用户id
     */
    private Long userId;

    /**
     * 可见范围：public（公开）/ private（私有）
     * 查询过滤条件，供管理员按可见范围筛选应用
     */
    private String visibility;

    /**
     * 标签名（标签系统新增）
     * 查询过滤条件：传入后只返回"拥有该标签"的应用（对 tags 字段做 LIKE 模糊匹配）
     */
    private String tagName;

    private static final long serialVersionUID = 1L;
}
