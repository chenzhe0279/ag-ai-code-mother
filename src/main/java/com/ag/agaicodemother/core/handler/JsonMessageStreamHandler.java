package com.ag.agaicodemother.core.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ag.agaicodemother.ai.model.message.AiResponseMessage;
import com.ag.agaicodemother.ai.model.message.StreamMessage;
import com.ag.agaicodemother.ai.model.message.ToolExecutedMessage;
import com.ag.agaicodemother.ai.model.message.ToolRequestMessage;
import com.ag.agaicodemother.ai.tools.BaseTool;
import com.ag.agaicodemother.ai.tools.ToolManager;
import com.ag.agaicodemother.constant.AppConstant;
import com.ag.agaicodemother.core.builder.VueProjectBuilder;
import com.ag.agaicodemother.model.entity.App;
import com.ag.agaicodemother.model.entity.User;
import com.ag.agaicodemother.model.enums.AppGenStatusEnum;
import com.ag.agaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.ag.agaicodemother.model.enums.StreamMessageTypeEnum;
import com.ag.agaicodemother.service.AppService;
import com.ag.agaicodemother.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Lazy
    @Resource
    private AppService appService;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ToolManager toolManager;
    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser, Integer version) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds);
                })
                .filter(StrUtil::isNotEmpty) // 过滤空字串
                .doOnComplete(() -> {
                    // 流式响应完成后，添加 AI 消息到对话历史
                    String aiResponse = chatHistoryStringBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    updateGenStatus(appId, AppGenStatusEnum.SUCCEEDED);
                    String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId + "/" + AppConstant.CODE_VERSION_DIR_PREFIX + version;
                    // 目录里有项目才构建，AI 按失败协议空手结束时不再报错
                    if (FileUtil.exist(projectPath + "/package.json")) {
                        vueProjectBuilder.buildProjectAsync(projectPath);
                    } else {
                        log.warn("目录中没有 package.json，跳过构建: {}", projectPath);
                    }
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    updateGenStatus(appId, AppGenStatusEnum.FAILED);
                })
                // 客户端中途关页面/断开 SSE 也视为失败，避免状态永远卡在生成中
                .doOnCancel(() -> updateGenStatus(appId, AppGenStatusEnum.FAILED));
    }

    /**
     * 解析并收集 TokenStream 数据
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        // 解析 JSON
        // 将当前 JSON 字符串 chunk 解析为通用 StreamMessage 对象，用于识别消息类型
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        // 根据消息类型的 value 值获取对应的枚举实例（AI_RESPONSE / TOOL_REQUEST / TOOL_EXECUTED 等）
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        // 根据消息类型分发处理逻辑
        switch (typeEnum) {
            // AI 普通回复消息：直接透传文本内容
            case AI_RESPONSE -> {
                // 再次将 chunk 解析为具体的 AiResponseMessage 类型，以获取回复数据字段
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                // 获取 AI 回复的文本内容
                String data = aiMessage.getData();
                // 将回复文本追加到聊天历史构建器中，用于流式结束后持久化到数据库
                chatHistoryStringBuilder.append(data);
                // 原样返回回复文本，供前端实时展示
                return data;
            }
            // 工具调用请求消息：表示 AI 准备调用某个工具（如写入文件）
            case TOOL_REQUEST -> {
                // 将 chunk 解析为具体的 ToolRequestMessage 类型，以获取工具调用详情
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                // 获取当前工具调用的唯一 ID
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                // 通过工具 ID 判断该工具的调用是否在之前的分块中已经出现过（去重）
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 首次出现：将工具 ID 加入已见集合，避免后续相同请求重复输出
                    seenToolIds.add(toolId);
                    // 向前端输出一次工具选择提示，前后加换行避免与相邻文本粘连
                    BaseTool tool = toolManager.getTool(toolName);
                    return tool.generateToolRequestResponse();
                } else {
                    // 该工具请求已输出过或 ID 为空，直接返回空串（由外层 filter 过滤）
                    return "";
                }
            }
            // 工具执行完成消息：包含实际写入文件的内容
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                String toolName = toolExecutedMessage.getName();
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                // 根据工具名称获取工具实例并生成相应的结果格式
                BaseTool tool = toolManager.getTool(toolName);
                String result = tool.generateToolExecutedResult(jsonObject);
                // 输出前端和要持久化的内容
                String output = String.format("\n\n%s\n\n", result);
                chatHistoryStringBuilder.append(output);
                return output;
            }
            // 未知消息类型：理论上不会出现，但保留兜底处理
            default -> {
                // 记录不支持的消息类型日志，便于排查问题
                log.error("不支持的消息类型: {}", typeEnum);
                // 返回空字符串，由外层 filter 过滤，不向前端输出任何内容
                return "";
            }
        }
    }

    /**
     * 更新应用生成状态（流式回调中调用，独立于主事务执行）
     */
    private void updateGenStatus(Long appId, AppGenStatusEnum status) {
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setGenStatus(status.getValue());
        appService.updateById(updateApp);
    }
}
