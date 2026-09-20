package com.ag.agaicodemother.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 内容安全审查记录 实体类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("safety_review_record")
public class SafetyReviewRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 触发用户id
     */
    @Column("userId")
    private Long userId;

    /**
     * 关联应用id
     */
    @Column("appId")
    private Long appId;

    /**
     * 用户输入内容（截断存储）
     */
    @Column("userMessage")
    private String userMessage;

    /**
     * 检测方式：static静态关键词/ai大模型
     */
    @Column("detectionType")
    private String detectionType;

    /**
     * 命中的规则或关键词
     */
    @Column("triggerRule")
    private String triggerRule;

    /**
     * 风险类别（AI判定）
     */
    @Column("riskCategory")
    private String riskCategory;

    /**
     * 风险等级：high/medium/low
     */
    @Column("riskLevel")
    private String riskLevel;

    /**
     * AI判定理由
     */
    @Column("aiReason")
    private String aiReason;

    /**
     * 处理结果：blocked已拦截/failed_open检测异常降级放行
     */
    @Column("handleResult")
    private String handleResult;

    /**
     * 客户端IP
     */
    @Column("clientIp")
    private String clientIp;

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