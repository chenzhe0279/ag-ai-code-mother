package com.ag.agaicodemother.ai;

import com.ag.agaicodemother.ai.model.HtmlCodeResult;
import com.ag.agaicodemother.ai.model.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiCodeGeneratorServiceTest {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;
    @Test
    void generateHTMLCode() {
        HtmlCodeResult htmlCode = aiCodeGeneratorService.generateHTMLCode("做个编程爱好者陈爱国的博客，不超过 20 行");
        Assertions.assertNotNull(htmlCode);
    }

    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult multiFileCode = aiCodeGeneratorService.generateMultiFileCode("做个编程爱好者陈爱国的留言板，不超过 20 行");
        Assertions.assertNotNull(multiFileCode);
    }
}