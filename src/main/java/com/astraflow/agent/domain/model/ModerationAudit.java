package com.astraflow.agent.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 审核审计台账实体（单轨 {@code @Entity}）。
 *
 * <p>{@code node}/{@code decision} 暂用 {@link String}，枚举建模留待审核行为 change（避免本 change 提前耦合）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "moderation_audit")
public class ModerationAudit {

    /** 自增主键（DB IDENTITY）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 所属会话 ID（外键 sessions.id）。 */
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /** 所属轮次 ID。 */
    @Column(name = "turn_id", length = 64)
    private String turnId;

    /** 审核节点（PRE_LLM/CONTEXT/PRE_TOOL_USE/POST_TOOL_USE/OUTPUT）。 */
    @Column(name = "node", nullable = false, length = 24)
    private String node;

    /** 触发审核的工具名（可空）。 */
    @Column(name = "tool", length = 64)
    private String tool;

    /** 命中规则（可空）。 */
    @Column(name = "hit_rule", length = 128)
    private String hitRule;

    /** 审核决定（PASS/BLOCK/REDACT/HUMAN_REVIEW）。 */
    @Column(name = "decision", nullable = false, length = 16)
    private String decision;

    /** 内容摘要（可空）。 */
    @Column(name = "content_digest", length = 128)
    private String contentDigest;

    /** 创建时间。 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 创建一条审核审计记录。
     *
     * @param sessionId     所属会话 ID
     * @param turnId        轮次 ID（可空）
     * @param node          审核节点
     * @param tool          工具名（可空）
     * @param hitRule       命中规则（可空）
     * @param decision      审核决定
     * @param contentDigest 内容摘要（可空）
     * @return 未持久化的审核审计实例
     */
    public static ModerationAudit create(UUID sessionId, String turnId, String node, String tool,
                                         String hitRule, String decision, String contentDigest) {
        ModerationAudit audit = new ModerationAudit();
        audit.sessionId = sessionId;
        audit.turnId = turnId;
        audit.node = node;
        audit.tool = tool;
        audit.hitRule = hitRule;
        audit.decision = decision;
        audit.contentDigest = contentDigest;
        return audit;
    }
}
