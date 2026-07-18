package com.astraflow.agent.persistence;

import com.astraflow.agent.domain.model.AgentEvent;
import com.astraflow.agent.domain.model.EventType;
import com.astraflow.agent.domain.model.Session;
import com.astraflow.agent.domain.repository.SessionRepository;
import com.astraflow.agent.infrastructure.persistence.postgres.JpaAgentEventRepository;
import com.astraflow.agent.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code agent_events.event_json} 的 {@code JsonNode} round-trip 测试。
 *
 * <p>覆盖 7 种 {@link EventType} 的异构 payload 写入后按 {@code event_type} 反序列化还原、
 * 中文/Unicode 无乱码、null 与空对象边界。同时作为任务 8.7 对 {@code @JdbcTypeCode(SqlTypes.JSON)}
 * 在 Hibernate 7（Boot 4.1）+ PG 可用性的验证：本类全绿即判定原生 JSON 映射可用，无需 fallback。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class EventJsonMappingTest extends AbstractIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private JpaAgentEventRepository agentEventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Test
    @DisplayName("8.1 ASSISTANT_TEXT 事件 round-trip：还原 delta/turnId")
    void testASSISTANT_TEXT_roundTrip() throws Exception {
        JsonNode payload = OBJECT_MAPPER.readTree("{\"delta\":\"你好\",\"turnId\":\"t1\"}");

        AgentEvent found = saveAndReload(EventType.ASSISTANT_TEXT, payload);

        assertThat(found.getEventJson().get("delta").asText()).isEqualTo("你好");
        assertThat(found.getEventJson().get("turnId").asText()).isEqualTo("t1");
    }

    @Test
    @DisplayName("8.2 TOOL_USE 与 TOOL_RESULT 事件 round-trip：异构字段（含嵌套 inputJson）正确还原")
    void testTOOL_USE_andTOOL_RESULT_roundTrip() throws Exception {
        JsonNode toolUsePayload = OBJECT_MAPPER.readTree(
                "{\"groupId\":\"g1\",\"toolUseId\":\"tu1\",\"tool\":\"search\",\"inputJson\":{\"query\":\"天气\",\"limit\":5}}");
        JsonNode toolResultPayload = OBJECT_MAPPER.readTree(
                "{\"groupId\":\"g1\",\"toolUseId\":\"tu1\",\"preview\":\"杭州 28°C\",\"ref\":\"r1\"}");

        AgentEvent toolUse = saveAndReload(EventType.TOOL_USE, toolUsePayload);
        AgentEvent toolResult = saveAndReload(EventType.TOOL_RESULT, toolResultPayload);

        assertThat(toolUse.getEventJson().get("groupId").asText()).isEqualTo("g1");
        assertThat(toolUse.getEventJson().get("toolUseId").asText()).isEqualTo("tu1");
        assertThat(toolUse.getEventJson().get("tool").asText()).isEqualTo("search");
        assertThat(toolUse.getEventJson().get("inputJson").get("query").asText()).isEqualTo("天气");
        assertThat(toolUse.getEventJson().get("inputJson").get("limit").asInt()).isEqualTo(5);

        assertThat(toolResult.getEventJson().get("preview").asText()).isEqualTo("杭州 28°C");
        assertThat(toolResult.getEventJson().get("ref").asText()).isEqualTo("r1");
    }

    @Test
    @DisplayName("8.3 DONE 与 ERROR 事件 round-trip：各字段正确还原、DONE.subtype 为合法枚举值")
    void testDONE_andERROR_roundTrip() throws Exception {
        JsonNode donePayload = OBJECT_MAPPER.readTree(
                "{\"subtype\":\"completed\",\"turns\":3,\"costUsd\":0.012}");
        JsonNode errorPayload = OBJECT_MAPPER.readTree(
                "{\"code\":\"RATE_LIMIT\",\"message\":\"请求过快\",\"turnId\":\"t2\"}");

        AgentEvent done = saveAndReload(EventType.DONE, donePayload);
        AgentEvent error = saveAndReload(EventType.ERROR, errorPayload);

        assertThat(done.getEventJson().get("subtype").asText()).isEqualTo("completed");
        assertThat(done.getEventJson().get("turns").asInt()).isEqualTo(3);
        assertThat(done.getEventJson().get("costUsd").asDouble()).isEqualTo(0.012);

        assertThat(error.getEventJson().get("code").asText()).isEqualTo("RATE_LIMIT");
        assertThat(error.getEventJson().get("message").asText()).isEqualTo("请求过快");
        assertThat(error.getEventJson().get("turnId").asText()).isEqualTo("t2");
    }

    @Test
    @DisplayName("8.4 SESSION_START 与 TURN_END 事件 round-trip：各字段正确还原")
    void testSESSION_START_andTURN_END_roundTrip() throws Exception {
        JsonNode startPayload = OBJECT_MAPPER.readTree(
                "{\"model\":\"deepseek-chat\",\"tools\":[\"search\",\"calc\"]}");
        JsonNode turnEndPayload = OBJECT_MAPPER.readTree("{\"turnId\":\"t1\"}");

        AgentEvent start = saveAndReload(EventType.SESSION_START, startPayload);
        AgentEvent turnEnd = saveAndReload(EventType.TURN_END, turnEndPayload);

        assertThat(start.getEventJson().get("model").asText()).isEqualTo("deepseek-chat");
        assertThat(start.getEventJson().get("tools").get(0).asText()).isEqualTo("search");
        assertThat(start.getEventJson().get("tools").get(1).asText()).isEqualTo("calc");

        assertThat(turnEnd.getEventJson().get("turnId").asText()).isEqualTo("t1");
    }

    @Test
    @DisplayName("8.5 含中文与特殊 Unicode 的 payload round-trip 无乱码")
    void testChineseAndUnicode_roundTripNoMojibake() throws Exception {
        JsonNode payload = OBJECT_MAPPER.readTree(
                "{\"text\":\"你好世界 🌍 café — 日本語\",\"emoji\":\"😀🎉\"}");

        AgentEvent found = saveAndReload(EventType.ASSISTANT_TEXT, payload);

        assertThat(found.getEventJson().get("text").asText()).isEqualTo("你好世界 🌍 café — 日本語");
        assertThat(found.getEventJson().get("emoji").asText()).isEqualTo("😀🎉");
    }

    @Test
    @DisplayName("8.6 event_json 为 null 与空对象 {} 的 round-trip：null 读回仍为 null，{} 读回为空对象节点")
    void testNullAndEmptyObject_roundTrip() {
        // null：落盘读回仍为 null（不得变成字符串 "null"）
        AgentEvent nullEvent = saveAndReload(EventType.TURN_END, null);
        assertThat(nullEvent.getEventJson()).isNull();

        // 空对象 {}：读回为空对象节点
        AgentEvent emptyEvent = saveAndReload(EventType.TURN_END, OBJECT_MAPPER.createObjectNode());
        assertThat(emptyEvent.getEventJson()).isNotNull();
        assertThat(emptyEvent.getEventJson().isObject()).isTrue();
        assertThat(emptyEvent.getEventJson().isEmpty()).isTrue();
    }

    /** 保存一条事件并按主键重新读取，返回重载后的实例。 */
    private AgentEvent saveAndReload(EventType type, JsonNode payload) {
        UUID sessionId = sessionRepository.save(
                Session.create("tenant-1", "user-1", "deepseek-chat", null)).getId();
        AgentEvent saved = agentEventRepository.save(
                AgentEvent.create(sessionId, 1L, "t1", type, payload));
        return agentEventRepository.findById(saved.getId()).orElseThrow();
    }
}
