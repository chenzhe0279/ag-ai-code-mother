package com.ag.agaicodemother.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ag.agaicodemother.ai.SensitiveContentCheckService;
import com.ag.agaicodemother.ai.model.SensitiveCheckResult;
import com.ag.agaicodemother.exception.BusinessException;
import com.ag.agaicodemother.exception.ErrorCode;
import com.ag.agaicodemother.model.entity.User;
import com.ag.agaicodemother.service.ContentSafetyService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 内容安全检测服务实现：静态关键词快速预筛 + AI 大模型语义检测
 */
@Service
@Slf4j
public class ContentSafetyServiceImpl implements ContentSafetyService {

    /** 消息内容入库时截断的最大长度 */
    private static final int MAX_RECORD_MESSAGE_LENGTH = 2000;

    /** AI 检测超时时间（秒），超时降级放行 */
    private static final long AI_CHECK_TIMEOUT_SECONDS = 10;

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




    /**
     * AI 检测服务（启动时构建一次，避免每次请求重复构建）
     */
    private SensitiveContentCheckService sensitiveContentCheckService;


    @Override
    public void checkContentSafety(String message, User loginUser, Long appId, HttpServletRequest request) {
        // 第一层：静态关键词/注入模式快速预筛
        Optional<StaticHit> staticHit = staticCheck(message);
        if (staticHit.isPresent()) {
            StaticHit hit = staticHit.get();
            saveRecord(buildRecord(loginUser, appId, message, request)
                    .detectionType("static")
                    .triggerRule(StrUtil.maxLength(hit.rule(), 500))
                    .riskLevel(hit.riskLevel())
                    .handleResult("blocked")
                    .build());
            throw new BusinessException(ErrorCode.SENSITIVE_CONTENT);
        }

        // 第二层：AI 大模型语义检测（可开关）
        if (!aiCheckEnabled) {
            return;
        }
        SensitiveCheckResult result;
        try {
            result = CompletableFuture
                    .supplyAsync(() -> sensitiveContentCheckService.checkContent(message))
                    .get(AI_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 检测服务异常不能阻塞主业务：降级放行并记录，便于观察检测服务健康度
            log.warn("AI 敏感内容检测异常，降级放行。appId={}, userId={}", appId,
                    loginUser != null ? loginUser.getId() : null, e);
            saveRecord(buildRecord(loginUser, appId, message, request)
                    .detectionType("ai")
                    .riskLevel("low")
                    .handleResult("failed_open")
                    .aiReason("检测服务异常：" + StrUtil.maxLength(e.getMessage(), 500))
                    .build());
            return;
        }
        if (result != null && result.isSensitive()) {
            log.warn("AI 检测到敏感内容，已拦截。appId={}, userId={}, category={}, reason={}",
                    appId, loginUser != null ? loginUser.getId() : null, result.getCategory(), result.getReason());
            saveRecord(buildRecord(loginUser, appId, message, request)
                    .detectionType("ai")
                    .triggerRule(StrUtil.maxLength(result.getCategory(), 500))
                    .riskCategory(result.getCategory())
                    .riskLevel(StrUtil.blankToDefault(result.getRiskLevel(), "medium"))
                    .aiReason(result.getReason())
                    .handleResult("blocked")
                    .build());
            throw new BusinessException(ErrorCode.SENSITIVE_CONTENT);
        }
    }

    /**
     * 静态检测：关键词 + 注入模式
     * 关键词命中记 medium，注入模式命中记 high
     */
    private Optional<StaticHit> staticCheck(String message) {
        if (StrUtil.isBlank(message)) {
            return Optional.empty();
        }
        String lowerInput = message.toLowerCase();
        for (String word : SENSITIVE_WORDS) {
            if (lowerInput.contains(word.toLowerCase())) {
                return Optional.of(new StaticHit("关键词: " + word, "medium"));
            }
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(message).find()) {
                return Optional.of(new StaticHit("注入模式: " + pattern.pattern(), "high"));
            }
        }
        return Optional.empty();
    }

    private SafetyReviewRecord.SafetyReviewRecordBuilder buildRecord(User loginUser, Long appId,
                                                                     String message, HttpServletRequest request) {
        return SafetyReviewRecord.builder()
                .userId(loginUser != null ? loginUser.getId() : null)
                .appId(appId)
                .userMessage(StrUtil.maxLength(message, MAX_RECORD_MESSAGE_LENGTH))
                .clientIp(getClientIp(request));
    }

    /**
     * 获取客户端 IP（优先取代理头）
     */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(xff)) {
            // 多级代理时第一个 IP 为客户端真实 IP
            return StrUtil.subBefore(xff, ",", false).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * 静态命中结果
     */
    private record StaticHit(String rule, String riskLevel) {
    }
}