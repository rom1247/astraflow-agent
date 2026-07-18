package com.astraflow.agent.infrastructure.persistence.postgres;

import com.astraflow.agent.domain.model.ModerationAudit;
import com.astraflow.agent.domain.repository.ModerationAuditRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 审核审计台账仓储 JPA 实现（单轨：直接操作 domain 实体 ModerationAudit，无 PO、无转换层）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public interface JpaModerationAuditRepository extends JpaRepository<ModerationAudit, Long>, ModerationAuditRepository {
}
