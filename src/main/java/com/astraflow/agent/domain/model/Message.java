package com.astraflow.agent.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * 对话消息实体（单轨 {@code @Entity}）。
 *
 * <p>{@code content} 以 JSONB 承载 role 异构内容（user={text}、assistant={text,toolCalls?}、tool={toolResult}），
 * 用 {@code @JdbcTypeCode(SqlTypes.JSON)} 映射 {@link JsonNode}。{@code session_id} 为外键列（仅存值，不建关联）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "messages")
public class Message {

    /** 自增主键（DB IDENTITY）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 所属会话 ID（外键 sessions.id）。 */
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /** 消息角色。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private MessageRole role;

    /** 消息内容（JSONB）。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false)
    private JsonNode content;

    /** 关联的工具调用 ID（role=TOOL/ASSISTANT 的 tool_calls 时使用）。 */
    @Column(name = "tool_use_id", length = 64)
    private String toolUseId;

    /** 所属轮次 ID。 */
    @Column(name = "turn_id", length = 64)
    private String turnId;

    /** 创建时间。 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 创建一条消息。
     *
     * @param sessionId  所属会话 ID
     * @param role       角色
     * @param content    内容（JSONB）
     * @param toolUseId  工具调用 ID（可空）
     * @param turnId     轮次 ID（可空）
     * @return 未持久化的消息实例
     */
    public static Message create(UUID sessionId, MessageRole role, JsonNode content, String toolUseId, String turnId) {
        Message message = new Message();
        message.sessionId = sessionId;
        message.role = role;
        message.content = content;
        message.toolUseId = toolUseId;
        message.turnId = turnId;
        return message;
    }
}
