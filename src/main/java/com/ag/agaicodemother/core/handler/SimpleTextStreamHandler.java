package com.ag.agaicodemother.core.handler;

import com.ag.agaicodemother.model.entity.App;
import com.ag.agaicodemother.model.entity.User;
import com.ag.agaicodemother.model.enums.AppGenStatusEnum;
import com.ag.agaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.ag.agaicodemother.service.AppService;
import com.ag.agaicodemother.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 简单文本流处理器
 * 处理 HTML 和 MULTI_FILE 类型的流式响应
 */
@Slf4j
public class SimpleTextStreamHandler {

    @Resource
    private AppService appService;

    /**
     * 处理传统流（HTML, MULTI_FILE）
     * 直接收集完整的文本响应
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        StringBuilder aiResponseBuilder = new StringBuilder();
        return originFlux
                .map(codeChunk -> {
                    aiResponseBuilder.append(codeChunk);
                    return codeChunk;
                })
                // 流正常结束（此时文件已在 Facade 的 doOnComplete 中保存完毕）后置为已成功
                .doOnComplete(() -> {
                    String aiResponse = aiResponseBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    updateGenStatus(appId, AppGenStatusEnum.SUCCEEDED);
                })
                // 流异常时置为失败，并保留错误日志方便排查
                .doOnError(error -> {
                    log.error("AI 代码生成失败, appId={}", appId, error);
                    String aiErrorMessage = "AI回复失败：" + error.getMessage();
                    chatHistoryService.addChatMessage(appId, aiErrorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    updateGenStatus(appId, AppGenStatusEnum.FAILED);
                })
                // 客户端中途关页面/断开 SSE 也视为失败，避免状态永远卡在生成中
                .doOnCancel(() -> updateGenStatus(appId, AppGenStatusEnum.FAILED));
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
