package com.ag.agaicodemother.ai;

import com.ag.agaicodemother.ai.model.HtmlCodeResult;
import com.ag.agaicodemother.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.SystemMessage;

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
}
