package com.astraflow.agent.domain.model;

/**
 * 会话状态枚举，消除 sessions.status 列魔法值。
 *
 * <p>实体字段以 {@link jakarta.persistence.EnumType#STRING} 持久化（禁 ORDINAL）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public enum SessionStatus {

    /** 活跃：可继续对话。 */
    ACTIVE,

    /** 已关闭：不再接收新消息。 */
    CLOSED
}
