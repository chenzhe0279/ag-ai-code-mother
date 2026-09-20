package com.ag.agaicodemother.service;


import com.ag.agaicodemother.model.dto.safty.SafetyReviewRecordQueryRequest;
import com.ag.agaicodemother.model.entity.SafetyReviewRecord;
import com.mybatisflex.core.paginate.Page;

/**
 * 内容安全审查记录 服务层。
 */
public interface SafetyReviewRecordService {

    /**
     * 保存审查记录（内部使用，失败不影响主流程）
     *
     * @param record 审查记录
     * @return 是否保存成功
     */
    boolean saveRecord(SafetyReviewRecord record);

    /**
     * 分页查询审查记录（管理端）
     *
     * @param queryRequest 查询请求
     * @return 分页结果
     */
    Page<SafetyReviewRecord> pageRecords(SafetyReviewRecordQueryRequest queryRequest);
}