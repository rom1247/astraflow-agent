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
 * Agent 事件日志实体（单轨 {@code @Entity}）。
 *
 * <p>{@code seq} = {@code sessions.last_event_seq} 自增产物 = SSE {@code eventId}，由 {@code append} 热路径
 * （{@code UPDATE sessions … RETURNING} 行锁自增）分配，{@code UNIQUE(session_id, seq)} 为重放主索引。
 *
 * <p>{@code eventJson} 以 JSONB 承载 7 种 {@link EventType} 的异构 payload，用
 * {@code @JdbcTypeCode(SqlTypes.JSON)} 映射 {@link JsonNode}（Hibernate 7 原生 JSON，已验证可用，见 EventJsonMappingTest）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "agent_events")
public class AgentEvent {

    /** 落盘状态默认值（agent_events.status）。 */
    public static final String STATUS_PERSISTED = "PERSISTED";

    /** 自增主键（DB IDENTITY）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 所属会话 ID（外键 sessions.id）。 */
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /** per-session 单调递增序号（= SSE eventId），由 append 热路径分配。 */
    @Column(name = "seq", nullable = false)
    private long seq;

    /** 所属轮次 ID。 */
    @Column(name = "turn_id", length = 64)
    private String turnId;

    /** 事件类型（以枚举名字符串持久化，禁 ORDINAL）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    /** 事件异构 payload（JSONB，按 event_type 反序列化）。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_json")
    private JsonNode eventJson;

    /** 落盘状态（默认 PERSISTED）。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 创建时间。 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 创建一条 Agent 事件（status 默认 PERSISTED）。
     *
     * @param sessionId 所属会话 ID
     * @param seq       序号（append 热路径分配；直接 CRUD 时由调用方给定）
     * @param turnId    轮次 ID（可空）
     * @param eventType 事件类型
     * @param eventJson 异构 payload（可空）
     * @return 未持久化的事件实例
     */
    public static AgentEvent create(UUID sessionId, long seq, String turnId, EventType eventType, JsonNode eventJson) {
        AgentEvent event = new AgentEvent();
        event.sessionId = sessionId;
        event.seq = seq;
        event.turnId = turnId;
        event.eventType = eventType;
        event.eventJson = eventJson;
        event.status = STATUS_PERSISTED;
        return event;
    }
}
