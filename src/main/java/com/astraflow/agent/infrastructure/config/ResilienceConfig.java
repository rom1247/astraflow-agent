package com.astraflow.agent.infrastructure.config;

import com.astraflow.agent.application.constant.ResilienceConstants;
import com.astraflow.agent.infrastructure.ai.ResilientChatModel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * LLM 韧性装配（design D2/D4）。
 *
 * <p>从 {@link ChatProperties} 构建 Resilience4j {@link Retry} + {@link CircuitBreaker}，
 * 装饰 {@link DeepSeekChatModel} 为 {@code @Primary ChatModel}（{@link ResilientChatModel}），
 * 使图 {@code CallModelAction} 与 {@code SpringAiLlmClient} 共享同一韧性底层。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ChatProperties.class)
public class ResilienceConfig {

    /**
     * LLM 调用重试实例（指数退避 + 抖动，coding-conventions 记录每次重试次数与等待时长）。
     *
     * @param props LLM 参数
     * @return Resilience4j Retry
     */
    @Bean
    public Retry llmRetry(ChatProperties props) {
        Retry retry = Retry.of(ResilienceConstants.LLM_NAME, retryConfig(props.getRetry()));
        retry.getEventPublisher()
                .onRetry(event -> log.warn("LLM 调用重试: name={} 第{}次 等待{}ms 原因={}",
                        event.getName(), event.getNumberOfRetryAttempts(),
                        event.getWaitInterval(), String.valueOf(event.getLastThrowable())))
                .onError(event -> log.error("LLM 重试耗尽: name={} 尝试{}次 最终失败",
                        event.getName(), event.getNumberOfRetryAttempts(), event.getLastThrowable()));
        return retry;
    }

    /**
     * LLM 调用熔断实例（连续失败达阈值 → 打开 fast-fail；冷却后半开自愈）。
     *
     * @param props LLM 参数
     * @return Resilience4j CircuitBreaker
     */
    @Bean
    public CircuitBreaker llmCircuitBreaker(ChatProperties props) {
        return CircuitBreaker.of(ResilienceConstants.LLM_NAME, circuitBreakerConfig(props.getCircuitBreaker()));
    }

    /**
     * 经韧性装饰的 {@code @Primary ChatModel}（design D2）。
     *
     * @param delegate       自动装配的 DeepSeekChatModel
     * @param retry          LLM 重试
     * @param circuitBreaker LLM 熔断
     * @return 装饰后的 ChatModel（注入优先级高于裸 DeepSeekChatModel）
     */
    @Bean
    @Primary
    public ChatModel resilientChatModel(DeepSeekChatModel delegate, Retry retry, CircuitBreaker circuitBreaker) {
        return new ResilientChatModel(delegate, retry, circuitBreaker);
    }

    private static RetryConfig retryConfig(ChatProperties.Retry retry) {
        return RetryConfig.custom()
                .maxAttempts(retry.getMaxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                        retry.getInitialBackoff().toMillis(),
                        retry.getMultiplier(),
                        retry.getRandomizationFactor()))
                .retryOnException(ResilientChatModel::isRetryable)
                .build();
    }

    private static CircuitBreakerConfig circuitBreakerConfig(ChatProperties.CircuitBreaker cb) {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(cb.getFailureRateThreshold())
                .slowCallRateThreshold(cb.getSlowCallRateThreshold())
                .slowCallDurationThreshold(cb.getSlowCallDurationThreshold())
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(cb.getSlidingWindowSize())
                .minimumNumberOfCalls(cb.getMinimumNumberOfCalls())
                .waitDurationInOpenState(cb.getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(cb.getPermittedNumberOfCallsInHalfOpenState())
                .build();
    }
}
