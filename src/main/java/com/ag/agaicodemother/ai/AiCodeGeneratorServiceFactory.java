package com.ag.agaicodemother.ai;


import com.ag.agaicodemother.ai.tools.*;
import com.ag.agaicodemother.exception.BusinessException;
import com.ag.agaicodemother.exception.ErrorCode;
import com.ag.agaicodemother.model.enums.CodeGenTypeEnum;
import com.ag.agaicodemother.service.ChatHistoryService;
import com.ag.agaicodemother.utils.SpringContextUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI服务创建工厂
 */
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    //@Resource
    //private StreamingChatModel openAiStreamingChatModel;

    //@Resource
    //private StreamingChatModel reasoningStreamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 appId 获取服务（带缓存）
     *//*
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        //返回与 key 关联的值，如果不存在则通过 mappingFunction 计算并缓存后返回
        return serviceCache.get(appId, this::createAiCodeGeneratorService);
    }*/

    /**
     * 根据 appId codeGenType versionId 获取服务（带缓存）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType, Integer versionId) {
        //获取缓存 Key
        String cacheKey = buildCacheKey(appId, codeGenType, versionId);
        //返回与 key 关联的值，如果不存在则通过 mappingFunction 计算并缓存后返回
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }
    /**
     * 创建新的 AI 服务实例
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        // 根据代码生成类型选择不同的模型配置
        return switch (codeGenType) {
            // Vue 项目生成使用推理模型
            case VUE_PROJECT -> {
                // 使用多例模式的 StreamingChatModel 解决并发问题
                StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                    .streamingChatModel(reasoningStreamingChatModel)
                    .chatMemoryProvider(memoryId -> chatMemory)
                    .tools(toolManager.getAllTools())
                    //处理工具调用幻觉问题
                    .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                            toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                    ))
                    .build();
            }
            // HTML 和多文件生成使用默认模型
            case HTML, MULTI_FILE -> {
                // 使用多例模式的 StreamingChatModel 解决并发问题
                StreamingChatModel openAiStreamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(openAiStreamingChatModel)
                    .chatMemory(chatMemory)
                    .build();
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }


    /**
     * 创建 AI 代码生成器服务
     * 仅供"生成应用名称"等一次性调用使用（AppServiceImpl.generateAppNameByAi）
     * 注意：接口中存在 @MemoryId 方法（generateVueProjectCodeStream），
     * LangChain4j 构建 AiServices 时强制要求配置 chatMemoryProvider，
     * 即使本 Bean 只调用 generateAppName 也必须提供，否则构建期直接抛异常
     * @return
     */

    public AiCodeGeneratorService generateAppName(){
       // 使用多例模式的 StreamingChatModel 解决并发问题
       StreamingChatModel openAiStreamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
       return AiServices.builder(AiCodeGeneratorService.class)
               .chatModel(chatModel)
               .streamingChatModel(openAiStreamingChatModel)
               // 必须提供：接口含 @MemoryId 方法，缺失会在 build() 时校验失败
               // 应用名生成不依赖历史对话，这里按需创建临时记忆即可
               .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                       .id(memoryId)
                       .chatMemoryStore(redisChatMemoryStore)
                       .maxMessages(20)
                       .build())
               .build();
    }
    /**
     * 构造缓存 Key
     * @param appId
     * @param codeGenType
     * @param versionId
     * @return
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType, Integer versionId) {
        return appId + ":" + codeGenType + ":" + versionId;
    }
}
