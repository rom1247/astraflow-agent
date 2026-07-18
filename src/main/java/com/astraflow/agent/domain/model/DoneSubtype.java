package com.astraflow.agent.domain.model;

/**
 * DONE 事件 subtype 结束码枚举（禁魔法字符串，agent-loop spec「subtype 以枚举承载」）。
 *
 * <p>一次性定义完整 7 值集合：本 change 的 {@code AgentEngine} 产出
 * {@link #SUCCESS} / {@link #ERROR_DURING_EXECUTION} / {@link #ERROR_MAX_TURNS}；
 * {@link #ERROR_CANCELED} 与 {@code ERROR_BLOCKED_*} 为协议预留值，由 #5（硬取消）/ #7（审核）产出。
 * wire 值（{@link #getValue()}）即事件协议 {@code event_json.subtype}，禁散落字符串字面量。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public enum DoneSubtype {

    /** 正常完成。 */
    SUCCESS("success"),

    /** LLM 或运行时异常终止。 */
    ERROR_DURING_EXECUTION("error_during_execution"),

    /** 超过最大轮次终止。 */
    ERROR_MAX_TURNS("error_max_turns"),

    /** 取消（由传输层 #5 触发，预留）。 */
    ERROR_CANCELED("error_canceled"),

    /** 输入被审核拦截（#7，预留）。 */
    ERROR_BLOCKED_INPUT("error_blocked_input"),

    /** 输出被审核拦截（#7，预留）。 */
    ERROR_BLOCKED_OUTPUT("error_blocked_output"),

    /** 工具调用被审核拦截（#7）。 */
    ERROR_BLOCKED_TOOL("error_blocked_tool");

    /** wire 值（事件协议小写下划线）。 */
    private final String value;

    DoneSubtype(String value) {
        this.value = value;
    }

    /**
     * @return 事件协议 wire 值
     */
    public String getValue() {
        return value;
    }
}
