package com.astraflow.agent.persistence;

import com.astraflow.agent.domain.model.Message;
import com.astraflow.agent.domain.model.MessageRole;
import com.astraflow.agent.domain.model.Session;
import com.astraflow.agent.domain.repository.MessageRepository;
import com.astraflow.agent.domain.repository.SessionRepository;
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
 * {@link Message} 实体与 {@link MessageRepository} 仓储的基础 CRUD 测试。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class MessageRepositoryTest extends AbstractIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Test
    @DisplayName("为某会话保存多条异构消息后按 session_id 查回全部、字段正确还原")
    void testSaveMultipleMessages_retrievedBySessionId() throws Exception {
        UUID sessionId = sessionRepository.save(
                Session.create("tenant-1", "user-1", "deepseek-chat", null)).getId();

        JsonNode userContent = OBJECT_MAPPER.readTree("{\"text\":\"你好\"}");
        JsonNode assistantContent = OBJECT_MAPPER.readTree("{\"text\":\"您好\",\"toolCalls\":[{\"id\":\"call_1\"}]}");
        JsonNode toolContent = OBJECT_MAPPER.readTree("{\"toolResult\":{\"ok\":true}}");

        messageRepository.save(Message.create(sessionId, MessageRole.USER, userContent, null, "t0"));
        messageRepository.save(Message.create(sessionId, MessageRole.ASSISTANT, assistantContent, null, "t1"));
        messageRepository.save(Message.create(sessionId, MessageRole.TOOL, toolContent, "call_1", "t1"));

        List<Message> found = messageRepository.findBySessionId(sessionId);

        assertThat(found).hasSize(3);
        // 按 created_at 升序：USER -> ASSISTANT -> TOOL
        assertThat(found).extracting(Message::getRole)
                .containsExactly(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.TOOL);

        Message userMsg = found.get(0);
        assertThat(userMsg.getSessionId()).isEqualTo(sessionId);
        assertThat(userMsg.getContent().get("text").asText()).isEqualTo("你好");
        assertThat(userMsg.getTurnId()).isEqualTo("t0");

        Message assistantMsg = found.get(1);
        assertThat(assistantMsg.getContent().get("text").asText()).isEqualTo("您好");
        assertThat(assistantMsg.getContent().get("toolCalls").get(0).get("id").asText()).isEqualTo("call_1");

        Message toolMsg = found.get(2);
        assertThat(toolMsg.getRole()).isEqualTo(MessageRole.TOOL);
        assertThat(toolMsg.getToolUseId()).isEqualTo("call_1");
        assertThat(toolMsg.getContent().get("toolResult").get("ok").asBoolean()).isTrue();
    }
}
