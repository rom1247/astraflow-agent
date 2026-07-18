package com.astraflow.agent.domain.repository;

import com.astraflow.agent.domain.model.Message;

import java.util.List;
import java.util.UUID;

/**
 * 消息仓储端口（零框架依赖，返回领域实体）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public interface MessageRepository {

    /**
     * 保存一条消息。
     *
     * @param message 消息实体
     * @return 已持久化的消息
     */
    Message save(Message message);

    /**
     * 按会话查询全部消息（按创建时间升序，用于重建对话历史）。
     *
     * @param sessionId 会话 ID
     * @return 该会话的消息列表
     */
    List<Message> findBySessionId(UUID sessionId);
}
