package com.astraflow.agent.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 会话聚合根（单轨 {@code @Entity}）。
 *
 * <p>UUID 业务键即主键（应用侧 {@link #create(UUID)} 生成），不使用 {@code @GeneratedValue}。
 * {@code lastEventSeq} 为事件 seq 行锁载体（由 {@code append} 热路径 {@code UPDATE…RETURNING} 自增）。
 * 禁 {@code @Setter}/{@code @Data}，状态变更仅经领域行为方法。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "sessions")
public class Session {

    /** 业务键即主键。 */
    @Id
    @Column(name = "id")
    private UUID id;

    /** 租户标识（MVP 单租户固定值）。 */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /** 发起用户标识。 */
    @Column(name = "user_id", length = 64)
    private String userId;

    /** 使用的模型名（如 deepseek-chat）。 */
    @Column(name = "model", nullable = false, length = 64)
    private String model;

    /** 系统提示词。 */
    @Column(name = "system_prompt", columnDefinition = "text")
    private String systemPrompt;

    /** 会话状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SessionStatus status;

    /** 已落盘事件的最大 seq（行锁自增载体），初始 0。 */
    @Column(name = "last_event_seq", nullable = false)
    private long lastEventSeq;

    /** 创建时间（DB 默认 now()，此处由 Hibernate 写入）。 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 最后更新时间（每次 update 刷新）。 */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 创建一个 ACTIVE 状态的新会话。
     *
     * @param tenantId     租户标识
     * @param userId       用户标识（可空）
     * @param model        模型名
     * @param systemPrompt 系统提示词（可空）
     * @return 新会话实例（未持久化，{@code lastEventSeq}=0、{@code status}=ACTIVE）
     */
    public static Session create(String tenantId, String userId, String model, String systemPrompt) {
        Session session = new Session();
        session.id = UUID.randomUUID();
        session.tenantId = tenantId;
        session.userId = userId;
        session.model = model;
        session.systemPrompt = systemPrompt;
        session.status = SessionStatus.ACTIVE;
        session.lastEventSeq = 0L;
        return session;
    }

    /**
     * 变更会话状态（领域行为）。
     *
     * @param status 新状态
     */
    public void updateStatus(SessionStatus status) {
        this.status = status;
    }
}
