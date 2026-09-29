package com.ag.agaicodemother.langgraph4j.node;
import com.ag.agaicodemother.langgraph4j.ai.ImageCollectionPlanService;
import com.ag.agaicodemother.langgraph4j.ai.ImageCollectionService;
import com.ag.agaicodemother.langgraph4j.model.ImageCollectionPlan;
import com.ag.agaicodemother.langgraph4j.model.ImageResource;
import com.ag.agaicodemother.langgraph4j.state.WorkflowContext;
import com.ag.agaicodemother.langgraph4j.tools.ImageSearchTool;
import com.ag.agaicodemother.langgraph4j.tools.LogoGeneratorTool;
import com.ag.agaicodemother.langgraph4j.tools.MermaidDiagramTool;
import com.ag.agaicodemother.langgraph4j.tools.UndrawIllustrationTool;
import com.ag.agaicodemother.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片收集节点
 */
@Slf4j
public class ImageCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            // 从 LangGraph4j 的工作流状态（state）中反序列化出上下文对象 WorkflowContext，
            // 该上下文贯穿整个工作流，保存了各节点之间共享的数据
            WorkflowContext context = WorkflowContext.getContext(state);
            // 从上下文中取出用户的原始需求提示词（prompt），作为后续 AI 规划图片收集方案的输入
            String originalPrompt = context.getOriginalPrompt();
            // 创建最终收集结果的容器列表，用于汇总所有异步任务返回的图片资源
            List<ImageResource> collectedImages = new ArrayList<>();

            // 使用 try-catch 包裹整个收集流程：图片收集属于"尽力而为"的增强环节，
            // 即使失败也不应中断整个代码生成工作流
            try {
                // 第一步：获取图片收集计划
                // 通过 Spring 上下文工具类获取图片收集计划服务的 Bean
                //（因为本类不是 Spring 管理的 Bean，无法直接使用 @Autowired 注入）
                ImageCollectionPlanService planService = SpringContextUtil.getBean(ImageCollectionPlanService.class);
                // 调用 AI 服务，根据用户原始提示词分析出需要哪些类型的图片，
                // 返回一个结构化的"图片收集计划"（包含内容图、插画、架构图、Logo 四类任务列表）
                ImageCollectionPlan plan = planService.planImageCollection(originalPrompt);
                // 记录日志：计划已生成，即将开始并发执行各类收集任务
                log.info("获取到图片收集计划，开始并发执行");

                // 第二步：并发执行各种图片收集任务
                // 创建 Future 列表，用于持有所有异步任务的句柄，后续统一等待和取结果
                List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
                // 并发执行内容图片搜索
                // 判空保护：AI 规划的计划中可能不包含内容图片任务
                if (plan.getContentImageTasks() != null) {
                    // 获取图片搜索工具的 Bean（基于搜索引擎检索真实网络图片）
                    ImageSearchTool imageSearchTool = SpringContextUtil.getBean(ImageSearchTool.class);
                    // 遍历计划中的每一个内容图片搜索任务（每个任务包含一个搜索关键词 query）
                    for (ImageCollectionPlan.ImageSearchTask task : plan.getContentImageTasks()) {
                        //CompletableFuture.supplyAsync : 创建一个有返回值的任务
                        // 将每个搜索任务提交到 ForkJoinPool 公共线程池异步执行，
                        // 任务返回该关键词搜索到的图片资源列表，Future 句柄存入 futures 集合
                        futures.add(CompletableFuture.supplyAsync(() ->
                                imageSearchTool.searchContentImages(task.query())));
                    }
                }
                // 并发执行插画图片搜索
                // 判空保护：计划中可能不包含插画任务
                if (plan.getIllustrationTasks() != null) {
                    // 获取 Undraw 插画工具的 Bean（从 undraw 开源插画库检索风格化插画）
                    UndrawIllustrationTool illustrationTool = SpringContextUtil.getBean(UndrawIllustrationTool.class);
                    // 遍历计划中的每一个插画搜索任务
                    for (ImageCollectionPlan.IllustrationTask task : plan.getIllustrationTasks()) {
                        // 将插画搜索任务提交为异步任务，返回匹配到的插画资源列表
                        futures.add(CompletableFuture.supplyAsync(() ->
                                illustrationTool.searchIllustrations(task.query())));
                    }
                }
                // 并发执行架构图生成
                // 判空保护：计划中可能不包含架构图任务
                if (plan.getDiagramTasks() != null) {
                    // 获取 Mermaid 图表工具的 Bean（将 Mermaid 语法代码渲染为图片）
                    MermaidDiagramTool diagramTool = SpringContextUtil.getBean(MermaidDiagramTool.class);
                    // 遍历计划中的每一个架构图生成任务（每个任务包含 mermaid 代码和图表描述）
                    for (ImageCollectionPlan.DiagramTask task : plan.getDiagramTasks()) {
                        // 将图表渲染任务提交为异步任务，把 AI 生成的 mermaid 代码渲染成图片资源
                        futures.add(CompletableFuture.supplyAsync(() ->
                                diagramTool.generateMermaidDiagram(task.mermaidCode(), task.description())));
                    }
                }
                // 并发执行Logo生成
                // 判空保护：计划中可能不包含 Logo 任务
                if (plan.getLogoTasks() != null) {
                    // 获取 Logo 生成工具的 Bean（根据文字描述生成应用 Logo 图片）
                    LogoGeneratorTool logoTool = SpringContextUtil.getBean(LogoGeneratorTool.class);
                    // 遍历计划中的每一个 Logo 生成任务
                    for (ImageCollectionPlan.LogoTask task : plan.getLogoTasks()) {
                        // 将 Logo 生成任务提交为异步任务，返回生成的 Logo 图片资源列表
                        futures.add(CompletableFuture.supplyAsync(() ->
                                logoTool.generateLogos(task.description())));
                    }
                }

                // 等待所有任务完成并收集结果
                // CompletableFuture.allOf：将 futures 列表组合成一个新的 Future，
                // 当且仅当所有子任务都完成（正常或异常）时它才完成；
                // toArray(new CompletableFuture[0]) 是 List 转数组的惯用写法
                CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0]));
                // join() 阻塞当前线程，直到所有并发任务全部执行完毕
                allTasks.join();
                // 收集所有结果
                // 遍历每一个已完成的 Future，逐个取出其返回的图片资源列表
                for (CompletableFuture<List<ImageResource>> future : futures) {
                    // get() 获取任务返回值（此时任务已完成，不会阻塞）；
                    // 若任务执行中抛出异常，get() 会抛 ExecutionException，由外层 catch 捕获
                    List<ImageResource> images = future.get();
                    // 判空保护：某些工具在无匹配结果时可能返回 null
                    if (images != null) {
                        // 将该任务收集到的图片全部追加到总结果列表中
                        collectedImages.addAll(images);
                    }
                }
                // 记录日志：所有并发任务完成，输出最终收集到的图片总数
                log.info("并发图片收集完成，共收集到 {} 张图片", collectedImages.size());
            } catch (Exception e) {
                // 捕获整个收集过程中的任何异常（AI 规划失败、任务执行异常、线程中断等），
                // 仅记录错误日志而不向上抛出，保证工作流可以继续执行后续节点
                log.error("图片收集失败: {}", e.getMessage(), e);
            }
            // 更新状态
            // 将上下文中的"当前步骤"标记为图片收集，供前端展示工作流进度使用
            context.setCurrentStep("图片收集");
            // 将收集到的所有图片资源写入上下文（失败时为空列表，不影响后续节点）
            context.setImageList(collectedImages);
            // 将更新后的上下文序列化回工作流状态并返回，
            // 作为本节点的输出，传递给 LangGraph4j 的下一个节点
            return WorkflowContext.saveContext(context);
        });
    }
}
