package com.astraflow.agent.persistence;

import com.astraflow.agent.domain.model.ModerationAudit;
import com.astraflow.agent.domain.model.Session;
import com.astraflow.agent.domain.repository.ModerationAuditRepository;
import com.astraflow.agent.domain.repository.SessionRepository;
import com.astraflow.agent.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ModerationAudit} 实体与 {@link ModerationAuditRepository} 仓储的基础 CRUD 测试。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class ModerationAuditRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ModerationAuditRepository moderationAuditRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Test
    @DisplayName("保存审核审计记录（node=PRE_LLM、decision=PASS）后按 session_id 查回字段一致")
    void testSaveModerationAudit_retrievedBySessionId() {
        UUID sessionId = sessionRepository.save(
                Session.create("tenant-1", "user-1", "deepseek-chat", null)).getId();

        moderationAuditRepository.save(ModerationAudit.create(
                sessionId, "t1", "PRE_LLM", null, null, "PASS", "digest-abc"));

        List<ModerationAudit> found = moderationAuditRepository.findBySessionId(sessionId);

        assertThat(found).hasSize(1);
        ModerationAudit audit = found.get(0);
        assertThat(audit.getSessionId()).isEqualTo(sessionId);
        assertThat(audit.getTurnId()).isEqualTo("t1");
        assertThat(audit.getNode()).isEqualTo("PRE_LLM");
        assertThat(audit.getDecision()).isEqualTo("PASS");
        assertThat(audit.getContentDigest()).isEqualTo("digest-abc");
    }
}
