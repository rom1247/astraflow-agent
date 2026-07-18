package com.astraflow.agent.infrastructure.persistence.postgres;

import com.astraflow.agent.domain.model.Message;
import com.astraflow.agent.domain.repository.MessageRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 消息仓储 JPA 实现（单轨：直接操作 domain 实体 Message，无 PO、无转换层）。
 *
 * <p>{@link #findBySessionId} 由 Spring Data 据方法名派生（按 created_at 升序）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public interface JpaMessageRepository extends JpaRepository<Message, Long>, MessageRepository {
}
