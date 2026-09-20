package com.ag.agaicodemother.controller;

import com.ag.agaicodemother.common.BaseResponse;
import com.ag.agaicodemother.common.ResultUtils;
import com.ag.agaicodemother.constant.UserConstant;
import com.ag.agaicodemother.exception.BusinessException;
import com.ag.agaicodemother.exception.ErrorCode;
import com.ag.agaicodemother.exception.ThrowUtils;
import com.ag.agaicodemother.model.dto.safty.SafetyReviewRecordQueryRequest;
import com.ag.agaicodemother.model.entity.SafetyReviewRecord;
import com.ag.agaicodemother.model.entity.User;
import com.ag.agaicodemother.service.SafetyReviewRecordService;
import com.ag.agaicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容安全审查记录接口（仅管理员）
 */
@RestController
@RequestMapping("/safetyReview")
public class SafetyReviewController {

    @Resource
    private SafetyReviewRecordService safetyReviewRecordService;

    @Resource
    private UserService userService;

    /**
     * 分页查询审查记录
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<SafetyReviewRecord>> listRecordsByPage(@RequestBody SafetyReviewRecordQueryRequest queryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        // 仅管理员可查看审查记录
        User loginUser = userService.getLoginUser(request);
        if (!UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        long pageSize = queryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 50, ErrorCode.PARAMS_ERROR, "每页最多查询 50 条记录");
        Page<SafetyReviewRecord> page = safetyReviewRecordService.pageRecords(queryRequest);
        return ResultUtils.success(page);
    }
}
