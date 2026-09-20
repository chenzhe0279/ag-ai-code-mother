package com.ag.agaicodemother.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * AI 敏感内容检测结果（结构化输出）
 */
@Description("AI 内容安全检测结果")
@Data
public class SensitiveCheckResult {

    @Description("是否包含敏感或恶意内容，true 表示检测到风险")
    private boolean sensitive;

    @Description("风险类别：prompt_injection提示词注入/illegal违法违规/pornography色情低俗/violence血腥暴力/privacy隐私侵犯/other其他，无风险时为空")
    private String category;

    @Description("风险等级：high高/medium中/low低，无风险时为 low")
    private String riskLevel;

    @Description("判定理由，中文，50字以内")
    private String reason;
}