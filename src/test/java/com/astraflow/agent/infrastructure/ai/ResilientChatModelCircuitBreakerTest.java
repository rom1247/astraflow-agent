package com.astraflow.agent.infrastructure.ai;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ResilientChatModel} 熔断与自愈集成测试（spec llm-resilience R3/R4）。
 *
 * <p>用 {@code maxAttempts=1} 的 Retry（每次调用 = 1 次 CB 计数）+ 收紧阈值的 CircuitBreaker，
 * 使熔断行为确定：连续失败达 {@code minimumNumberOfCalls} → 打开 fast-fail；冷却后半开试探，成功关闭 / 失败重开。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class ResilientChatModelCircuitBreakerTest extends AbstractDeepSeekWireMockTest {

    /** 熔断打开冷却时长（毫秒，测试用极小值缩短耗时）。 */
    private static final long COOL_DOWN_MILLIS = 400L;

    private final StreamingChatResponseAggregator aggregator = new StreamingChatResponseAggregator();

    private ChatModel model;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUpModel() {
        // maxAttempts=1：失败不重试，使每次用户调用恰好对应 1 次 CB 计数（熔断阈值可控）
        Retry retry = fastRetry(1);
        // 收紧阈值：minimumNumberOfCalls=2 即可触发计算；冷却 400ms 便于半开自愈测试
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(COOL_DOWN_MILLIS))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
        circuitBreaker = fastCircuitBreaker(cbConfig);
        model = new ResilientChatModel(newDeepSeekChatModel(), retry, circuitBreaker);
    }

    @Test
    @DisplayName("连续 5xx 达阈值 → 熔断打开，再次调用 fast-fail 且不发 HTTP（R3.1）")
    void testStream_consecutiveErrorsOpenCircuitAndFastFail() {
        stubChatCompletionsError(500, null);

        // 2 次失败达 minimumNumberOfCalls → 熔断打开
        attemptFailingCall();
        attemptFailingCall();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 第 3 次调用被 fast-fail（CallNotPermitted），不发起 HTTP
        assertThatThrownBy(() -> aggregator.aggregate(model.stream(userPrompt("hi"))).block()).isNotNull();

        // 仅 2 次实际 HTTP（第 3 次被熔断短路）
        wireMockServer.verify(2, postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)));
    }

    @Test
    @DisplayName("熔断已打开 → 新请求立即 fast-fail、不发 HTTP（R3.2）")
    void testStream_openCircuitFastFailsWithoutHttp() {
        stubChatCompletionsError(500, null);
        attemptFailingCall();
        attemptFailingCall();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        wireMockServer.verify(2, postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)));

        // 新请求立即 fast-fail，HTTP 计数不增加
        assertThatThrownBy(() -> aggregator.aggregate(model.stream(userPrompt("hi"))).block()).isNotNull();
        wireMockServer.verify(2, postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)));
    }

    @Test
    @DisplayName("熔断打开 → 过冷却 → 半开试探成功 → 关闭、恢复正常调用（R4.1）")
    void testStream_halfOpenSuccessClosesCircuit() throws InterruptedException {
        // 前 2 次 500（打开熔断），之后成功（半开试探命中）
        stubFailuresThenStream(2, 500, null, "恢复");
        attemptFailingCall();
        attemptFailingCall();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 过冷却时间 → 半开试探命中成功桩
        Thread.sleep(COOL_DOWN_MILLIS + 100);

        // 半开试探成功 → 关闭
        AggregatedChatResponse result = aggregator.aggregate(model.stream(userPrompt("hi"))).block();
        assertThat(result.text()).isEqualTo("恢复");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // 恢复后正常调用（不再被短路）
        AggregatedChatResponse again = aggregator.aggregate(model.stream(userPrompt("hi"))).block();
        assertThat(again.text()).isEqualTo("恢复");
    }

    @Test
    @DisplayName("半开态试探失败 → 重新回到打开态（R4.2）")
    void testStream_halfOpenFailureReopensCircuit() throws InterruptedException {
        stubChatCompletionsError(500, null);
        attemptFailingCall();
        attemptFailingCall();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 过冷却 → 半开试探（仍 500）→ 失败重开
        Thread.sleep(COOL_DOWN_MILLIS + 100);
        attemptFailingCall();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 重开后再次调用被 fast-fail：半开试探那次发了 HTTP（共 3 次），重开后的调用不发（仍 3 次）
        assertThatThrownBy(() -> aggregator.aggregate(model.stream(userPrompt("hi"))).block()).isNotNull();
        wireMockServer.verify(3, postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)));
    }

    /** 发起一次必失败的流式调用（吞掉 error，仅用于驱动熔断计数）。 */
    private void attemptFailingCall() {
        assertThatThrownBy(() -> aggregator.aggregate(model.stream(userPrompt("hi"))).block()).isNotNull();
    }
}
