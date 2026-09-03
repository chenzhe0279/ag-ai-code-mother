package com.ag.agaicodemother.core.parser;

import com.ag.agaicodemother.ai.model.HtmlCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 单文件代码解析器
 *
 */
public class HtmlCodeParser implements CodeParser<HtmlCodeResult> {

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    // 匹配 “描述：xxxx” 或 “description: xxxx”，直到行尾
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("(?:描述|description)\\s*[:：]\\s*([^\\n\\r]+)", Pattern.CASE_INSENSITIVE);
    @Override
    public HtmlCodeResult parseCode(String codeContent) {
        HtmlCodeResult result = new HtmlCodeResult();
        // 提取 HTML 代码
        String htmlCode = extractHtmlCode(codeContent);
        String description = extractDescription(codeContent);
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        } else {
            // 如果没有找到代码块，将整个内容作为HTML
            result.setHtmlCode(codeContent.trim());
        }
        if (description != null && !description.trim().isEmpty()) {
            result.setDescription(description.trim());
        } else {
            // 不要塞整段代码，回退到"暂无描述"或用户消息
            result.setDescription("暂无描述");
        }
        return result;
    }

    /**
     * 提取HTML代码内容
     *
     * @param content 原始内容
     * @return HTML代码
     */
    private String extractHtmlCode(String content) {
        Matcher matcher = HTML_CODE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String extractDescription(String content) {
        // 先去掉所有 ```...``` 代码块，避免在代码内部误匹配到 JS/HTML 行
        String prose = content.replaceAll("```[\\s\\S]*?```", "");
        Matcher matcher = DESCRIPTION_PATTERN.matcher(prose);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
