package com.astraflow.agent.persistence;

import com.astraflow.agent.domain.model.Session;
import com.astraflow.agent.domain.model.ToolCall;
import com.astraflow.agent.domain.repository.SessionRepository;
import com.astraflow.agent.domain.repository.ToolCallRepository;
import com.astraflow.agent.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolCall} 实体与 {@link ToolCallRepository} 仓储的基础 CRUD 测试。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class ToolCallRepositoryTest extends AbstractIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private ToolCallRepository toolCallRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Test
    @DisplayName("保存工具调用记录（input/output JSONB）后按 session_id 查回字段一致")
    void testSaveToolCall_retrievedBySessionId() throws Exception {
        UUID sessionId = sessionRepository.save(
                Session.create("tenant-1", "user-1", "deepseek-chat", null)).getId();

        JsonNode input = OBJECT_MAPPER.readTree("{\"query\":\"天气\",\"city\":\"杭州\"}");
        JsonNode output = OBJECT_MAPPER.readTree("{\"temp\":28,\"desc\":\"晴\"}");

        toolCallRepository.save(ToolCall.create(sessionId, "t1", "get_weather", input, output, "SUCCESS"));

        List<ToolCall> found = toolCallRepository.findBySessionId(sessionId);

        assertThat(found).hasSize(1);
        ToolCall call = found.get(0);
        assertThat(call.getSessionId()).isEqualTo(sessionId);
        assertThat(call.getTurnId()).isEqualTo("t1");
        assertThat(call.getTool()).isEqualTo("get_weather");
        assertThat(call.getStatus()).isEqualTo("SUCCESS");
        assertThat(call.getInput().get("city").asText()).isEqualTo("杭州");
        assertThat(call.getInput().get("query").asText()).isEqualTo("天气");
        assertThat(call.getOutput().get("temp").asInt()).isEqualTo(28);
        assertThat(call.getOutput().get("desc").asText()).isEqualTo("晴");
    }
}
