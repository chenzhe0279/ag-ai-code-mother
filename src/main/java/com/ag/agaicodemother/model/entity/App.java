package com.ag.agaicodemother.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

import java.io.Serial;

import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 应用 实体类。
 *
 * @author <a href="https://github.com/chenzhe0279">陈爱国</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app")
public class App implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator,value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 应用名称
     */
    @Column("appName")
    private String appName;

    /**
     * 应用封面
     */
    private String cover;

    /**
     * 应用初始化的 prompt
     */
    @Column("initPrompt")
    private String initPrompt;

    /**
     * 代码生成类型（枚举）
     */
    @Column("codeGenType")
    private String codeGenType;

    /**
     * 生成状态（生成状态功能新增）
     * 取值：not_start（未开始）/ generating（生成中）/ succeeded（已成功）/ failed（失败）
     * 状态流转由 chatToGenCode 驱动：发起生成置 generating，
     * 流正常结束置 succeeded，流异常中断置 failed；前端轮询此字段展示实时进度
     */
    @Column("genStatus")
    private String genStatus;

    /**
     * 当前代码版本号
     */
    @Column("currentVersion")
    private Integer currentVersion;

    /**
     * 部署标识
     */
    @Column("deployKey")
    private String deployKey;

    /**
     * 部署时间
     */
    @Column("deployedTime")
    private LocalDateTime deployedTime;

    /**
     * 部署状态（部署控制功能新增）
     * 取值：online（已上线）/ offline（已下线）
     * 说明：该字段只对"部署过"的应用有意义（deployKey 非空）；
     * 下线 = 删除部署目录文件 + 状态置 offline，deployKey 保留，重新部署 URL 不变
     */
    @Column("deployStatus")
    private String deployStatus;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 可见范围
     * 取值：public（公开）/ private（私有）
     * 隐私保护规则：
     * 1. 普通用户只能看到别人"公开"的应用；
     * 2. 管理员可以查看所有应用；
     * 3. 创建者本人始终可以看到自己的应用（无论公开还是私有）。
     */
    @Column("visibility")
    private String visibility;

    /**
     * 应用标签（标签系统新增）
     * 存储格式：逗号分隔的标签文本，如 "游戏,工具,效率"
     * 为 null 或空串表示该应用没有设置标签
     */
    @Column("tags")
    private String tags;

    /**
     * 创建用户id
     */
    @Column("userId")
    private Long userId;

    /**
     * 编辑时间
     */
    @Column("editTime")
    private LocalDateTime editTime;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;

}
