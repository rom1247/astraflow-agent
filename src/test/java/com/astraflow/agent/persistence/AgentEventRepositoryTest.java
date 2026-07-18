package com.astraflow.agent.persistence;

import com.astraflow.agent.domain.model.AgentEvent;
import com.astraflow.agent.domain.model.EventType;
import com.astraflow.agent.domain.model.Session;
import com.astraflow.agent.domain.repository.SessionRepository;
import com.astraflow.agent.infrastructure.persistence.postgres.JpaAgentEventRepository;
import com.astraflow.agent.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgentEvent} 实体与 {@link JpaAgentEventRepository} 的基础 CRUD 测试，
 * 验证 {@code event_type} 以枚举名字符串（非 ORDINAL）持久化。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class AgentEventRepositoryTest extends AbstractIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private JpaAgentEventRepository agentEventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("保存 AgentEvent 后按 id 查回，event_type 列持久化为枚举名字符串（非 ORDINAL）")
    void testSaveEvent_eventTypePersistedAsEnumNameString() throws Exception {
        UUID sessionId = sessionRepository.save(
                Session.create("tenant-1", "user-1", "deepseek-chat", null)).getId();

        AgentEvent event = AgentEvent.create(sessionId, 1L, "t1", EventType.SESSION_START,
                OBJECT_MAPPER.readTree("{\"model\":\"deepseek-chat\"}"));
        AgentEvent saved = agentEventRepository.save(event);

        AgentEvent found = agentEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getEventType()).isEqualTo(EventType.SESSION_START);
        assertThat(found.getSessionId()).isEqualTo(sessionId);
        assertThat(found.getSeq()).isEqualTo(1L);
        assertThat(found.getStatus()).isEqualTo("PERSISTED");
        assertThat(found.getEventJson().get("model").asText()).isEqualTo("deepseek-chat");

        // 直接查列值，确认持久化为字符串 "SESSION_START" 而非 ORDINAL 数字
        String dbEventType = jdbcTemplate.queryForObject(
                "SELECT event_type FROM agent_events WHERE id = ?", String.class, saved.getId());
        assertThat(dbEventType).isEqualTo("SESSION_START");
    }
}
