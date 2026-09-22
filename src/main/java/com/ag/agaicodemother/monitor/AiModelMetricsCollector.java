package com.ag.agaicodemother.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 监控指标收集器
 */
@Component
@Slf4j
public class AiModelMetricsCollector {

    // 注入 Micrometer 的指标注册中心，所有指标都要通过它注册并对外暴露（如 Prometheus）
    @Resource
    private MeterRegistry meterRegistry;

    // 缓存已创建的指标，避免重复创建（按指标类型分离缓存）
    // 每个缓存的 key 由维度标签拼接而成，value 为对应的 Counter/Timer 实例
    // 请求次数计数器缓存：key = userId_appId_modelName_status
    private final ConcurrentMap<String, Counter> requestCountersCache = new ConcurrentHashMap<>();
    // 错误次数计数器缓存：key = userId_appId_modelName_errorMessage
    private final ConcurrentMap<String, Counter> errorCountersCache = new ConcurrentHashMap<>();
    // Token 消耗计数器缓存：key = userId_appId_modelName_tokenType
    private final ConcurrentMap<String, Counter> tokenCountersCache = new ConcurrentHashMap<>();
    // 响应耗时计时器缓存：key = userId_appId_modelName
    private final ConcurrentMap<String, Timer> responseTimersCache = new ConcurrentHashMap<>();

    /**
     * 记录请求次数。
     * <p>
     * 每次 AI 模型调用都可调用本方法计数，通过 status 标签区分请求结果（如成功/失败）。
     *
     * @param userId    用户 ID，标识发起请求的用户
     * @param appId     应用 ID，标识请求所属的应用
     * @param modelName 模型名称，即本次调用的大模型标识
     * @param status    请求状态（如 success / fail）
     */
    public void recordRequest(String userId, String appId, String modelName, String status) {
        // 将四个维度拼接成唯一缓存 key，确保相同维度组合复用同一个 Counter
        String key = String.format("%s_%s_%s_%s", userId, appId, modelName, status);
        // computeIfAbsent：原子操作，缓存中不存在该 key 时才创建并注册指标，存在则直接返回缓存实例
        Counter counter = requestCountersCache.computeIfAbsent(key, k ->
                // 构建名为 ai_model_requests_total 的计数器
                Counter.builder("ai_model_requests_total")
                        // 指标描述，便于在监控面板中展示
                        .description("AI模型总请求次数")
                        // 附加维度标签：用户 ID
                        .tag("user_id", userId)
                        // 附加维度标签：应用 ID
                        .tag("app_id", appId)
                        // 附加维度标签：模型名称
                        .tag("model_name", modelName)
                        // 附加维度标签：请求状态
                        .tag("status", status)
                        // 注册到注册中心并返回该指标
                        .register(meterRegistry)
        );
        // 计数器加 1，代表发生了一次请求
        counter.increment();
    }

    /**
     * 记录错误次数。
     * <p>
     * 当 AI 模型调用出现异常时调用，通过 error_message 标签区分不同类型的错误。
     *
     * @param userId       用户 ID，标识发生错误的请求所属用户
     * @param appId        应用 ID，标识发生错误的请求所属应用
     * @param modelName    模型名称
     * @param errorMessage 错误信息描述（作为标签维度）
     */
    public void recordError(String userId, String appId, String modelName, String errorMessage) {
        // 拼接唯一缓存 key，包含错误信息以区分不同类型错误
        String key = String.format("%s_%s_%s_%s", userId, appId, modelName, errorMessage);
        // 缓存中不存在时才创建并注册错误计数器，否则复用已有实例
        Counter counter = errorCountersCache.computeIfAbsent(key, k ->
                // 构建名为 ai_model_errors_total 的计数器
                Counter.builder("ai_model_errors_total")
                        // 指标描述
                        .description("AI模型错误次数")
                        // 维度标签：用户 ID
                        .tag("user_id", userId)
                        // 维度标签：应用 ID
                        .tag("app_id", appId)
                        // 维度标签：模型名称
                        .tag("model_name", modelName)
                        // 维度标签：错误信息
                        .tag("error_message", errorMessage)
                        // 注册指标并返回
                        .register(meterRegistry)
        );
        // 错误计数加 1
        counter.increment();
    }

    /**
     * 记录 Token 消耗量。
     * <p>
     * 用于统计大模型 Token 使用情况，通过 tokenType 区分不同类型（如输入/输出）。
     *
     * @param userId     用户 ID
     * @param appId      应用 ID
     * @param modelName  模型名称
     * @param tokenType  Token 类型（如 input 输入 / output 输出）
     * @param tokenCount 本次消耗的 Token 数量
     */
    public void recordTokenUsage(String userId, String appId, String modelName,
                                 String tokenType, long tokenCount) {
        // 拼接唯一缓存 key，按 Token 类型区分
        String key = String.format("%s_%s_%s_%s", userId, appId, modelName, tokenType);
        // 缓存中不存在时才创建并注册 Token 计数器，否则复用已有实例
        Counter counter = tokenCountersCache.computeIfAbsent(key, k ->
                // 构建名为 ai_model_tokens_total 的计数器
                Counter.builder("ai_model_tokens_total")
                        // 指标描述
                        .description("AI模型Token消耗总数")
                        // 维度标签：用户 ID
                        .tag("user_id", userId)
                        // 维度标签：应用 ID
                        .tag("app_id", appId)
                        // 维度标签：模型名称
                        .tag("model_name", modelName)
                        // 维度标签：Token 类型
                        .tag("token_type", tokenType)
                        // 注册指标并返回
                        .register(meterRegistry)
        );
        // 计数器按本次消耗数量累加（一次可增加多个）
        counter.increment(tokenCount);
    }

    /**
     * 记录响应耗时。
     * <p>
     * 用于统计 AI 模型调用的响应时间，Timer 会自动记录调用次数、总耗时及最大/平均耗时等派生指标。
     *
     * @param userId    用户 ID
     * @param appId     应用 ID
     * @param modelName 模型名称
     * @param duration  本次调用所花费的时间
     */
    public void recordResponseTime(String userId, String appId, String modelName, Duration duration) {
        // 拼接唯一缓存 key（响应时间维度不含 status，故为三段）
        String key = String.format("%s_%s_%s", userId, appId, modelName);
        // 缓存中不存在时才创建并注册计时器，否则复用已有实例
        Timer timer = responseTimersCache.computeIfAbsent(key, k ->
                // 构建名为 ai_model_response_duration_seconds 的计时器（单位为秒）
                Timer.builder("ai_model_response_duration_seconds")
                        // 指标描述
                        .description("AI模型响应时间")
                        // 维度标签：用户 ID
                        .tag("user_id", userId)
                        // 维度标签：应用 ID
                        .tag("app_id", appId)
                        // 维度标签：模型名称
                        .tag("model_name", modelName)
                        // 注册指标并返回
                        .register(meterRegistry)
        );
        // 记录一次耗时，Timer 内部自动累加统计
        timer.record(duration);
    }
}

