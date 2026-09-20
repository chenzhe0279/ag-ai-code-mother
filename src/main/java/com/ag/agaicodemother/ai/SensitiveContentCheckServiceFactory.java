package com.ag.agaicodemother.ai;

import com.ag.agaicodemother.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *  AI 敏感内容检测服务
 *
 */
@Slf4j
@Configuration
public class SensitiveContentCheckServiceFactory {

    /**
     * 创建AI代码生成类型路由服务实例
     */
    public SensitiveContentCheckService createAiCodeGenTypeRoutingService() {
        // 动态获取多例的路由 ChatModel，支持并发
        ChatModel chatModel = SpringContextUtil.getBean("sensitiveContentCheckChatModelPrototype", ChatModel.class);
        return AiServices.builder(SensitiveContentCheckService.class)
                .chatModel(chatModel)
                .build();
    }

    /**
     * 默认提供一个 Bean
     */
    /*@Bean
    public SensitiveContentCheckService sensitiveContentCheckService() {
        return createAiCodeGenTypeRoutingService();
    }*/
}

