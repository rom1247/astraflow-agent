package com.astraflow.agent.domain.model;

/**
 * 消息角色枚举，消除 messages.role 列魔法值。
 *
 * <p>实体字段以 {@link jakarta.persistence.EnumType#STRING} 持久化（禁 ORDINAL）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public enum MessageRole {

    /** 用户消息。 */
    USER,

    /** 模型（助手）消息。 */
    ASSISTANT,

    /** 工具结果消息。 */
    TOOL
}
