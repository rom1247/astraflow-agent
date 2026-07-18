package com.astraflow.agent.domain.tool;

import java.io.Serial;

/**
 * 工具注册异常：{@link ToolRegistry} 构造期检测到同名工具重复注册时抛出。
 *
 * <p>遵循 {@code coding-conventions.md}：业务异常继承 {@link RuntimeException}，含 {@code @Serial}。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public class ToolRegistrationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造异常。
     *
     * @param message 中文错误描述
     */
    public ToolRegistrationException(String message) {
        super(message);
    }
}
