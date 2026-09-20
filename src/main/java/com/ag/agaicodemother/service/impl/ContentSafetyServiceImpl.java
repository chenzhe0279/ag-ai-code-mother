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

    /**
     * 内容安全检测主入口：对用户输入执行「静态预筛 + AI 语义检测」双层校验，
     * 命中敏感内容时抛出 BusinessException 拦截请求，并写入审计记录。
     *
     * @param message   待检测的用户消息内容
     * @param loginUser 当前登录用户（可能为 null，如匿名场景），用于审计记录归属
     * @param appId     关联的应用 ID（可能为 null），用于审计记录归属
     * @param request   当前 HTTP 请求，用于解析客户端真实 IP（可能为 null）
     */
    @Override
    public void checkContentSafety(String message, User loginUser, Long appId, HttpServletRequest request) {
        // 空消息直接放行（非空/长度校验由业务入口负责，此处不重复拦截）
        // StrUtil.isBlank 同时覆盖 null、空串、纯空白字符三种情况
        if (StrUtil.isBlank(message)) {
            return;
        }
        // 提取审计所需的公共上下文
        // loginUser 可能为 null，此处做空安全处理，避免后续调用 getId() 抛 NPE
        Long userId = loginUser == null ? null : loginUser.getId();
        // 解析客户端 IP，供审计记录使用（内部会处理 request 为空的情况）
        String clientIp = resolveClientIp(request);

        // ==================== 第一层：静态关键词 / 正则快速预筛 ====================
        // 命中成本极低，优先拦截最典型的注入攻击，命中即无需再调用大模型（省时省钱）
        // matchStaticRule 命中时返回具体的关键词或正则串，未命中返回 null
        String staticHitRule = matchStaticRule(message);
        if (staticHitRule != null) {
            // 写审查记录（static + blocked），失败不影响拦截主流程
            // 参数含义：检测方式=static，触发规则=命中内容，风险类别=null（静态无法细分），
            //          风险等级=high（默认高危），AI 理由=null，处理结果=blocked
            saveRecord(userId, appId, message, DETECTION_TYPE_STATIC, staticHitRule,
                    null, RISK_LEVEL_HIGH, null, HANDLE_RESULT_BLOCKED, clientIp);
            // 记录告警日志，便于事后排查攻击来源与命中规则
            log.warn("静态敏感词拦截：userId={}, appId={}, 命中规则={}", userId, appId, staticHitRule);
            // 抛出业务异常，中断请求，交由全局异常处理器返回敏感内容错误码
            throw new BusinessException(ErrorCode.SENSITIVE_CONTENT);
        }

        // ==================== 第二层：AI 大模型语义检测（同步调用）====================
        // 识别静态匹配无法覆盖的隐晦表达、语义级攻击
        // result 承载大模型返回的检测结论（是否敏感、类别、风险等级、理由）
        SensitiveCheckResult result;
        try {
            // 同步调用大模型，直接阻塞等待检测结果
            // 通过工厂获取按 AI 代码生成场景路由的检测服务实例
            SensitiveContentCheckService sensitiveContentCheckService = sensitiveContentCheckServiceFactory.createAiCodeGenTypeRoutingService();
            // 执行实际检测，得到结构化结论
            result = sensitiveContentCheckService.checkContent(message);
        } catch (Exception e) {
            // 异常：降级放行（fail-open），记录审计，避免大模型抖动阻断正常业务
            // 处理结果标记为 failed_open，AI 理由中保留异常信息用于追踪
            saveRecord(userId, appId, message, DETECTION_TYPE_AI, null,
                    null, null, "AI 检测异常，降级放行：" + e.getMessage(),
                    HANDLE_RESULT_FAILED_OPEN, clientIp);
            // 打印错误日志并携带异常堆栈，便于定位大模型调用失败原因
            log.error("AI 敏感内容检测异常，降级放行：userId={}, appId={}", userId, appId, e);
            // 降级放行：直接返回，不拦截正常业务
            return;
        }

        // AI 判定为敏感 → 写审查记录（ai + blocked）并拦截
        // 先判空 result，再调用 isSensitive()，避免大模型返回 null 时的 NPE
        if (result != null && result.isSensitive()) {
            // 写入 AI 拦截记录，携带模型给出的类别、风险等级与判定理由
            saveRecord(userId, appId, message, DETECTION_TYPE_AI, null,
                    result.getCategory(), result.getRiskLevel(), result.getReason(),
                    HANDLE_RESULT_BLOCKED, clientIp);
            // 记录告警日志，输出模型判定详情，便于人工复核
            log.warn("AI 敏感内容拦截：userId={}, appId={}, 类别={}, 等级={}, 理由={}",
                    userId, appId, result.getCategory(), result.getRiskLevel(), result.getReason());
            // 抛出业务异常，中断请求
            throw new BusinessException(ErrorCode.SENSITIVE_CONTENT);
        }
        // AI 判定安全 → 放行，正常请求不落库，避免刷爆审查表
    }

    /**
     * 静态规则匹配：命中返回命中的关键词或正则，未命中返回 null
     *
     * @param message 待检测消息（调用方已保证非空白）
     * @return 命中的敏感词原文或正则表达式串；未命中返回 null
     */
    private String matchStaticRule(String message) {
        // 预先转小写，实现大小写不敏感的关键词匹配
        String lowerMessage = message.toLowerCase();
        // 关键词包含匹配：遍历敏感词列表，只要消息中包含任一敏感词即命中
        for (String word : SENSITIVE_WORDS) {
            // 敏感词同样转小写后再做 contains 比较，保证中英文混合场景一致
            if (lowerMessage.contains(word.toLowerCase())) {
                return word;
            }
        }
        // 注入攻击正则匹配：遍历预编译的正则模式，覆盖更复杂的攻击句式
        for (Pattern pattern : INJECTION_PATTERNS) {
            // 用原始消息（保留大小写，正则内部已用 (?i) 忽略大小写）构建匹配器
            Matcher matcher = pattern.matcher(message);
            // find() 表示消息中任意位置存在匹配子串即命中
            if (matcher.find()) {
                // 返回命中的正则表达式串，便于审计记录定位具体规则
                return pattern.pattern();
            }
        }
        // 两层静态规则均未命中，返回 null 交由后续 AI 检测
        return null;
    }

    /**
     * 构建并保存审查记录（保存失败只记日志，不影响拦截/放行主流程）
     *
     * @param userId        用户 ID
     * @param appId         应用 ID
     * @param message       原始消息（入库前会截断）
     * @param detectionType 检测方式：static / ai
     * @param triggerRule   触发的静态规则（AI 检测时为 null）
     * @param riskCategory  风险类别（AI 检测返回）
     * @param riskLevel     风险等级
     * @param aiReason      AI 判定理由或异常说明
     * @param handleResult  处理结果：blocked / failed_open
     * @param clientIp      客户端 IP
     */
    private void saveRecord(Long userId, Long appId, String message, String detectionType,
                            String triggerRule, String riskCategory, String riskLevel,
                            String aiReason, String handleResult, String clientIp) {
        // 使用建造者模式组装审查记录实体，逐字段填充审计信息
        SafetyReviewRecord record = SafetyReviewRecord.builder()
                .userId(userId)                                  // 归属用户
                .appId(appId)                                    // 归属应用
                .userMessage(truncateMessage(message))           // 截断后的消息内容，防止超长撑爆字段
                .detectionType(detectionType)                    // 检测方式
                .triggerRule(StrUtil.maxLength(triggerRule, 500))// 触发规则限长 500，避免正则串过长
                .riskCategory(riskCategory)                      // 风险类别
                .riskLevel(riskLevel)                            // 风险等级
                .aiReason(aiReason)                              // AI 理由 / 异常信息
                .handleResult(handleResult)                      // 处理结果
                .clientIp(clientIp)                              // 客户端 IP
                .build();                                        // 构建实体对象
        // 落库保存（该服务内部已捕获异常，保证不影响主流程）
        safetyReviewRecordService.saveRecord(record);
    }

    /**
     * 消息入库截断，避免超长内容撑爆字段
     *
     * @param message 原始消息
     * @return 长度超过上限时返回截断后的子串，否则原样返回；入参为 null 时返回 null
     */
    private String truncateMessage(String message) {
        // 空值直接返回，避免后续 length()/substring() 抛 NPE
        if (message == null) {
            return null;
        }
        // 超过最大长度则截取前 MAX_RECORD_MESSAGE_LENGTH 个字符，否则原样返回
        return message.length() > MAX_RECORD_MESSAGE_LENGTH
                ? message.substring(0, MAX_RECORD_MESSAGE_LENGTH) : message;
    }

    /**
     * 解析客户端 IP：优先用传入的 request，为空时回退到 RequestContextHolder
     * （与 RateLimitAspect.getClientIP 保持一致的取值优先级）
     *
     * @param request 当前 HTTP 请求，可能为 null
     * @return 客户端真实 IP，无法解析时返回 "unknown"
     */
    private String resolveClientIp(HttpServletRequest request) {
        // 先以传入的 request 为准
        HttpServletRequest req = request;
        // 传入为空时，尝试从线程上下文获取当前请求
        if (req == null) {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            // 上下文中也没有请求（如异步线程），无法解析，返回 unknown
            if (attributes == null) {
                return "unknown";
            }
            // 从上下文属性中取出真实请求对象
            req = attributes.getRequest();
        }
        // 优先级 1：X-Forwarded-For，经过反向代理时携带的原始客户端 IP
        String ip = req.getHeader("X-Forwarded-For");
        // 优先级 2：上一步为空或被标记为 unknown 时，尝试 X-Real-IP
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        // 优先级 3：仍无效时，回退到 TCP 连接的远端地址
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        // 多级代理时取第一个非 unknown 的 IP
        // X-Forwarded-For 形如 "client, proxy1, proxy2"，第一个即为最初客户端
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        // 兜底：全部解析失败时返回 unknown，保证字段非空
        return ip != null ? ip : "unknown";
    }
}