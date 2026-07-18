package com.astraflow.agent.domain.model;

import lombok.Getter;

/**
 * LLM 调用错误码（优雅降级返回结构化 error，design D7）。
 *
 * <p>携带于 {@link LlmException}，供上层（change #4）映射为 {@code DONE(error_during_execution)} 等事件。
 * 放 {@code domain/model}：domain/application 消费方可捕获 {@link LlmException} 并读取错误码。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
@Getter
public enum LlmErrorCode {

    /** LLM 调用被限流（429）。 */
    RATE_LIMITED("LLM_RATE_LIMITED", "LLM 调用被限流"),

    /** LLM 调用重试耗尽（持续失败达上限）。 */
    RETRY_EXHAUSTED("LLM_RETRY_EXHAUSTED", "LLM 调用重试耗尽"),

    /** LLM 熔断器已打开（fast-fail）。 */
    CIRCUIT_OPEN("LLM_CIRCUIT_OPEN", "LLM 熔断器已打开，调用被快速失败"),

    /** LLM 上游调用失败（未分类错误）。 */
    UPSTREAM_ERROR("LLM_UPSTREAM_ERROR", "LLM 上游调用失败");

    private final String code;

    private final String defaultMessage;

    LlmErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
