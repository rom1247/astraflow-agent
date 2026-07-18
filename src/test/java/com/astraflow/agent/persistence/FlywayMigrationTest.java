package com.astraflow.agent.persistence;

import com.astraflow.agent.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway V1 迁移测试：断言 {@code V1__init.sql} 在真实 PostgreSQL 上迁移成功，
 * 5 张表（sessions/messages/agent_events/tool_calls/moderation_audit）结构、默认值、
 * 索引、唯一约束与外键均符合 phase0-mvp §2.1 DDL 草案。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class FlywayMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ----------------------------- 2.1 sessions -----------------------------

    @Test
    @DisplayName("sessions 表存在且结构正确：id UUID 主键、last_event_seq BIGINT NOT NULL DEFAULT 0、status VARCHAR(16) NOT NULL、时间戳 NOT NULL DEFAULT now()")
    void testSessionsTable_existsWithCorrectStructure() {
        assertThat(tableExists("sessions")).isTrue();

        assertThat(columnType("sessions", "id")).isEqualTo("uuid");
        assertThat(isPrimaryKey("sessions", "id")).isTrue();

        assertThat(columnType("sessions", "last_event_seq")).isEqualTo("bigint");
        assertThat(isNullable("sessions", "last_event_seq")).isFalse();
        assertThat(columnDefault("sessions", "last_event_seq")).contains("0");

        assertThat(columnType("sessions", "status")).isEqualTo("character varying");
        assertThat(characterLength("sessions", "status")).isEqualTo(16);
        assertThat(isNullable("sessions", "status")).isFalse();

        assertThat(columnType("sessions", "created_at")).isEqualTo("timestamp with time zone");
        assertThat(isNullable("sessions", "created_at")).isFalse();
        assertThat(columnDefault("sessions", "created_at")).contains("now");

        assertThat(columnType("sessions", "updated_at")).isEqualTo("timestamp with time zone");
        assertThat(isNullable("sessions", "updated_at")).isFalse();
        assertThat(columnDefault("sessions", "updated_at")).contains("now");
    }

    // ----------------------------- 2.2 messages -----------------------------

    @Test
    @DisplayName("messages 表存在：session_id 外键引用 sessions、role/content(JSONB)/tool_use_id/turn_id/created_at、索引 (session_id, created_at)")
    void testMessagesTable_existsWithCorrectStructure() {
        assertThat(tableExists("messages")).isTrue();

        assertThat(columnType("messages", "session_id")).isEqualTo("uuid");
        assertThat(isNullable("messages", "session_id")).isFalse();

        assertThat(columnType("messages", "role")).isEqualTo("character varying");
        assertThat(characterLength("messages", "role")).isEqualTo(16);
        assertThat(isNullable("messages", "role")).isFalse();

        assertThat(columnType("messages", "content")).isEqualTo("jsonb");
        assertThat(isNullable("messages", "content")).isFalse();

        assertThat(columnType("messages", "tool_use_id")).isEqualTo("character varying");
        assertThat(columnType("messages", "turn_id")).isEqualTo("character varying");
        assertThat(columnType("messages", "created_at")).isEqualTo("timestamp with time zone");

        assertThat(indexExists("messages", "idx_messages_session_created")).isTrue();
    }

    // ----------------------------- 2.3 agent_events -----------------------------

    @Test
    @DisplayName("agent_events 表存在：seq BIGINT、UNIQUE(session_id, seq)、event_type VARCHAR(32)、event_json JSONB、status VARCHAR(16) DEFAULT 'PERSISTED'、turn_id")
    void testAgentEventsTable_existsWithCorrectStructure() {
        assertThat(tableExists("agent_events")).isTrue();

        assertThat(columnType("agent_events", "seq")).isEqualTo("bigint");
        assertThat(isNullable("agent_events", "seq")).isFalse();

        assertThat(columnType("agent_events", "event_type")).isEqualTo("character varying");
        assertThat(characterLength("agent_events", "event_type")).isEqualTo(32);

        assertThat(columnType("agent_events", "event_json")).isEqualTo("jsonb");

        assertThat(columnType("agent_events", "status")).isEqualTo("character varying");
        assertThat(characterLength("agent_events", "status")).isEqualTo(16);
        assertThat(columnDefault("agent_events", "status")).contains("PERSISTED");

        assertThat(columnType("agent_events", "turn_id")).isEqualTo("character varying");

        assertThat(uniqueConstraintExists("agent_events", "uq_agent_events_session_seq")).isTrue();
    }

    // ----------------------------- 2.4 tool_calls -----------------------------

    @Test
    @DisplayName("tool_calls 表存在：session_id/turn_id/tool/input(JSONB)/output(JSONB)/status/latency_ms/cost_tokens/created_at、索引 (session_id, created_at)")
    void testToolCallsTable_existsWithCorrectStructure() {
        assertThat(tableExists("tool_calls")).isTrue();

        assertThat(columnType("tool_calls", "session_id")).isEqualTo("uuid");
        assertThat(columnType("tool_calls", "turn_id")).isEqualTo("character varying");
        assertThat(columnType("tool_calls", "tool")).isEqualTo("character varying");
        assertThat(columnType("tool_calls", "input")).isEqualTo("jsonb");
        assertThat(columnType("tool_calls", "output")).isEqualTo("jsonb");
        assertThat(columnType("tool_calls", "status")).isEqualTo("character varying");
        assertThat(columnType("tool_calls", "latency_ms")).isEqualTo("integer");
        assertThat(columnType("tool_calls", "cost_tokens")).isEqualTo("integer");
        assertThat(columnType("tool_calls", "created_at")).isEqualTo("timestamp with time zone");

        assertThat(indexExists("tool_calls", "idx_tool_calls_session_created")).isTrue();
    }

    // ----------------------------- 2.5 moderation_audit -----------------------------

    @Test
    @DisplayName("moderation_audit 表存在：session_id/turn_id/node/tool/hit_rule/decision/content_digest/created_at、索引 (session_id, turn_id)")
    void testModerationAuditTable_existsWithCorrectStructure() {
        assertThat(tableExists("moderation_audit")).isTrue();

        assertThat(columnType("moderation_audit", "session_id")).isEqualTo("uuid");
        assertThat(columnType("moderation_audit", "turn_id")).isEqualTo("character varying");
        assertThat(columnType("moderation_audit", "node")).isEqualTo("character varying");
        assertThat(isNullable("moderation_audit", "node")).isFalse();
        assertThat(columnType("moderation_audit", "tool")).isEqualTo("character varying");
        assertThat(columnType("moderation_audit", "hit_rule")).isEqualTo("character varying");
        assertThat(columnType("moderation_audit", "decision")).isEqualTo("character varying");
        assertThat(isNullable("moderation_audit", "decision")).isFalse();
        assertThat(columnType("moderation_audit", "content_digest")).isEqualTo("character varying");
        assertThat(columnType("moderation_audit", "created_at")).isEqualTo("timestamp with time zone");

        assertThat(indexExists("moderation_audit", "idx_moderation_audit_session_turn")).isTrue();
    }

    // ----------------------------- 2.6 外键约束 -----------------------------

    @Test
    @DisplayName("外键约束生效：向 messages 插入不存在 session_id 抛外键违例")
    void testMessagesForeignKeyConstraint_enforced() {
        UUID nonexistentSessionId = UUID.randomUUID();
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO messages(session_id, role, content) VALUES (?, 'USER', '{}'::jsonb)",
                        nonexistentSessionId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ----------------------------- 内省辅助方法 -----------------------------

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    private String columnType(String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = ? AND column_name = ?",
                String.class, tableName, columnName);
    }

    private boolean isNullable(String tableName, String columnName) {
        String nullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = ? AND column_name = ?",
                String.class, tableName, columnName);
        return "YES".equalsIgnoreCase(nullable);
    }

    private String columnDefault(String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                "SELECT column_default FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = ? AND column_name = ?",
                String.class, tableName, columnName);
    }

    private int characterLength(String tableName, String columnName) {
        Integer len = jdbcTemplate.queryForObject(
                "SELECT character_maximum_length FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = ? AND column_name = ?",
                Integer.class, tableName, columnName);
        return len == null ? -1 : len;
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' "
                        + "AND tablename = ? AND indexname = ?",
                Integer.class, tableName, indexName);
        return count != null && count > 0;
    }

    private boolean uniqueConstraintExists(String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints WHERE table_schema = 'public' "
                        + "AND table_name = ? AND constraint_name = ? AND constraint_type = 'UNIQUE'",
                Integer.class, tableName, constraintName);
        return count != null && count > 0;
    }

    private boolean isPrimaryKey(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.key_column_usage kcu "
                        + "JOIN information_schema.table_constraints tc "
                        + "  ON kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema "
                        + "WHERE tc.constraint_type = 'PRIMARY KEY' AND kcu.table_schema = 'public' "
                        + "AND kcu.table_name = ? AND kcu.column_name = ?",
                Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}
