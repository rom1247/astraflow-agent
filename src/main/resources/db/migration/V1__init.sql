-- V1__init.sql: Phase 0 持久化地基 —— 5 表初始化迁移
-- DDL 来源：docs/design/phase0-mvp.md §2.1
-- 说明：agent_events.event_json 允许 NULL（见 tasks 8.6：null payload round-trip 读回仍为 null，
--       不得变成字符串 "null"），相对 phase0 §2.1 草案的 NOT NULL 为刻意放宽。

-- 会话主表：UUID 业务键即主键（URL 防遍历、无中心分配），last_event_seq 为 seq 行锁载体
CREATE TABLE sessions (
    id             UUID         PRIMARY KEY,
    tenant_id      VARCHAR(64)  NOT NULL,
    user_id        VARCHAR(64),
    model          VARCHAR(64)  NOT NULL,
    system_prompt  TEXT,
    status         VARCHAR(16)  NOT NULL,
    last_event_seq BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 消息表：role 异构内容以 JSONB 承载；按 (session_id, created_at) 重建对话历史
CREATE TABLE messages (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id  UUID         NOT NULL REFERENCES sessions(id),
    role        VARCHAR(16)  NOT NULL,
    content     JSONB        NOT NULL,
    tool_use_id VARCHAR(64),
    turn_id     VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_session_created ON messages(session_id, created_at);

-- 事件日志表：seq = sessions.last_event_seq 自增产物 = SSE eventId；UNIQUE(session_id, seq) 为重放主索引
CREATE TABLE agent_events (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id UUID         NOT NULL REFERENCES sessions(id),
    seq        BIGINT       NOT NULL,
    turn_id    VARCHAR(64),
    event_type VARCHAR(32)  NOT NULL,
    event_json JSONB,
    status     VARCHAR(16)  NOT NULL DEFAULT 'PERSISTED',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_agent_events_session_seq UNIQUE (session_id, seq)
);

-- 工具调用台账：input/output 以 JSONB 承载异构参数与结果
CREATE TABLE tool_calls (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id  UUID         NOT NULL REFERENCES sessions(id),
    turn_id     VARCHAR(64),
    tool        VARCHAR(64),
    input       JSONB,
    output      JSONB,
    status      VARCHAR(16),
    latency_ms  INTEGER,
    cost_tokens INTEGER,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_tool_calls_session_created ON tool_calls(session_id, created_at);

-- 审核审计台账：node 标识审核节点（PRE_LLM/CONTEXT/PRE_TOOL_USE/POST_TOOL_USE/OUTPUT）
CREATE TABLE moderation_audit (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id     UUID         NOT NULL REFERENCES sessions(id),
    turn_id        VARCHAR(64),
    node           VARCHAR(24)  NOT NULL,
    tool           VARCHAR(64),
    hit_rule       VARCHAR(128),
    decision       VARCHAR(16)  NOT NULL,
    content_digest VARCHAR(128),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_moderation_audit_session_turn ON moderation_audit(session_id, turn_id);
