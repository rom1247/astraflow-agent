package com.astraflow.agent.infrastructure.persistence.postgres;

import com.astraflow.agent.domain.model.Session;
import com.astraflow.agent.domain.repository.SessionRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * 会话仓储 JPA 实现（单轨：直接操作 domain 实体 Session，无 PO、无转换层）。
 *
 * <p>由 Spring Data 自动代理，同时实现 {@link SessionRepository} 端口。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public interface JpaSessionRepository extends JpaRepository<Session, UUID>, SessionRepository {
}
