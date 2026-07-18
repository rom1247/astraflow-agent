package com.astraflow.agent.persistence;

import com.astraflow.agent.domain.model.Session;
import com.astraflow.agent.domain.model.SessionStatus;
import com.astraflow.agent.domain.repository.SessionRepository;
import com.astraflow.agent.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Session} 实体与 {@link SessionRepository} 仓储的基础 CRUD 测试。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class SessionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private SessionRepository sessionRepository;

    @Test
    @DisplayName("保存会话后按主键查回字段一致、last_event_seq=0、createdAt 已填充")
    void testSaveSession_retrievedById_fieldsMatch() {
        Session session = Session.create("tenant-1", "user-1", "deepseek-chat", "你是一个助手");

        Session saved = sessionRepository.save(session);
        Session found = sessionRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getTenantId()).isEqualTo("tenant-1");
        assertThat(found.getUserId()).isEqualTo("user-1");
        assertThat(found.getModel()).isEqualTo("deepseek-chat");
        assertThat(found.getSystemPrompt()).isEqualTo("你是一个助手");
        assertThat(found.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(found.getLastEventSeq()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("经领域行为将 status 从 ACTIVE 变为 CLOSED 并保存后，查回为 CLOSED")
    void testUpdateSessionStatus_retrievedAsClosed() {
        Session session = Session.create("tenant-1", "user-1", "deepseek-chat", null);
        Session saved = sessionRepository.save(session);

        saved.updateStatus(SessionStatus.CLOSED);
        sessionRepository.save(saved);

        Session found = sessionRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(SessionStatus.CLOSED);
    }

    @Test
    @DisplayName("按不存在的 UUID 查询返回空")
    void testFindById_whenUuidNotExist_returnsEmpty() {
        Optional<Session> found = sessionRepository.findById(UUID.randomUUID());
        assertThat(found).isEmpty();
    }
}
