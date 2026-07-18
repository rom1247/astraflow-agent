package com.astraflow.agent.domain.repository;

import com.astraflow.agent.domain.model.AgentEvent;

import java.util.Optional;

/**
 * Agent 事件仓储端口（零框架依赖，返回领域实体）。
 *
 * <p>简单 CRUD 由实现委托 {@code JpaAgentEventRepository}；{@code append} 热路径（seq 行锁自增 + 同事务落盘）
 * 由 {@code PostgresAgentEventRepositoryImpl} 用 {@code JdbcTemplate} 实现（见 agent-event-sequencing）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public interface AgentEventRepository {

    /**
     * 保存一条事件。
     *
     * @param event 事件实体
     * @return 已持久化的事件
     */
    AgentEvent save(AgentEvent event);

    /**
     * 按主键查询事件。
     *
     * @param id 事件 ID
     * @return 命中的事件，不存在返回 {@link Optional#empty()}
     */
    Optional<AgentEvent> findById(Long id);

    /**
     * 追加一条事件并分配 per-session 单调递增的 seq（即 SSE eventId）。
     *
     * <p>在 {@code REQUIRES_NEW} 自治事务内执行 {@code UPDATE sessions SET last_event_seq=last_event_seq+1 … RETURNING}
     * 取得新 seq（隐式行锁），随即将该 seq 与事件落盘到 {@code agent_events}，返回 seq。
     *
     * @param event 待落盘事件（其 seq 字段被忽略，由本方法分配）
     * @return 分配的 seq（= SSE eventId）
     * @throws org.springframework.dao.DataAccessException 当 {@code sessionId} 不存在时，
     *         {@code UPDATE…RETURNING} 命中 0 行，抛出 {@link org.springframework.dao.EmptyResultDataAccessException}；
     *         不得静默返回 0 或 null
     */
    long append(AgentEvent event);
}
