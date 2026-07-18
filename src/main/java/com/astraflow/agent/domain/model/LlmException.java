package com.astraflow.agent.domain.model;

import java.io.Serial;

/**
 * LLM 调用异常（优雅降级结构化 error，design D7）。
 *
 * <p>由 {@code ResilientChatModel} 经 {@code onErrorMap} 包装 Resilience4j / Spring AI 异常而来，
 * 携带 {@link LlmErrorCode}，消息为中文便于排查。上层 SHALL 捕获本异常映射为 error 事件，
 * 不向调用方抛未封装原始异常（spec R5）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public class LlmException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final LlmErrorCode errorCode;

    /**
     * 构造异常。
     *
     * @param errorCode 错误码
     * @param message   中文消息
     */
    public LlmException(LlmErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造异常（携带原因）。
     *
     * @param errorCode 错误码
     * @param message   中文消息
     * @param cause     原始异常
     */
    public LlmException(LlmErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public LlmErrorCode getErrorCode() {
        return errorCode;
    }
}
