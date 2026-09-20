package com.ag.agaicodemother.ai;

import com.ag.agaicodemother.ai.model.SensitiveCheckResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * AI 敏感内容检测服务
 */
public interface SensitiveContentCheckService {

    /**
     * 调用大模型对用户消息做语义级安全检测
     *
     * @param userMessage 用户输入消息
     * @return 检测结果（是否敏感、类别、等级、理由）
     */
    @SystemMessage(fromResource = "prompt/sensitive-check-system-prompt.txt")
    SensitiveCheckResult checkContent(@UserMessage String userMessage);
}