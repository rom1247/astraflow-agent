package com.astraflow.agent.infrastructure.ai;

import com.astraflow.agent.domain.model.LlmErrorCode;
import com.astraflow.agent.domain.model.LlmException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * LLM 优雅降级测试（spec llm-resilience R5）：重试耗尽 / 熔断打开均包装为 {@link LlmException}，
 * 不抛未封装原始异常。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class LlmExceptionTest extends AbstractDeepSeekWireMockTest {

    private final StreamingChatResponseAggregator aggregator = new StreamingChatResponseAggregator();

    @Test
    @DisplayName("重试耗尽 → 包装为 LlmException(RETRY_EXHAUSTED)（R5.1）")
    void testRetryExhausted_mapsToLlmException() {
        // retry-3；CB 阈值放大避免提前打开（让重试耗尽主导）
        Retry retry = fastRetry(3);
        CircuitBreaker circuitBreaker = fastCircuitBreaker(fastCircuitBreakerConfig()
                .slidingWindowSize(10).minimumNumberOfCalls(10).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60)).build());
        ChatModel model = new ResilientChatModel(newDeepSeekChatModel(), retry, circuitBreaker);
        stubChatCompletionsError(429, 1);

        Throwable thrown = catchThrowable(() -> aggregator.aggregate(model.stream(userPrompt("hi"))).block());

        assertThat(thrown).isInstanceOf(LlmException.class);
        assertThat(((LlmException) thrown).getErrorCode()).isEqualTo(LlmErrorCode.RETRY_EXHAUSTED);
    }

    @Test
    @DisplayName("熔断 fast-fail → 包装为 LlmException(CIRCUIT_OPEN)（R5.2）")
    void testCircuitOpen_mapsToLlmException() {
        // retry-1 + 收紧 CB（min=2）快速打开
        Retry retry = fastRetry(1);
        CircuitBreaker circuitBreaker = fastCircuitBreaker(fastCircuitBreakerConfig()
                .slidingWindowSize(4).minimumNumberOfCalls(2).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60)).build());
        ChatModel model = new ResilientChatModel(newDeepSeekChatModel(), retry, circuitBreaker);
        stubChatCompletionsError(500, null);

        // 2 次失败打开熔断（吞掉 error）
        catchThrowable(() -> aggregator.aggregate(model.stream(userPrompt("hi"))).block());
        catchThrowable(() -> aggregator.aggregate(model.stream(userPrompt("hi"))).block());

        // 第 3 次 → 熔断打开 fast-fail → LlmException(CIRCUIT_OPEN)
        Throwable thrown = catchThrowable(() -> aggregator.aggregate(model.stream(userPrompt("hi"))).block());

        assertThat(thrown).isInstanceOf(LlmException.class);
        assertThat(((LlmException) thrown).getErrorCode()).isEqualTo(LlmErrorCode.CIRCUIT_OPEN);
    }
}
