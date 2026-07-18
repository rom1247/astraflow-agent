package com.astraflow.agent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LLM 调用参数（模型 / 重试 / 熔断 / 超时），由 {@code ResilienceConfig} 读取构建 Resilience4j 实例。
 *
 * <p>前缀 {@code astraflow.llm}。禁止硬编码（red-lines 魔法值规范），所有韧性阈值外化至此。
 *
 * <pre>
 * astraflow:
 *   llm:
 *     model: deepseek-v4-flash
 *     retry:
 *       max-attempts: 3
 *       initial-backoff: 1s
 *       multiplier: 2.0
 *     circuit-breaker:
 *       failure-rate-threshold: 50
 *       sliding-window-size: 20
 *       minimum-number-of-calls: 10
 *       wait-duration-in-open-state: 30s
 *     timeout:
 *       response: 60s
 * </pre>
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
@ConfigurationProperties(prefix = "astraflow.llm")
public class ChatProperties {

    /** 默认模型名（与 spring.ai.deepseek.chat.model 对齐）。 */
    private String model = "deepseek-v4-flash";

    private Retry retry = new Retry();

    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    private Timeout timeout = new Timeout();

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public Timeout getTimeout() {
        return timeout;
    }

    public void setTimeout(Timeout timeout) {
        this.timeout = timeout;
    }

    /** 重试参数（指数退避）。 */
    public static class Retry {

        /** 最大尝试次数（含首次，1 表示不重试）。 */
        private int maxAttempts = 3;

        /** 初始退避时长。 */
        private Duration initialBackoff = Duration.ofSeconds(1);

        /** 退避倍率（指数递增）。 */
        private double multiplier = 2.0;

        /** 随机化因子（抖动，避免惊群）。 */
        private double randomizationFactor = 0.5;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }

        public double getRandomizationFactor() {
            return randomizationFactor;
        }

        public void setRandomizationFactor(double randomizationFactor) {
            this.randomizationFactor = randomizationFactor;
        }
    }

    /** 熔断参数。 */
    public static class CircuitBreaker {

        /** 失败率阈值（百分比，0–100）。 */
        private float failureRateThreshold = 50.0f;

        /** 滑动窗口大小（调用数）。 */
        private int slidingWindowSize = 20;

        /** 触发失败率计算的最少调用数。 */
        private int minimumNumberOfCalls = 10;

        /** 打开态冷却时长。 */
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);

        /** 半开态允许的试探调用数。 */
        private int permittedNumberOfCallsInHalfOpenState = 3;

        /** 判定为慢调用的时长阈值。 */
        private Duration slowCallDurationThreshold = Duration.ofSeconds(60);

        /** 慢调用率阈值（百分比）。 */
        private float slowCallRateThreshold = 100.0f;

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        public int getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        }

        public Duration getSlowCallDurationThreshold() {
            return slowCallDurationThreshold;
        }

        public void setSlowCallDurationThreshold(Duration slowCallDurationThreshold) {
            this.slowCallDurationThreshold = slowCallDurationThreshold;
        }

        public float getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }

        public void setSlowCallRateThreshold(float slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }
    }

    /** 超时参数。 */
    public static class Timeout {

        /** 响应超时（流式首个字节 / 整体响应）。 */
        private Duration response = Duration.ofSeconds(60);

        public Duration getResponse() {
            return response;
        }

        public void setResponse(Duration response) {
            this.response = response;
        }
    }
}
