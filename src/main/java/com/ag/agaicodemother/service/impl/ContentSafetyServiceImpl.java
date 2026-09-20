package com.ag.agaicodemother.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ag.agaicodemother.ai.SensitiveContentCheckService;
import com.ag.agaicodemother.ai.SensitiveContentCheckServiceFactory;
import com.ag.agaicodemother.ai.model.SensitiveCheckResult;
import com.ag.agaicodemother.exception.BusinessException;
import com.ag.agaicodemother.exception.ErrorCode;
import com.ag.agaicodemother.model.entity.SafetyReviewRecord;
import com.ag.agaicodemother.model.entity.User;
import com.ag.agaicodemother.service.ContentSafetyService;
import com.ag.agaicodemother.service.SafetyReviewRecordService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内容安全检测服务实现：静态关键词快速预筛 + AI 大模型语义检测
 */
@Service
@Slf4j
public class ContentSafetyServiceImpl implements ContentSafetyService {

    /** 消息内容入库时截断的最大长度 */
    private static final int MAX_RECORD_MESSAGE_LENGTH = 2000;

    /** 检测方式：静态关键词 */
    private static final String DETECTION_TYPE_STATIC = "static";

    /** 检测方式：AI 大模型 */
    private static final String DETECTION_TYPE_AI = "ai";

    /** 处理结果：已拦截 */
    private static final String HANDLE_RESULT_BLOCKED = "blocked";

    /** 处理结果：检测异常降级放行 */
    private static final String HANDLE_RESULT_FAILED_OPEN = "failed_open";

    /** 静态命中时默认的风险等级 */
    private static final String RISK_LEVEL_HIGH = "high";

    /**
     * 静态敏感词（与 PromptSafetyInputGuardrail 同源维护，作为第一层快速预筛）
     */
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
            "忽略之前的指令", "ignore previous instructions", "ignore above",
            "破解", "hack", "绕过", "bypass", "越狱", "jailbreak"
    );

    /**
     * 提示词注入攻击模式
     */
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)ignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?|prompts?)"),
            Pattern.compile("(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)"),
            Pattern.compile("(?i)(?:pretend|act|behave)\\s+(?:as|like)\\s+(?:if|you\\s+are)"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*:")
    );

    @Resource
    private SensitiveContentCheckServiceFactory sensitiveContentCheckServiceFactory;

    @Resource
    private SafetyReviewRecordService safetyReviewRecordService;

    @Override
    public void checkContentSafety(String message, User loginUser, Long appId, HttpServletRequest request) {
        // 空消息直接放行（非空/长度校验由业务入口负责，此处不重复拦截）
        if (StrUtil.isBlank(message)) {
            return;
        }
        // 提取审计所需的公共上下文
        Long userId = loginUser == null ? null : loginUser.getId();
        String clientIp = resolveClientIp(request);

        // ==================== 第一层：静态关键词 / 正则快速预筛 ====================
        // 命中成本极低，优先拦截最典型的注入攻击，命中即无需再调用大模型（省时省钱）
        String staticHitRule = matchStaticRule(message);
        if (staticHitRule != null) {
            // 写审查记录（static + blocked），失败不影响拦截主流程
            saveRecord(userId, appId, message, DETECTION_TYPE_STATIC, staticHitRule,
                    null, RISK_LEVEL_HIGH, null, HANDLE_RESULT_BLOCKED, clientIp);
            log.warn("静态敏感词拦截：userId={}, appId={}, 命中规则={}", userId, appId, staticHitRule);
            throw new BusinessException(ErrorCode.SENSITIVE_CONTENT);
        }

        // ==================== 第二层：AI 大模型语义检测（同步调用）====================
        // 识别静态匹配无法覆盖的隐晦表达、语义级攻击
        SensitiveCheckResult result;
        try {
            // 同步调用大模型，直接阻塞等待检测结果
            SensitiveContentCheckService sensitiveContentCheckService = sensitiveContentCheckServiceFactory.createAiCodeGenTypeRoutingService();
            result = sensitiveContentCheckService.checkContent(message);
        } catch (Exception e) {
            // 异常：降级放行（fail-open），记录审计，避免大模型抖动阻断正常业务
            saveRecord(userId, appId, message, DETECTION_TYPE_AI, null,
                    null, null, "AI 检测异常，降级放行：" + e.getMessage(),
                    HANDLE_RESULT_FAILED_OPEN, clientIp);
            log.error("AI 敏感内容检测异常，降级放行：userId={}, appId={}", userId, appId, e);
            return;
        }

        // AI 判定为敏感 → 写审查记录（ai + blocked）并拦截
        if (result != null && result.isSensitive()) {
            saveRecord(userId, appId, message, DETECTION_TYPE_AI, null,
                    result.getCategory(), result.getRiskLevel(), result.getReason(),
                    HANDLE_RESULT_BLOCKED, clientIp);
            log.warn("AI 敏感内容拦截：userId={}, appId={}, 类别={}, 等级={}, 理由={}",
                    userId, appId, result.getCategory(), result.getRiskLevel(), result.getReason());
            throw new BusinessException(ErrorCode.SENSITIVE_CONTENT);
        }
        // AI 判定安全 → 放行，正常请求不落库，避免刷爆审查表
    }

    /**
     * 静态规则匹配：命中返回命中的关键词或正则，未命中返回 null
     */
    private String matchStaticRule(String message) {
        String lowerMessage = message.toLowerCase();
        // 关键词包含匹配
        for (String word : SENSITIVE_WORDS) {
            if (lowerMessage.contains(word.toLowerCase())) {
                return word;
            }
        }
        // 注入攻击正则匹配
        for (Pattern pattern : INJECTION_PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return pattern.pattern();
            }
        }
        return null;
    }

    /**
     * 构建并保存审查记录（保存失败只记日志，不影响拦截/放行主流程）
     */
    private void saveRecord(Long userId, Long appId, String message, String detectionType,
                            String triggerRule, String riskCategory, String riskLevel,
                            String aiReason, String handleResult, String clientIp) {
        SafetyReviewRecord record = SafetyReviewRecord.builder()
                .userId(userId)
                .appId(appId)
                .userMessage(truncateMessage(message))
                .detectionType(detectionType)
                .triggerRule(StrUtil.maxLength(triggerRule, 500))
                .riskCategory(riskCategory)
                .riskLevel(riskLevel)
                .aiReason(aiReason)
                .handleResult(handleResult)
                .clientIp(clientIp)
                .build();
        safetyReviewRecordService.saveRecord(record);
    }

    /**
     * 消息入库截断，避免超长内容撑爆字段
     */
    private String truncateMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_RECORD_MESSAGE_LENGTH
                ? message.substring(0, MAX_RECORD_MESSAGE_LENGTH) : message;
    }

    /**
     * 解析客户端 IP：优先用传入的 request，为空时回退到 RequestContextHolder
     * （与 RateLimitAspect.getClientIP 保持一致的取值优先级）
     */
    private String resolveClientIp(HttpServletRequest request) {
        HttpServletRequest req = request;
        if (req == null) {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "unknown";
            }
            req = attributes.getRequest();
        }
        String ip = req.getHeader("X-Forwarded-For");
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        // 多级代理时取第一个非 unknown 的 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}