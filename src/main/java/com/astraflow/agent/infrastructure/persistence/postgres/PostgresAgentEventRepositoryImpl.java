package com.astraflow.agent.infrastructure.persistence.postgres;

import com.astraflow.agent.domain.model.AgentEvent;
import com.astraflow.agent.domain.repository.AgentEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Agent 事件仓储实现（seq 热路径：行锁自增 + 同事务落盘）。
 *
 * <p>{@code append} 在 {@link Propagation#REQUIRES_NEW} 自治事务内执行：
 * 一次 {@code UPDATE sessions SET last_event_seq = last_event_seq+1 WHERE id=? RETURNING last_event_seq}
 * 取得新 seq 并对 sessions 行加隐式行锁，随即 {@code INSERT agent_events}，返回 seq（即 SSE eventId）。
 * 无论调用方事务后续提交或回滚，事件与 seq 自增保持已提交（落盘点 = append 成功瞬间）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PostgresAgentEventRepositoryImpl implements AgentEventRepository {

    /**
     * Hibernate 7 的 {@code @JdbcTypeCode(SqlTypes.JSON)} 以 Jackson 2.x
     * （{@code com.fasterxml.jackson.databind.JsonNode}）映射 JSONB，故序列化 payload 复用同源 ObjectMapper。
     * 注意：Spring Boot 4.1 默认自动配置的是 Jackson 3.x（{@code tools.jackson.databind.ObjectMapper}），
     * 其类型与 Hibernate 2.x JsonNode 不同源，不能注入，故此处自建 Jackson 2.x 实例。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;
    private final JpaAgentEventRepository jpaAgentEventRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long append(AgentEvent event) {
        // 行锁自增：一次往返拿新 seq；session 不存在时 RETURNING 命中 0 行 → queryForObject 抛 EmptyResultDataAccessException
        Long seq = jdbcTemplate.queryForObject(
                "UPDATE sessions SET last_event_seq = last_event_seq + 1 WHERE id = ? RETURNING last_event_seq",
                Long.class, event.getSessionId());
        jdbcTemplate.update(
                "INSERT INTO agent_events(session_id, seq, turn_id, event_type, event_json, status) "
                        + "VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)",
                event.getSessionId(), seq, event.getTurnId(),
                event.getEventType().name(), serialize(event.getEventJson()), AgentEvent.STATUS_PERSISTED);
        log.debug("事件落盘: sessionId={}, seq={}, type={}", event.getSessionId(), seq, event.getEventType());
        return seq;
    }

    @Override
    public AgentEvent save(AgentEvent event) {
        return jpaAgentEventRepository.save(event);
    }

    @Override
    public Optional<AgentEvent> findById(Long id) {
        return jpaAgentEventRepository.findById(id);
    }

    /** 将 {@link JsonNode} 序列化为 JSON 字符串（供 JdbcTemplate 写入 JSONB 列），null 原样返回。 */
    private String serialize(JsonNode node) {
        if (node == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化事件 payload 为 JSON", e);
        }
    }
}
