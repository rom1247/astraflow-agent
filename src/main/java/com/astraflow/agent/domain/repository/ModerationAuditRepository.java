package com.astraflow.agent.domain.repository;

import com.astraflow.agent.domain.model.ModerationAudit;

import java.util.List;
import java.util.UUID;

/**
 * 审核审计台账仓储端口（零框架依赖，返回领域实体）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public interface ModerationAuditRepository {

    /**
     * 保存一条审核审计记录。
     *
     * @param audit 审核审计实体
     * @return 已持久化的审核审计
     */
    ModerationAudit save(ModerationAudit audit);

    /**
     * 按会话查询全部审核审计记录。
     *
     * @param sessionId 会话 ID
     * @return 该会话的审核审计列表
     */
    List<ModerationAudit> findBySessionId(UUID sessionId);
}
