package com.astraflow.agent.infrastructure.persistence.postgres;

import com.astraflow.agent.domain.model.AgentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Agent 事件仓储 JPA 接口（仅普通 CRUD）。
 *
 * <p><b>不</b> extend {@code AgentEventRepository} 端口：端口含 {@code append} 热路径（{@code JdbcTemplate} 原生 SQL），
 * JPA 接口无法承载，由 {@code PostgresAgentEventRepositoryImpl} 独立实现端口并注入本接口做简单 CRUD 委托。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public interface JpaAgentEventRepository extends JpaRepository<AgentEvent, Long> {
}
