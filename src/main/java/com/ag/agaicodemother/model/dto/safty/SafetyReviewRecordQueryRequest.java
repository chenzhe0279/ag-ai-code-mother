package com.ag.agaicodemother.model.dto.safty;

import com.ag.agaicodemother.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 审查记录分页查询请求（管理端）
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SafetyReviewRecordQueryRequest extends PageRequest implements Serializable {

    /**
     * 触发用户id
     */
    private Long userId;

    /**
     * 关联应用id
     */
    private Long appId;

    /**
     * 检测方式：static/ai
     */
    private String detectionType;

    /**
     * 处理结果：blocked/failed_open
     */
    private String handleResult;
}