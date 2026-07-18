package com.astraflow.agent.domain.repository;

import com.astraflow.agent.domain.model.ToolCall;

import java.util.List;
import java.util.UUID;

/**
 * 工具调用台账仓储端口（零框架依赖，返回领域实体）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public interface ToolCallRepository {

    /**
     * 保存一条工具调用记录。
     *
     * @param toolCall 工具调用实体
     * @return 已持久化的工具调用
     */
    ToolCall save(ToolCall toolCall);

    /**
     * 按会话查询全部工具调用记录。
     *
     * @param sessionId 会话 ID
     * @return 该会话的工具调用列表
     */
    List<ToolCall> findBySessionId(UUID sessionId);
}
