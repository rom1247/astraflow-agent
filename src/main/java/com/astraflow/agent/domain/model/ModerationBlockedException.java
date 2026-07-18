package com.astraflow.agent.domain.model;

import java.io.Serial;

/**
 * 内容审核拦截信号异常（moderation-hook spec，design D7）。
 *
 * <p>由图内审核 hook（工具执行前 ③）在 {@link Decision#BLOCK} 时抛出，经图 stream 传播，
 * 由 {@code AgentEngine} 的 {@code onErrorMap} 统一映射为 {@code DONE(error_blocked_tool)}。
 * 消息为中文便于排查。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public class ModerationBlockedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造异常。
     *
     * @param message 中文消息
     */
    public ModerationBlockedException(String message) {
        super(message);
    }
}
