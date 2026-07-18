package com.astraflow.agent.infrastructure.ai;

import com.astraflow.agent.domain.model.LlmErrorCode;
import com.astraflow.agent.domain.model.LlmException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * 经 Resilience4j 装饰的 {@link ChatModel}（design D2）。
 *
 * <p>委托底层 {@code DeepSeekChatModel}，对 {@link #stream(Prompt)} 套重试 + 熔断装饰，
 * 注册为 {@code @Primary ChatModel}，使图 {@code CallModelAction} 与 {@code SpringAiLlmClient}
 * 共享同一韧性底层。
 *
 * <p><b>链序（design D4）</b>：{@code Retry(外) → CircuitBreaker(内) → 实际 HTTP}。每次重试都经 CB 统计：
 * 偶发瞬时错误（重试可恢复）不计失败，持续故障（重试耗尽）累计触发熔断。CB 打开时短路，{@code CallNotPermittedException}
 * 被排除在重试之外（fast-fail 语义）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
@Slf4j
public class ResilientChatModel implements ChatModel {

    private final ChatModel delegate;

    private final Retry retry;

    private final CircuitBreaker circuitBreaker;

    /**
     * 构造装饰器。
     *
     * @param delegate       底层 ChatModel（生产为 DeepSeekChatModel）
     * @param retry          Resilience4j 重试实例
     * @param circuitBreaker Resilience4j 熔断实例
     */
    public ResilientChatModel(ChatModel delegate, Retry retry, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // transformDeferred：每次订阅获得独立 operator 态。先套 CB（内，贴近源），再套 Retry（外），
        // 实现 Retry → CircuitBreaker → HTTP 的链序（design D4）。
        // onErrorMap：最外层把 Resilience4j / Spring AI 异常包装为 LlmException（优雅降级，design D7 / spec R5）。
        return delegate.stream(prompt)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .onErrorMap(ResilientChatModel::mapToLlmException);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // 同步调用直接委托（图主循环走 stream；call 用于非流式便利场景，韧性装饰以 stream 为主）。
        return delegate.call(prompt);
    }

    @Override
    public ChatOptions getOptions() {
        return delegate.getOptions();
    }

    // 注：ChatModel#getDefaultOptions() 在 Spring AI 2.0 已 @Deprecated（红线禁用过时方法），
    // 此处不覆写，沿用接口默认实现。

    /**
     * 判定异常是否可重试（design D4）。
     *
     * <p>排除 {@link CallNotPermittedException}（CB 打开 → fast-fail，不应重试）；其余瞬时错误
     * （429 / 超时 / 连接拒绝 / Spring AI TransientAiException）一律重试。非瞬时错误（如 4xx 业务错误）
     * 亦会被重试至 {@code maxAttempts} 上限后停止（MVP 不按 HTTP 码细粒度区分，design D4 诠释）。
     *
     * @param throwable 异常
     * @return 是否可重试
     */
    public static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            return false;
        }
        return true;
    }

    /**
     * 把底层异常映射为 {@link LlmException}（优雅降级，design D7 / spec R5）。
     *
     * <p>映射规则：
     * <ul>
     *   <li>已封装的 {@link LlmException} 原样透传</li>
     *   <li>{@link CallNotPermittedException}（熔断打开 fast-fail）→ {@code CIRCUIT_OPEN}</li>
     *   <li><b>可重试错误经 Retry 后仍到达此处</b> = 重试耗尽 → {@code RETRY_EXHAUSTED}
     *       （Resilience4j reactive 耗尽后传播原始错误，非 {@code MaxRetriesExceededException}，
     *       故以「可重试错误幸存」判定耗尽）</li>
     *   <li>其余（不可重试）→ {@code UPSTREAM_ERROR}</li>
     * </ul>
     *
     * @param throwable 原始异常
     * @return 映射后的异常（始终非 null，供 {@code onErrorMap} 使用）
     */
    static Throwable mapToLlmException(Throwable throwable) {
        if (throwable instanceof LlmException) {
            return throwable;
        }
        if (throwable instanceof CallNotPermittedException) {
            return new LlmException(LlmErrorCode.CIRCUIT_OPEN, LlmErrorCode.CIRCUIT_OPEN.getDefaultMessage(), throwable);
        }
        if (isRetryable(throwable)) {
            return new LlmException(LlmErrorCode.RETRY_EXHAUSTED, LlmErrorCode.RETRY_EXHAUSTED.getDefaultMessage(), throwable);
        }
        return new LlmException(LlmErrorCode.UPSTREAM_ERROR,
                LlmErrorCode.UPSTREAM_ERROR.getDefaultMessage() + "：" + String.valueOf(throwable.getMessage()), throwable);
    }
}
