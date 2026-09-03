package com.ag.agaicodemother.core;

import com.ag.agaicodemother.ai.model.HtmlCodeResult;
import com.ag.agaicodemother.ai.model.MultiFileCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码解析器
 * 提供静态方法解析不同类型的代码内容
 *
 */
public class CodeParser {

    // 定义用于匹配 HTML 代码块的正则表达式：
    // ```html 表示以三个反引号和 html 开头；
    // \\s* 表示匹配任意数量的空白字符（空格、制表符等）；
    // \\n 表示匹配一个换行符；
    // ([\\s\\S]*?) 是非贪婪捕获组，用于匹配任意字符（包括换行）且尽可能少地匹配，即捕获代码块内容；
    // ``` 表示以三个反引号结束；
    // Pattern.CASE_INSENSITIVE 表示匹配时忽略大小写。
    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    // 定义用于匹配 CSS 代码块的正则表达式：
    // ```css 表示以三个反引号和 css 开头；
    // \\s* 匹配任意空白字符；
    // \\n 匹配一个换行符；
    // ([\\s\\S]*?) 非贪婪捕获代码块中的 CSS 内容；
    // ``` 表示以三个反引号结束；
    // 同样忽略大小写。
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    // 定义用于匹配 JavaScript 代码块的正则表达式：
    // (?:js|javascript) 是非捕获分组，表示匹配 js 或 javascript 标签；
    // \\s* 匹配标签后的任意空白字符；
    // \\n 匹配换行符；
    // ([\\s\\S]*?) 非贪婪捕获代码块中的 JS 内容；
    // ``` 表示以三个反引号结束；
    // 忽略大小写，因此 JS、JavaScript 等写法都能匹配。
    private static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    // 匹配 “描述：xxxx” 或 “description: xxxx”，直到行尾
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("(?:描述|description)\\s*[:：]\\s*([^\\n\\r]+)", Pattern.CASE_INSENSITIVE);

    /**
     * 解析 HTML 单文件代码
     */
    public static HtmlCodeResult parseHtmlCode(String codeContent) {
        // 创建一个空的 HtmlCodeResult 对象，用来保存解析后的 HTML 代码
        HtmlCodeResult result = new HtmlCodeResult();

        // 调用 extractHtmlCode 方法，尝试从原始内容中提取 HTML 代码块
        String htmlCode = extractHtmlCode(codeContent);
        String description = extractDescription(codeContent);

        // 判断提取到的 HTML 代码是否不为空，并且去除首尾空白后仍有内容
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            // 提取成功，将去除首尾空白后的 HTML 代码设置到结果对象中
            result.setHtmlCode(htmlCode.trim());
        } else {
            // 如果没有找到符合格式的 HTML 代码块，则将原始内容去除首尾空白后作为 HTML 代码兜底返回
            result.setHtmlCode(codeContent.trim());
        }

        if (description != null && !description.trim().isEmpty()) {
            result.setDescription(description.trim());
        } else {
            // 不要塞整段代码，回退到"暂无描述"或用户消息
            result.setDescription("暂无描述");
        }

        // 返回包含 HTML 代码的解析结果
        return result;
    }

    /**
     * 解析多文件代码（HTML + CSS + JS）
     */
    public static MultiFileCodeResult parseMultiFileCode(String codeContent) {
        // 创建一个空的多文件代码结果对象，用来保存 HTML、CSS 和 JS 代码
        MultiFileCodeResult result = new MultiFileCodeResult();

        // 使用 HTML 正则表达式从原始内容中提取 HTML 代码块
        String htmlCode = extractCodeByPattern(codeContent, HTML_CODE_PATTERN);

        // 使用 CSS 正则表达式从原始内容中提取 CSS 代码块
        String cssCode = extractCodeByPattern(codeContent, CSS_CODE_PATTERN);

        // 使用 JS 正则表达式从原始内容中提取 JavaScript 代码块
        String jsCode = extractCodeByPattern(codeContent, JS_CODE_PATTERN);

        String description = extractDescription(codeContent);

        // 如果提取到的 HTML 代码不为空且去除首尾空白后有内容，则设置到结果对象中
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        }

        // 如果提取到的 CSS 代码不为空且去除首尾空白后有内容，则设置到结果对象中
        if (cssCode != null && !cssCode.trim().isEmpty()) {
            result.setCssCode(cssCode.trim());
        }

        // 如果提取到的 JS 代码不为空且去除首尾空白后有内容，则设置到结果对象中
        if (jsCode != null && !jsCode.trim().isEmpty()) {
            result.setJsCode(jsCode.trim());
        }
        if (description != null && !description.trim().isEmpty()) {
            result.setDescription(description.trim());
        } else {
            // 不要塞整段代码，回退到"暂无描述"或用户消息
            result.setDescription("暂无描述");
        }
        // 返回包含 HTML、CSS 和 JS 代码的多文件解析结果
        return result;
    }

    /**
     * 提取HTML代码内容
     *
     * @param content 原始内容
     * @return HTML代码
     */
    private static String extractHtmlCode(String content) {
        // 使用预编译的 HTML 正则模式对传入内容创建 Matcher 匹配器
        Matcher matcher = HTML_CODE_PATTERN.matcher(content);

        // 检查内容中是否存在匹配的 HTML 代码块
        if (matcher.find()) {
            // 匹配成功，返回第一个捕获组，即代码块内部的 HTML 代码（不包含三个反引号标记和 html 标签）
            return matcher.group(1);
        }

        // 未找到匹配的 HTML 代码块，返回 null
        return null;
    }

    /**
     * 根据正则模式提取代码
     *
     * @param content 原始内容
     * @param pattern 正则模式
     * @return 提取的代码
     */
    private static String extractCodeByPattern(String content, Pattern pattern) {
        // 使用传入的正则模式对原始内容创建 Matcher 匹配器
        Matcher matcher = pattern.matcher(content);

        // 检查内容中是否存在符合该正则模式的代码块
        if (matcher.find()) {
            // 匹配成功，返回第一个捕获组，即代码块内的实际代码内容
            return matcher.group(1);
        }

        // 未找到匹配内容，返回 null
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
