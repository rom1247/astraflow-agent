package com.astraflow.agent.infrastructure.persistence.postgres;

import com.astraflow.agent.domain.model.ToolCall;
import com.astraflow.agent.domain.repository.ToolCallRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 工具调用台账仓储 JPA 实现（单轨：直接操作 domain 实体 ToolCall，无 PO、无转换层）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public interface JpaToolCallRepository extends JpaRepository<ToolCall, Long>, ToolCallRepository {
}
