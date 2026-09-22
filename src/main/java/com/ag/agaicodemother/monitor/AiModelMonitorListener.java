package com.ag.agaicodemother.monitor;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 监听器，触发指标收集
 */
@Component
@Slf4j
public class AiModelMonitorListener implements ChatModelListener {

    // 用于存储请求开始时间的键
    private static final String REQUEST_START_TIME_KEY = "request_start_time";
    // 用于监控上下文传递（因为请求和响应事件的触发不是同一个线程）
    private static final String MONITOR_CONTEXT_KEY = "monitor_context";
    
    @Resource
    private AiModelMetricsCollector aiModelMetricsCollector;

    /**
     * 模型请求发起时的回调。
     * <p>
     * 在 LangChain4j 向大模型真正发起调用之前触发，主要职责：
     * 1）记录请求开始时间，供后续计算响应耗时；
     * 2）从当前线程的监控上下文中取出用户/应用信息，并放入请求属性以便跨线程传递；
     * 3）上报一次「started」请求指标。
     *
     * @param requestContext 请求上下文，包含本次请求信息及可写入的属性 Map
     */
    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // 记录请求开始时间：以当前时刻存入请求属性，响应/错误回调时用它计算耗时
        requestContext.attributes().put(REQUEST_START_TIME_KEY, Instant.now());
        // 从监控上下文中获取信息（ThreadLocal 中保存的当前调用方信息）
        MonitorContext context = MonitorContextHolder.getContext();
        // 取出用户 ID
        String userId = context.getUserId();
        // 取出应用 ID
        String appId = context.getAppId();
        // 将监控上下文存入请求属性：因为响应事件可能由不同线程触发，ThreadLocal 会失效，需借属性传递
        requestContext.attributes().put(MONITOR_CONTEXT_KEY, context);
        // 获取模型名称（本次请求调用的具体大模型标识）
        String modelName = requestContext.chatRequest().modelName();
        // 记录请求指标，状态标记为 "started"，表示请求已发起
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "started");
    }

    /**
     * 模型成功返回响应时的回调。
     * <p>
     * 职责：上报「success」请求指标、记录响应耗时、记录 Token 消耗。
     * 由于响应可能与请求处于不同线程，用户/应用信息从请求属性中还原，而非 ThreadLocal。
     *
     * @param responseContext 响应上下文，包含模型返回结果及请求阶段写入的属性 Map
     */
    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        // 从属性中获取监控信息（由 onRequest 方法存储）
        Map<Object, Object> attributes = responseContext.attributes();
        // 从属性中取出监控上下文（跨线程传递，不能用 ThreadLocal）
        MonitorContext context = (MonitorContext) attributes.get(MONITOR_CONTEXT_KEY);
        // 还原用户 ID
        String userId = context.getUserId();
        // 还原应用 ID
        String appId = context.getAppId();
        // 获取模型名称（从响应中取得，确保与实际调用的模型一致）
        String modelName = responseContext.chatResponse().modelName();
        // 记录成功请求，状态标记为 "success"
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "success");
        // 记录响应时间（用开始时间与当前时间之差计算耗时）
        recordResponseTime(attributes, userId, appId, modelName);
        // 记录 Token 使用情况（输入/输出/总量）
        recordTokenUsage(responseContext, userId, appId, modelName);
    }

    /**
     * 模型调用发生异常时的回调。
     * <p>
     * 职责：上报「error」请求指标、记录具体错误信息、记录错误发生前的响应耗时。
     * 注意：此处从 ThreadLocal 监控上下文获取用户/应用信息（与 onResponse 的属性还原方式不同）。
     *
     * @param errorContext 错误上下文，包含原始请求信息、异常对象及请求阶段写入的属性 Map
     */
    @Override
    public void onError(ChatModelErrorContext errorContext) {
        // 从监控上下文中获取信息
        MonitorContext context = MonitorContextHolder.getContext();
        // 取出用户 ID
        String userId = context.getUserId();
        // 取出应用 ID
        String appId = context.getAppId();
        // 获取模型名称和错误类型
        String modelName = errorContext.chatRequest().modelName();
        // 取出异常的错误信息，作为错误指标的标签维度
        String errorMessage = errorContext.error().getMessage();
        // 记录失败请求，状态标记为 "error"
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "error");
        // 记录具体错误次数（按错误信息分类统计）
        aiModelMetricsCollector.recordError(userId, appId, modelName, errorMessage);
        // 记录响应时间（即使是错误响应，也需统计从发起到出错的耗时）
        Map<Object, Object> attributes = errorContext.attributes();
        recordResponseTime(attributes, userId, appId, modelName);
    }


    /**
     * 记录响应时间。
     * <p>
     * 从属性中取出 onRequest 阶段保存的开始时间，与当前时刻做差得到耗时，再上报给指标采集器。
     *
     * @param attributes 请求/响应共享的属性 Map，其中存有请求开始时间
     * @param userId     用户 ID
     * @param appId      应用 ID
     * @param modelName  模型名称
     */
    private void recordResponseTime(Map<Object, Object> attributes, String userId, String appId, String modelName) {
        // 取出请求开始时间（onRequest 中存入）
        Instant startTime = (Instant) attributes.get(REQUEST_START_TIME_KEY);
        // 计算从开始到当前的时间差，即本次调用耗时
        Duration responseTime = Duration.between(startTime, Instant.now());
        // 上报响应耗时指标
        aiModelMetricsCollector.recordResponseTime(userId, appId, modelName, responseTime);
    }

    /**
     * 记录 Token 使用情况。
     * <p>
     * 从响应元数据中提取 TokenUsage，分别上报输入、输出、总计三类 Token 消耗量。
     *
     * @param responseContext 响应上下文，其元数据中包含 Token 使用信息
     * @param userId          用户 ID
     * @param appId           应用 ID
     * @param modelName       模型名称
     */
    private void recordTokenUsage(ChatModelResponseContext responseContext, String userId, String appId, String modelName) {
        // 从响应元数据中获取 Token 使用统计对象
        TokenUsage tokenUsage = responseContext.chatResponse().metadata().tokenUsage();
        // 部分模型可能不返回 Token 信息，需判空避免空指针
        if (tokenUsage != null) {
            // 上报输入 Token（提示词）消耗量
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "input", tokenUsage.inputTokenCount());
            // 上报输出 Token（模型生成内容）消耗量
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "output", tokenUsage.outputTokenCount());
            // 上报总 Token 消耗量
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "total", tokenUsage.totalTokenCount());
        }
    }
}
