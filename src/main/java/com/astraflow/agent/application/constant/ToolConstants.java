package com.astraflow.agent.application.constant;

/**
 * 工具相关编译期固定阈值常量（{@code coding-conventions.md} 配置常量类）。
 *
 * <p>构造私有化，禁止实例化。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public final class ToolConstants {

    private ToolConstants() {
    }

    /** 算术表达式最大长度（calculator inputSchema.maxLength 引用同一常量，避免两处不一致）。 */
    public static final int MAX_EXPRESSION_LENGTH = 200;
}
