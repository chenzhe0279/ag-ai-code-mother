package com.ag.agaicodemother.core.parser;

import com.ag.agaicodemother.ai.model.MultiFileCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多文件代码解析器（HTML + CSS + JS）
 *
 */
public class MultiFileCodeParser implements CodeParser<MultiFileCodeResult> {

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    // 匹配 “描述：xxxx” 或 “description: xxxx”，直到行尾
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("(?:描述|description)\\s*[:：]\\s*([^\\n\\r]+)", Pattern.CASE_INSENSITIVE);
    @Override
    public MultiFileCodeResult parseCode(String codeContent) {
        MultiFileCodeResult result = new MultiFileCodeResult();
        // 提取各类代码
        String htmlCode = extractCodeByPattern(codeContent, HTML_CODE_PATTERN);
        String cssCode = extractCodeByPattern(codeContent, CSS_CODE_PATTERN);
        String jsCode = extractCodeByPattern(codeContent, JS_CODE_PATTERN);
        String description = extractDescription(codeContent);
        // 设置HTML代码
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        }
        // 设置CSS代码
        if (cssCode != null && !cssCode.trim().isEmpty()) {
            result.setCssCode(cssCode.trim());
        }
        // 设置JS代码
        if (jsCode != null && !jsCode.trim().isEmpty()) {
            result.setJsCode(jsCode.trim());
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
     * 根据正则模式提取代码
     *
     * @param content 原始内容
     * @param pattern 正则模式
     * @return 提取的代码
     */
    private String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
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
