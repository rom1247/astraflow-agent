package com.astraflow.agent.domain.model;

/**
 * Agent 事件类型枚举，消除 {@code agent_events.event_type} 列魔法值。
 *
 * <p>共 7 种事件类型，覆盖一个 Agent 会话轮次的完整生命周期：
 * 会话启动 → 文本增量 → 工具调用 → 工具结果 → 轮次结束 → 结束 / 错误。
 * 实体字段以 {@link jakarta.persistence.EnumType#STRING} 持久化（禁 ORDINAL），
 * 重放时按 {@code event_type} 反序列化 {@code event_json} 异构 payload。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public enum EventType {

    /** 会话启动。 */
    SESSION_START,

    /** 模型文本增量。 */
    ASSISTANT_TEXT,

    /** 工具即将调用。 */
    TOOL_USE,

    /** 工具结果。 */
    TOOL_RESULT,

    /** 单轮结束。 */
    TURN_END,

    /** 会话结束。 */
    DONE,

    /** 错误。 */
    ERROR
}
