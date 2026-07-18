package com.astraflow.agent.domain.model;

import java.io.Serial;

/**
 * 最大轮次超限信号异常（agent-loop spec，design D8）。
 *
 * <p>由 {@code AgentEngine} 的最大轮次守卫（{@code callModelHook} 计数 agent 节点完成次数）在超限时抛出，
 * 经图 stream 传播，由 {@code AgentEngine} 的 {@code onErrorMap} 统一映射为 {@code DONE(error_max_turns)}。
 * 消息为中文便于排查。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public class MaxTurnsExceededException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造异常。
     *
     * @param message 中文消息
     */
    public MaxTurnsExceededException(String message) {
        super(message);
    }
}
