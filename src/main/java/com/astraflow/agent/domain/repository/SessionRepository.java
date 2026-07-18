package com.astraflow.agent.domain.repository;

import com.astraflow.agent.domain.model.Session;

import java.util.Optional;
import java.util.UUID;

/**
 * 会话仓储端口（零框架依赖，返回领域实体）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public interface SessionRepository {

    /**
     * 保存（新增或更新）会话。
     *
     * @param session 会话实体
     * @return 已持久化的会话
     */
    Session save(Session session);

    /**
     * 按主键查询会话。
     *
     * @param id 会话 ID
     * @return 命中的会话，不存在返回 {@link Optional#empty()}
     */
    Optional<Session> findById(UUID id);
}
