package com.astraflow.agent.persistence;

import com.astraflow.agent.domain.model.AgentEvent;
import com.astraflow.agent.domain.model.EventType;
import com.astraflow.agent.domain.model.Session;
import com.astraflow.agent.domain.repository.AgentEventRepository;
import com.astraflow.agent.domain.repository.SessionRepository;
import com.astraflow.agent.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * seq 热路径测试：{@link AgentEventRepository#append} 的单调递增、并发不重号、跨 session 独立、自治事务、
 * 不存在会话异常等行为契约。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class AgentEventSequencingTest extends AbstractIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private AgentEventRepository agentEventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("9.1 单线程顺序 append N 个事件，seq 严格单调递增 1..N，= agent_events.seq = sessions.last_event_seq")
    void testSingleThreadSequentialAppend_seqMonotonicallyIncreasing() throws Exception {
        UUID sessionId = newSession();

        long seq1 = agentEventRepository.append(event(sessionId, "t1"));
        long seq2 = agentEventRepository.append(event(sessionId, "t2"));
        long seq3 = agentEventRepository.append(event(sessionId, "t3"));

        assertThat(seq1).isEqualTo(1L);
        assertThat(seq2).isEqualTo(2L);
        assertThat(seq3).isEqualTo(3L);

        // 返回值 = agent_events.seq = sessions.last_event_seq 新值
        Long dbSeq = jdbcTemplate.queryForObject(
                "SELECT seq FROM agent_events WHERE session_id = ? AND seq = ?", Long.class, sessionId, seq3);
        assertThat(dbSeq).isEqualTo(seq3);
        assertThat(lastEventSeq(sessionId)).isEqualTo(seq3);
    }

    @Test
    @DisplayName("9.2 UNIQUE(session_id, seq)：重复插入抛唯一约束违例")
    void testUniqueConstraint_duplicateInsertThrowsException() {
        UUID sessionId = newSession();
        long seq = agentEventRepository.append(event(sessionId, "t1"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO agent_events(session_id, seq, event_type, status) VALUES (?, ?, 'ASSISTANT_TEXT', 'PERSISTED')",
                sessionId, seq))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("9.3 ≥8 线程并发 append 同一 session：全部成功、seq 无重复连续整数 1..N、last_event_seq=N")
    void testMultiThreadConcurrentAppend_noDuplicateSeq() {
        int threads = 8;
        int perThread = 5;
        int total = threads * perThread;
        UUID sessionId = newSession();

        Set<Long> allSeqs = concurrentAppend(sessionId, threads, perThread);

        assertThat(allSeqs).hasSize(total);
        assertThat(allSeqs).containsExactlyInAnyOrderElementsOf(
                LongStream.rangeClosed(1, total).boxed().collect(Collectors.toList()));
        assertThat(lastEventSeq(sessionId)).isEqualTo(total);
        assertThat(eventCount(sessionId)).isEqualTo(total);
    }

    @Test
    @DisplayName("9.4 100 个事件多线程并发 append：记录数=100、last_event_seq=100")
    void test100EventsConcurrentAppend() {
        int total = 100;
        UUID sessionId = newSession();

        Set<Long> allSeqs = concurrentAppend(sessionId, 10, 10);

        assertThat(allSeqs).hasSize(total);
        assertThat(eventCount(sessionId)).isEqualTo(total);
        assertThat(lastEventSeq(sessionId)).isEqualTo(total);
    }

    @Test
    @DisplayName("9.5 两个独立会话交错并发 append：A 的 seq 为 1..a、B 为 1..b，互不干扰")
    void testCrossSessionIndependentCounting() throws Exception {
        UUID sessionA = newSession();
        UUID sessionB = newSession();
        int aCount = 2;
        int bCount = 3;

        ExecutorService exec = Executors.newFixedThreadPool(2);
        try {
            Future<List<Long>> futureA = exec.submit(() -> appendN(sessionA, aCount));
            Future<List<Long>> futureB = exec.submit(() -> appendN(sessionB, bCount));

            List<Long> seqsA = futureA.get();
            List<Long> seqsB = futureB.get();

            assertThat(seqsA).containsExactlyInAnyOrder(1L, 2L);
            assertThat(seqsB).containsExactlyInAnyOrder(1L, 2L, 3L);
            assertThat(lastEventSeq(sessionA)).isEqualTo(aCount);
            assertThat(lastEventSeq(sessionB)).isEqualTo(bCount);
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    @DisplayName("9.6 自治事务：外层事务回滚不影响已 append 的事件与 seq 自增")
    void testAutonomousTransaction_eventSurvivesOuterRollback() {
        UUID sessionId = newSession();

        new TransactionTemplate(transactionManager).execute(status -> {
            agentEventRepository.append(event(sessionId, "t1")); // REQUIRES_NEW 自治提交
            status.setRollbackOnly(); // 回滚外层事务
            return null;
        });

        assertThat(eventCount(sessionId)).isEqualTo(1);
        assertThat(lastEventSeq(sessionId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("9.7 append 指向不存在会话：抛 DataAccessException，不静默返回 0 或 null")
    void testMissingSession_appendThrowsException() {
        UUID missingSessionId = UUID.randomUUID();

        assertThatThrownBy(() -> agentEventRepository.append(event(missingSessionId, "t1")))
                .isInstanceOf(DataAccessException.class);
    }

    // ----------------------------- 辅助方法 -----------------------------

    private UUID newSession() {
        return sessionRepository.save(
                Session.create("tenant-1", "user-1", "deepseek-chat", null)).getId();
    }

    private AgentEvent event(UUID sessionId, String turnId) {
        try {
            return AgentEvent.create(sessionId, 0L, turnId, EventType.ASSISTANT_TEXT,
                    OBJECT_MAPPER.readTree("{\"delta\":\"hi\"}"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 用 {@code threads} 个线程对 {@code sessionId} 各 append {@code perThread} 个事件，返回全部 seq 集合。 */
    private Set<Long> concurrentAppend(UUID sessionId, int threads, int perThread) {
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<Long>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(exec.submit(() -> appendN(sessionId, perThread)));
            }
            Set<Long> allSeqs = new HashSet<>();
            for (Future<List<Long>> future : futures) {
                allSeqs.addAll(future.get());
            }
            return allSeqs;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发 append 被中断", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("并发 append 任务异常", e.getCause());
        } finally {
            exec.shutdownNow();
        }
    }

    /** 对 {@code sessionId} 顺序 append {@code n} 个事件，返回 seq 列表。 */
    private List<Long> appendN(UUID sessionId, int n) {
        List<Long> seqs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            seqs.add(agentEventRepository.append(event(sessionId, "t" + i)));
        }
        return seqs;
    }

    private long lastEventSeq(UUID sessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT last_event_seq FROM sessions WHERE id = ?", Long.class, sessionId);
    }

    private long eventCount(UUID sessionId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_events WHERE session_id = ?", Long.class, sessionId);
        return count == null ? 0 : count;
    }
}
