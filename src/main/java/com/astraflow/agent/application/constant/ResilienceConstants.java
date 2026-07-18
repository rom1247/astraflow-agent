package com.astraflow.agent.application.constant;

/**
 * LLM 韧性相关常量（禁魔法值，red-lines 强制）。
 *
 * <p>集中 HTTP 状态码、Resilience4j 实例名等字面量，供 {@code ResilientChatModel} /
 * {@code ResilienceConfig} / 错误映射复用。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public final class ResilienceConstants {

    private ResilienceConstants() {
    }

    /** HTTP 429 Too Many Requests（限流，可重试）。 */
    public static final int HTTP_TOO_MANY_REQUESTS = 429;

    /** HTTP 5xx 服务端错误下限（含 500/502/503/504）。 */
    public static final int HTTP_SERVER_ERROR_MIN = 500;

    /** HTTP 500 Internal Server Error。 */
    public static final int HTTP_INTERNAL_SERVER_ERROR = 500;

    /** HTTP 502 Bad Gateway。 */
    public static final int HTTP_BAD_GATEWAY = 502;

    /** HTTP 503 Service Unavailable。 */
    public static final int HTTP_SERVICE_UNAVAILABLE = 503;

    /** HTTP 504 Gateway Timeout。 */
    public static final int HTTP_GATEWAY_TIMEOUT = 504;

    /** LLM 调用重试 / 熔断共享实例名（Retry 与 CircuitBreaker 分属不同 Registry，同名互不冲突）。 */
    public static final String LLM_NAME = "llm-chat";
}
