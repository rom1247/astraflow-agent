package com.astraflow.agent.domain.tool;

/**
 * 工具执行结果状态枚举，消除 {@link ToolResult} 状态字段的魔法值。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public enum ToolResultStatus {

    /** 执行成功。 */
    SUCCESS,

    /** 执行失败（入参校验失败 / 工具业务错误 / 网络 / HTTP / 出站策略拦截等）。 */
    ERROR
}
