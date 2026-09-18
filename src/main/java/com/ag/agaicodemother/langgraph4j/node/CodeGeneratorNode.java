package com.ag.agaicodemother.langgraph4j.node;

import com.ag.agaicodemother.constant.AppConstant;
import com.ag.agaicodemother.core.AiCodeGeneratorFacade;
import com.ag.agaicodemother.langgraph4j.state.WorkflowContext;
import com.ag.agaicodemother.model.enums.CodeGenTypeEnum;
import com.ag.agaicodemother.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class CodeGeneratorNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码生成");
            
            //执行代码生成逻辑
            //获取提示词和代码生成类型
            String userMessage = context.getEnhancedPrompt();
            CodeGenTypeEnum generationType = context.getGenerationType();
            //获取 AI 生成代码服务
            AiCodeGeneratorFacade aiCodeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            log.info("开始生成代码，类型: {} ({})", generationType.getValue(), generationType.getText());
            // 先使用固定的 appId 和 versionId(后续再整合到业务中)
            Long appId = 0L;
            Integer versionId = 1;
            // 调用流式代码生成
            Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(userMessage, generationType, appId, versionId);
            // 同步等待流式输出完成
            codeStream.blockLast(Duration.ofMinutes(10)); // 最多等待 10 分钟
            //设置目录路径
            // 根据类型设置生成目录
            String generatedCodeDir = String.format("%s/%s_%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId, AppConstant.CODE_VERSION_DIR_PREFIX, versionId);
            // 更新状态
            context.setCurrentStep("代码生成");
            context.setGeneratedCodeDir(generatedCodeDir);
            log.info("代码生成完成，目录: {}", generatedCodeDir);
            return WorkflowContext.saveContext(context);
        });
    }
}
