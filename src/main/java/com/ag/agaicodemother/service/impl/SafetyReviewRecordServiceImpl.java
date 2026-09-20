package com.ag.agaicodemother.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ag.agaicodemother.mapper.SafetyReviewRecordMapper;
import com.ag.agaicodemother.model.dto.safty.SafetyReviewRecordQueryRequest;
import com.ag.agaicodemother.model.entity.SafetyReviewRecord;
import com.ag.agaicodemother.service.SafetyReviewRecordService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 内容安全审查记录 服务层实现。
 */
@Service
@Slf4j
public class SafetyReviewRecordServiceImpl extends ServiceImpl<SafetyReviewRecordMapper, SafetyReviewRecord> implements SafetyReviewRecordService {

    @Override
    public boolean saveRecord(SafetyReviewRecord record) {
        try {
            return this.save(record);
        } catch (Exception e) {
            // 审查记录保存失败不能影响主流程，只记录日志
            log.error("保存内容安全审查记录失败", e);
            return false;
        }
    }

    @Override
    public Page<SafetyReviewRecord> pageRecords(SafetyReviewRecordQueryRequest queryRequest) {
        long pageNum = queryRequest.getPageNum();
        long pageSize = queryRequest.getPageSize();
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (queryRequest.getUserId() != null) {
            queryWrapper.eq("userId", queryRequest.getUserId());
        }
        if (queryRequest.getAppId() != null) {
            queryWrapper.eq("appId", queryRequest.getAppId());
        }
        if (StrUtil.isNotBlank(queryRequest.getDetectionType())) {
            queryWrapper.eq("detectionType", queryRequest.getDetectionType());
        }
        if (StrUtil.isNotBlank(queryRequest.getHandleResult())) {
            queryWrapper.eq("handleResult", queryRequest.getHandleResult());
        }
        queryWrapper.orderBy("createTime", false);
        return this.getMapper().paginate(new Page<>(pageNum, pageSize), queryWrapper);
    }
}