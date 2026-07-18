package com.astraflow.agent.infrastructure.ai;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ResilientChatModel} 韧性行为集成测试（WireMock 桩 {@code /chat/completions}，不加载 Spring 上下文）。
 *
 * <p>覆盖 spec llm-streaming S1.1（正常流式）/ S3.x、llm-resilience R1（重试）/ R2（重试耗尽）：
 * 手工装配指向 WireMock 的真实 {@code DeepSeekChatModel} + 快速 Resilience4j 实例，
 * 验证 token 序列、429/超时/连接拒绝重试成功、重试耗尽、中途断开 error 上报。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class ResilientChatModelTest extends AbstractDeepSeekWireMockTest {

    private final StreamingChatResponseAggregator aggregator = new StreamingChatResponseAggregator();

    private ChatModel model;

    @BeforeEach
    void setUpModel() {
        Retry retry = fastRetry(3);
        CircuitBreaker circuitBreaker = fastCircuitBreaker(
                fastCircuitBreakerConfig()
                        .slidingWindowSize(4)
                        .minimumNumberOfCalls(4)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(java.time.Duration.ofMillis(200))
                        .build());
        model = new ResilientChatModel(newDeepSeekChatModel(), retry, circuitBreaker);
    }

    @Test
    @DisplayName("正常流式响应 → 经装饰的 ChatModel 产出按序 token，聚合得完整文本")
    void testStream_normalResponseProducesOrderedTokens() {
        stubStreamTokens("你好", "，世界");

        AggregatedChatResponse result = aggregator.aggregate(model.stream(userPrompt("你好，世界"))).block();

        assertThat(result).as("流式响应应正常完成").isNotNull();
        assertThat(result.text()).isEqualTo("你好，世界");
    }

    @Test
    @DisplayName("首次 429（含 Retry-After）+ 重试 200 → 自动重试最终收到完整流（R1.1）")
    void testStream_retryOn429ThenSuccess() {
        stubErrorThenStream(429, 1, "你好", "，世界");

        AggregatedChatResponse result = aggregator.aggregate(model.stream(userPrompt("hi"))).block();

        assertThat(result.text()).isEqualTo("你好，世界");
        // 1 次失败 + 1 次成功 = 2 次请求
        wireMockServer.verify(2, postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)));
    }

    @Test
    @DisplayName("首次连接被重置（网络中断）+ 重试可达 → 自动重试成功（R1.3 / S3.2）")
    void testStream_retryOnConnectionResetThenSuccess() {
        stubConnectionResetThenStream("你好", "，世界");

        AggregatedChatResponse result = aggregator.aggregate(model.stream(userPrompt("hi"))).block();

        assertThat(result.text()).isEqualTo("你好，世界");
    }

    @Test
    @DisplayName("连续 429 达重试上限 → 停止重试、不再发请求，流以错误终止（R2.1）")
    void testStream_retryExhaustedStopsAndErrors() {
        stubChatCompletionsError(429, 1);

        assertThatThrownBy(() -> aggregator.aggregate(model.stream(userPrompt("hi"))).block())
                .isNotNull();

        // 恰好 maxAttempts=3 次请求，重试耗尽后不再发请求
        wireMockServer.verify(3, postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)));
    }

    @Test
    @DisplayName("首次响应延迟超阈值 + 重试及时 → 超时触发重试最终成功（R1.2）")
    void testStream_retryOnTimeoutThenSuccess() {
        // 用 200ms 响应超时的模型；首次延迟 1500ms 必然超时
        model = new ResilientChatModel(
                newDeepSeekChatModel(java.time.Duration.ofMillis(200)),
                fastRetry(3),
                fastCircuitBreaker(fastCircuitBreakerConfig()
                        .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                        .waitDurationInOpenState(java.time.Duration.ofMillis(200)).build()));
        stubDelayedErrorThenStream(1500, "你好", "，世界");

        AggregatedChatResponse result = aggregator.aggregate(model.stream(userPrompt("hi"))).block();

        assertThat(result.text()).isEqualTo("你好，世界");
        wireMockServer.verify(2, postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)));
    }

    @Test
    @DisplayName("流式中途连接断开（持续）→ 重试耗尽后以 error 信号上报，不静默吞没（S3.1）")
    void testStream_midStreamBreakErrorsAfterRetry() {
        // 持续连接重置（中途/网络失败类型）：每次重试都失败 → 耗尽 → error
        wireMockServer.stubFor(post(urlEqualTo(CHAT_COMPLETIONS_PATH))
                .willReturn(connectionResetResponse()));

        // 聚合层（reduce）在 error 时不产出结果 → block() 抛出，证明错误信号穿透到上层、未被流式层吞没
        assertThatThrownBy(() -> aggregator.aggregate(model.stream(userPrompt("hi"))).block())
                .isNotNull();

        // 持续失败 → 恰好 maxAttempts=3 次请求
        wireMockServer.verify(3, postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)));
    }
}
