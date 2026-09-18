package com.ag.agaicodemother.langgraph4j.node;

import com.ag.agaicodemother.ai.AiCodeGenTypeRoutingService;
import com.ag.agaicodemother.langgraph4j.state.WorkflowContext;
import com.ag.agaicodemother.model.enums.CodeGenTypeEnum;
import com.ag.agaicodemother.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 智能路由节点
 */
@Slf4j
public class RouterNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 智能路由");
            //执行智能路由逻辑
            CodeGenTypeEnum codeGenTypeEnum;
            try {
                //获取智能路由服务
                AiCodeGenTypeRoutingService typeRoutingService = SpringContextUtil.getBean(AiCodeGenTypeRoutingService.class);
                //根据原始提示词进行智能路由
                codeGenTypeEnum = typeRoutingService.routeCodeGenType(context.getOriginalPrompt());
                log.info("AI智能路由完成，选择类型: {} ({})", codeGenTypeEnum.getValue(), codeGenTypeEnum.getText());
            }catch (Exception e) {
                log.error("AI智能路由失败，使用默认HTML类型: {}", e.getMessage());
                codeGenTypeEnum = CodeGenTypeEnum.HTML;
            }
            // 更新状态
            context.setCurrentStep("智能路由");
            context.setGenerationType(codeGenTypeEnum);
            return WorkflowContext.saveContext(context);
        });
    }
}
