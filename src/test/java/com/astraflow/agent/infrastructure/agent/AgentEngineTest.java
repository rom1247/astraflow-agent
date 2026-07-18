package com.astraflow.agent.infrastructure.agent;

import com.astraflow.agent.domain.model.AgentEvent;
import com.astraflow.agent.domain.model.EventType;
import com.astraflow.agent.domain.model.LlmErrorCode;
import com.astraflow.agent.domain.model.LlmException;
import com.astraflow.agent.domain.tool.Tool;
import com.astraflow.agent.domain.tool.ToolInputValidator;
import com.astraflow.agent.domain.tool.ToolInvoker;
import com.astraflow.agent.domain.tool.ToolRegistry;
import com.astraflow.agent.domain.tool.builtin.CalculatorTool;
import com.astraflow.agent.infrastructure.agent.support.FakeStreamingChatModel;
import com.astraflow.agent.infrastructure.tool.ToolCallbackAdapter;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentEngine} 运行时单元测试（agent-loop spec 完整事件序列 / 保序 / 并发 / 失败映射 / 最大轮次）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class AgentEngineTest {

    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    @DisplayName("纯文本回答 - 完整事件序列 SESSION_START→ASSISTANT_TEXT*→TURN_END→DONE(success)")
    void testRun_pureText_fullSequence() {
        AgentEngine engine = engineWith(model().streamTokens("你", "好"), List.of(), 10);

        List<AgentEvent> events = engine.run(SESSION_ID, List.of(), "在吗").collectList().block();

        assertEquals(List.of(EventType.SESSION_START, EventType.ASSISTANT_TEXT, EventType.ASSISTANT_TEXT,
                EventType.TURN_END, EventType.DONE), types(events));
        assertEquals("success", subtype(lastDone(events)));
        assertEquals("你好", assistantText(events));
        assertFalse(types(events).contains(EventType.TOOL_USE), "无工具调用");
    }

    @Test
    @DisplayName("含工具调用 - TOOL_USE/TOOL_RESULT 配对且在最终文本之前")
    void testRun_withToolCall_orderedToolThenText() {
        AgentEngine engine = engineWith(model()
                .requestTool("tu1", "calculator", "{\"expression\":\"1+2\"}")
                .streamTokens("结果是3"), List.of(new CalculatorTool()), 10);

        List<AgentEvent> events = engine.run(SESSION_ID, List.of(), "算1+2").collectList().block();

        List<EventType> types = types(events);
        assertTrue(types.indexOf(EventType.TOOL_USE) >= 0
                        && types.indexOf(EventType.TOOL_USE) < types.indexOf(EventType.TOOL_RESULT)
                        && types.indexOf(EventType.TOOL_RESULT) < types.indexOf(EventType.ASSISTANT_TEXT),
                "TOOL_USE→TOOL_RESULT→ASSISTANT_TEXT 有序");
        assertEquals("success", subtype(lastDone(events)));
    }

    @Test
    @DisplayName("一轮多工具调用 - 按 toolUseId 保序且一一对应")
    void testRun_multipleToolsSameRound_orderedById() {
        AgentEngine engine = engineWith(model()
                .requestTools(List.of(
                        new FakeStreamingChatModel.ToolCallSpec("tu_A", "calculator", "{\"expression\":\"1+1\"}"),
                        new FakeStreamingChatModel.ToolCallSpec("tu_B", "calculator", "{\"expression\":\"2+2\"}")))
                .streamTokens("完成"), List.of(new CalculatorTool()), 10);

        List<AgentEvent> events = engine.run(SESSION_ID, List.of(), "算两个").collectList().block();

        List<String> useIds = toolUseIds(events);
        List<String> resultIds = toolResultIds(events);
        assertEquals(List.of("tu_A", "tu_B"), useIds, "TOOL_USE 按 tu_A/tu_B 保序");
        assertEquals(List.of("tu_A", "tu_B"), resultIds, "TOOL_RESULT 按 tu_A/tu_B 保序");
    }

    @Test
    @DisplayName("SESSION_START 与 DONE 各出现且仅出现一次")
    void testRun_lifecycleEvents_eachOnce() {
        AgentEngine engine = engineWith(model().streamTokens("在的"), List.of(), 10);

        List<AgentEvent> events = engine.run(SESSION_ID, List.of(), "在吗").collectList().block();
        List<EventType> types = types(events);

        assertEquals(1, count(types, EventType.SESSION_START), "SESSION_START 恰好一次");
        assertEquals(1, count(types, EventType.DONE), "DONE 恰好一次");
    }

    @Test
    @DisplayName("空历史 - 首轮仍产出完整事件流不报错")
    void testRun_emptyHistory_completes() {
        AgentEngine engine = engineWith(model().streamTokens("好"), List.of(), 10);

        List<AgentEvent> events = engine.run(SESSION_ID, List.of(), "首条").collectList().block();
        List<EventType> types = types(events);

        assertEquals(EventType.SESSION_START, types.get(0));
        assertEquals(EventType.DONE, types.get(types.size() - 1));
    }

    @Test
    @DisplayName("历史消息 - 被纳入本轮上下文驱动模型")
    void testRun_historyIncludedInPrompt() {
        FakeStreamingChatModel fake = model().streamTokens("回复");
        AgentEngine engine = engineWith(fake, List.of(), 10);

        engine.run(SESSION_ID,
                List.of(new UserMessage("A"), new AssistantMessage("B")), "继续").collectList().block();

        List<String> promptTexts = fake.receivedPrompts().get(0).getInstructions().stream()
                .map(Message::getText)
                .toList();
        assertTrue(promptTexts.contains("A"), "历史 user:A 纳入 prompt");
        assertTrue(promptTexts.contains("B"), "历史 assistant:B 纳入 prompt");
    }

    @Test
    @DisplayName("LLM 重试耗尽 - 映射为 DONE(error_during_execution) 不传播原始异常")
    void testRun_llmRetryExhausted_mappedToStructuredDone() {
        AgentEngine engine = engineWith(model()
                .throwError(new LlmException(LlmErrorCode.RETRY_EXHAUSTED, "重试耗尽")), List.of(), 10);

        List<AgentEvent> events = engine.run(SESSION_ID, List.of(), "在吗").collectList().block();

        assertEquals("error_during_execution", subtype(lastDone(events)), "重试耗尽 → error_during_execution");
    }

    @Test
    @DisplayName("熔断打开 - 同样映射为 DONE(error_during_execution)")
    void testRun_circuitOpen_mappedToStructuredDone() {
        AgentEngine engine = engineWith(model()
                .throwError(new LlmException(LlmErrorCode.CIRCUIT_OPEN, "熔断打开")), List.of(), 10);

        List<AgentEvent> events = engine.run(SESSION_ID, List.of(), "在吗").collectList().block();

        assertEquals("error_during_execution", subtype(lastDone(events)));
    }

    @Test
    @DisplayName("达到最大轮次 - 以 DONE(error_max_turns) 终止、轮次受约束不无限循环")
    void testRun_maxTurns_terminates() {
        // 模型每轮都请求工具（永不给出无工具最终回复）
        FakeStreamingChatModel fake = model()
                .requestTool("tu1", "calculator", "{\"expression\":\"1+1\"}")
                .requestTool("tu1", "calculator", "{\"expression\":\"1+1\"}")
                .requestTool("tu1", "calculator", "{\"expression\":\"1+1\"}")
                .requestTool("tu1", "calculator", "{\"expression\":\"1+1\"}")
                .requestTool("tu1", "calculator", "{\"expression\":\"1+1\"}");
        AgentEngine engine = engineWith(fake, List.of(new CalculatorTool()), 2);

        List<AgentEvent> events = engine.run(SESSION_ID, List.of(), "一直算").collectList().block();
        List<EventType> types = types(events);

        assertEquals("error_max_turns", subtype(lastDone(events)), "达到上限 → error_max_turns");
        int toolUseRounds = count(types, EventType.TOOL_USE);
        assertTrue(toolUseRounds >= 1 && toolUseRounds <= 2,
                "轮次受 maxTurns 约束（无无限循环）: " + toolUseRounds);
        assertEquals(EventType.DONE, types.get(types.size() - 1), "DONE 为最后一个事件");
    }

    @Test
    @DisplayName("多会话并发 - N≥10 会话互不串扰")
    void testRun_concurrentSessions_noCrossTalk() {
        int n = 10;
        FakeStreamingChatModel fake = model().echoLastUserMessage();
        AgentEngine engine = engineWith(fake, List.of(), 10);
        List<UUID> sessionIds = IntStream.range(0, n).mapToObj(i -> UUID.randomUUID()).toList();
        List<String> userTexts = IntStream.range(0, n).mapToObj(i -> "会话内容-" + i).toList();

        ConcurrentMap<UUID, List<AgentEvent>> results = new ConcurrentHashMap<>();
        CompletableFuture<?>[] futures = IntStream.range(0, n).mapToObj(i -> CompletableFuture.runAsync(() ->
                results.put(sessionIds.get(i),
                        engine.run(sessionIds.get(i), List.of(), userTexts.get(i)).collectList().block())
        )).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();

        for (int i = 0; i < n; i++) {
            String ownText = userTexts.get(i);
            String ownAssistant = assistantText(results.get(sessionIds.get(i)));
            assertEquals(ownText, ownAssistant, "会话 " + i + " 仅含本会话内容");
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    assertFalse(ownAssistant.contains(userTexts.get(j)), "会话 " + i + " 不含会话 " + j + " 的内容");
                }
            }
        }
    }

    @Test
    @DisplayName("工具前审核 BLOCK - 以 DONE(error_blocked_tool) 收尾且工具未执行")
    void testRun_moderationBlock_mapsToErrorBlockedTool() {
        com.astraflow.agent.domain.port.ModerationService blocking =
                (content, node) -> com.astraflow.agent.domain.model.Decision.BLOCK;
        FakeStreamingChatModel fake = model()
                .requestTool("tu1", "calculator", "{\"expression\":\"1+1\"}")
                .streamTokens("不应到达");
        AgentEngine engine = engineWith(fake, List.of(new CalculatorTool()), 10, blocking);

        List<AgentEvent> events = engine.run(SESSION_ID, List.of(), "算一下").collectList().block();
        List<EventType> types = types(events);

        assertEquals("error_blocked_tool", subtype(lastDone(events)), "BLOCK → error_blocked_tool");
        assertFalse(types.contains(EventType.TOOL_RESULT), "工具未执行（无 TOOL_RESULT）");
    }

    private FakeStreamingChatModel model() {
        return FakeStreamingChatModel.create().withOptions(ToolCallingChatOptions.builder().build());
    }

    private AgentEngine engineWith(FakeStreamingChatModel model, List<Tool> tools, int maxTurns) {
        return engineWith(model, tools, maxTurns, new NoOpModerationService());
    }

    private AgentEngine engineWith(FakeStreamingChatModel model, List<Tool> tools, int maxTurns,
                                   com.astraflow.agent.domain.port.ModerationService moderationService) {
        try {
            ToolRegistry registry = new ToolRegistry(tools);
            ToolInvoker invoker = new ToolInvoker(registry, new ToolInputValidator());
            List<ToolCallback> callbacks = tools.stream()
                    .map(tool -> (ToolCallback) new ToolCallbackAdapter(tool, invoker))
                    .toList();
            AgentChatService chatService = new AgentChatService(model, callbacks);
            ModerationHooks hooks = new ModerationHooks(moderationService);
            CompiledGraph<AgentExecutor.State> graph = AgentGraphFactory.build(
                    chatService, callbacks, null, hooks.executeToolsHook(),
                    AgentGraphFactory.maxRecursionLimit(maxTurns));
            com.astraflow.agent.infrastructure.config.AgentProperties properties =
                    new com.astraflow.agent.infrastructure.config.AgentProperties();
            properties.setMaxTurns(maxTurns);
            return new AgentEngine(graph, properties,
                    new com.astraflow.agent.infrastructure.config.ChatProperties(), registry);
        } catch (GraphStateException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<EventType> types(List<AgentEvent> events) {
        return events.stream().map(AgentEvent::getEventType).toList();
    }

    private static int count(List<EventType> types, EventType target) {
        return (int) types.stream().filter(target::equals).count();
    }

    private static AgentEvent lastDone(List<AgentEvent> events) {
        return events.stream().filter(event -> event.getEventType() == EventType.DONE)
                .reduce((first, second) -> second).orElseThrow();
    }

    private static String subtype(AgentEvent done) {
        return done.getEventJson().get("subtype").asText();
    }

    private static String assistantText(List<AgentEvent> events) {
        StringBuilder builder = new StringBuilder();
        events.stream()
                .filter(event -> event.getEventType() == EventType.ASSISTANT_TEXT)
                .forEach(event -> builder.append(event.getEventJson().get("delta").asText()));
        return builder.toString();
    }

    private static List<String> toolUseIds(List<AgentEvent> events) {
        return events.stream()
                .filter(event -> event.getEventType() == EventType.TOOL_USE)
                .map(event -> event.getEventJson().get("toolUseId").asText())
                .toList();
    }

    private static List<String> toolResultIds(List<AgentEvent> events) {
        return new ArrayList<>(events.stream()
                .filter(event -> event.getEventType() == EventType.TOOL_RESULT)
                .map(event -> event.getEventJson().get("toolUseId").asText())
                .toList());
    }
}
