package com.astraflow.agent.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 工具调用台账实体（单轨 {@code @Entity}）。
 *
 * <p>{@code input}/{@code output} 以 JSONB 承载异构参数与结果，用 {@code @JdbcTypeCode(SqlTypes.JSON)} 映射 {@link JsonNode}。
 * 本 change 仅承载基础 CRUD，工具审计记录的业务行为属后续 change。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "tool_calls")
public class ToolCall {

    /** 自增主键（DB IDENTITY）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 所属会话 ID（外键 sessions.id）。 */
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /** 所属轮次 ID。 */
    @Column(name = "turn_id", length = 64)
    private String turnId;

    /** 工具名。 */
    @Column(name = "tool", length = 64)
    private String tool;

    /** 工具入参（JSONB）。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input")
    private JsonNode input;

    /** 工具出参（JSONB）。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output")
    private JsonNode output;

    /** 调用状态。 */
    @Column(name = "status", length = 16)
    private String status;

    /** 调用耗时（毫秒）。 */
    @Column(name = "latency_ms")
    private Integer latencyMs;

    /** 消耗 token 数。 */
    @Column(name = "cost_tokens")
    private Integer costTokens;

    /** 创建时间。 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 创建一条工具调用记录。
     *
     * @param sessionId 所属会话 ID
     * @param turnId    轮次 ID（可空）
     * @param tool      工具名
     * @param input     入参（JSONB，可空）
     * @param output    出参（JSONB，可空）
     * @param status    调用状态（可空）
     * @return 未持久化的工具调用实例
     */
    public static ToolCall create(UUID sessionId, String turnId, String tool, JsonNode input, JsonNode output, String status) {
        ToolCall toolCall = new ToolCall();
        toolCall.sessionId = sessionId;
        toolCall.turnId = turnId;
        toolCall.tool = tool;
        toolCall.input = input;
        toolCall.output = output;
        toolCall.status = status;
        return toolCall;
    }
}
