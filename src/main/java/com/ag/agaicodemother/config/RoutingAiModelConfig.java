package com.ag.agaicodemother.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
//@ConfigurationProperties(prefix = "langchain4j.open-ai.routing-chat-model")
@Data
public class RoutingAiModelConfig {

    @Value("${langchain4j.open-ai.routing-chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.routing-chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.routing-chat-model.model-name}")
    private String modelName;

    @Value("${langchain4j.open-ai.routing-chat-model.max-tokens}")
    private Integer maxTokens;

    @Value("${langchain4j.open-ai.routing-chat-model.log-requests}")
    private Boolean logRequests = false;

    @Value("${langchain4j.open-ai.routing-chat-model.log-responses}")
    private Boolean logResponses = false;

    /**
     * 创建用于路由判断的ChatModel
     */
    @Bean
    @Scope("prototype")
    public ChatModel routingChatModelPrototype() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .maxTokens(maxTokens)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
