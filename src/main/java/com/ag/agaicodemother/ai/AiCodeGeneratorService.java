package com.ag.agaicodemother.ai;

import com.ag.agaicodemother.ai.model.HtmlCodeResult;
import com.ag.agaicodemother.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.SystemMessage;
import reactor.core.publisher.Flux;

public interface AiCodeGeneratorService {

    /**
     * 生成HTML代码
     * @param userMessage
     * @return AI的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHTMLCode(String userMessage);

    /**
     * 生成多文件代码
     * @param userMessage
     * @return AI的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);

    /**
     * 生成 HTML 代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(String userMessage);

    /**
     * 生成多文件代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);

    /**
     * 根据应用初始描述自动生成应用名称
     *
     * @param userMessage 用户对应用的初始描述
     * @return AI 生成的应用名称结果
     */
    @SystemMessage(fromResource = "prompt/app-name-system-prompt.txt")
    String generateAppName(String userMessage);

}
