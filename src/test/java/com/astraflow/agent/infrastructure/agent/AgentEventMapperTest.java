package com.astraflow.agent.infrastructure.agent;

import com.astraflow.agent.domain.model.AgentEvent;
import com.astraflow.agent.domain.model.EventType;
import com.astraflow.agent.domain.model.LlmErrorCode;
import com.astraflow.agent.domain.model.LlmException;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AgentEventMapper} 流式映射单元测试（agent-loop spec，复刻 spike V5）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class AgentEventMapperTest {

    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String TURN_ID = "turn-1";

    @Test
    @DisplayName("ASSISTANT_TEXT - 多 chunk 拼接为完整文本")
    void testToFlux_streamingChunks_produceAssistantText() {
        AsyncGenerator<NodeOutput<AgentExecutor.State>> gen = generatorOf(
                stream("agent", "你"),
                stream("agent", "好"));

        List<AgentEvent> events = AgentEventMapper.toFlux(gen, SESSION_ID, TURN_ID).collectList().block();

        List<EventType> types = eventTypes(events);
        assertEquals(2, count(types, EventType.ASSISTANT_TEXT), "两个 chunk → 两个 ASSISTANT_TEXT");
        assertEquals("你", events.get(0).getEventJson().get("delta").asText());
        assertEquals("好", events.get(1).getEventJson().get("delta").asText());
    }

    @Test
    @DisplayName("TOOL_USE - agent 节点完成后由 AssistantMessage.toolCalls 产出")
    void testToFlux_assistantWithToolCalls_produceToolUse() {
        AssistantMessage assistant = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("tu1", "function", "calculator", "{\"expression\":\"1+2\"}")))
                .build();
        NodeOutput<AgentExecutor.State> node = new NodeOutput<>("agent", stateWith(assistant));

        List<AgentEvent> events = AgentEventMapper.toFlux(generatorOf(node), SESSION_ID, TURN_ID).collectList().block();

        AgentEvent toolUse = firstOfType(events, EventType.TOOL_USE);
        assertEquals("tu1", toolUse.getEventJson().get("toolUseId").asText());
        assertEquals("calculator", toolUse.getEventJson().get("tool").asText());
        assertEquals("{\"expression\":\"1+2\"}", toolUse.getEventJson().get("inputJson").asText());
    }

    @Test
    @DisplayName("TOOL_RESULT - action 节点 ToolResponseMessage 产出配对结果")
    void testToFlux_toolResponse_produceToolResult() {
        ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("tu1", "calculator", "3.0")))
                .build();
        NodeOutput<AgentExecutor.State> node = new NodeOutput<>("action", stateWith(toolResponse));

        List<AgentEvent> events = AgentEventMapper.toFlux(generatorOf(node), SESSION_ID, TURN_ID).collectList().block();

        AgentEvent toolResult = firstOfType(events, EventType.TOOL_RESULT);
        assertEquals("tu1", toolResult.getEventJson().get("toolUseId").asText());
        assertEquals("calculator", toolResult.getEventJson().get("tool").asText());
        assertEquals("3.0", toolResult.getEventJson().get("preview").asText());
    }

    @Test
    @DisplayName("DONE - NodeOutput.isEND 时产出 DONE")
    void testToFlux_endNode_produceDone() {
        NodeOutput<AgentExecutor.State> end = new NodeOutput<>("__END__", stateWith(new AssistantMessage("好")));

        List<AgentEvent> events = AgentEventMapper.toFlux(generatorOf(end), SESSION_ID, TURN_ID).collectList().block();

        assertEquals(1, count(eventTypes(events), EventType.DONE), "END 节点 → 一个 DONE");
        AgentEvent done = firstOfType(events, EventType.DONE);
        assertEquals("success", done.getEventJson().get("subtype").asText());
    }

    @Test
    @DisplayName("错误传播 - AsyncGenerator 抛异常时以错误信号传播")
    void testToFlux_generatorThrows_propagatesError() {
        LlmException toThrow = new LlmException(LlmErrorCode.RETRY_EXHAUSTED, "重试耗尽");
        AsyncGenerator<NodeOutput<AgentExecutor.State>> gen = throwingGenerator(toThrow);

        assertThrows(Exception.class,
                () -> AgentEventMapper.toFlux(gen, SESSION_ID, TURN_ID).collectList().block(),
                "生成器异常经 sink.error 传播");
    }

    @Test
    @DisplayName("有序 - 事件类型序列与产出顺序一致")
    void testToFlux_fullSequence_orderedTypes() {
        AssistantMessage assistantWithTools = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("tu1", "function", "calculator", "{}")))
                .build();
        ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("tu1", "calculator", "3.0")))
                .build();

        List<AgentEvent> events = AgentEventMapper.toFlux(generatorOf(
                new NodeOutput<>("agent", stateWith(assistantWithTools)),
                new NodeOutput<>("action", stateWith(toolResponse)),
                new NodeOutput<>("__END__", stateWith(new AssistantMessage("结果3")))
        ), SESSION_ID, TURN_ID).collectList().block();

        List<EventType> expected = List.of(EventType.TOOL_USE, EventType.TOOL_RESULT, EventType.DONE);
        assertIterableEquals(expected, eventTypes(events), "事件按节点顺序映射");
    }

    private static StreamingOutput<AgentExecutor.State> stream(String node, String chunk) {
        // StreamingOutput 构造顺序为 (chunk, node, state, metadata)——chunk 为首参（见 1.8.20 字节码）
        return new StreamingOutput<>(chunk, node, stateWith(new AssistantMessage("")), null);
    }

    private static AgentExecutor.State stateWith(org.springframework.ai.chat.messages.Message message) {
        return new AgentExecutor.State(Map.of(MessagesState.MESSAGES_STATE, List.of(message)));
    }

    @SafeVarargs
    private static AsyncGenerator<NodeOutput<AgentExecutor.State>> generatorOf(NodeOutput<AgentExecutor.State>... outputs) {
        return AsyncGenerator.from(List.of(outputs).iterator());
    }

    private static AsyncGenerator<NodeOutput<AgentExecutor.State>> throwingGenerator(RuntimeException toThrow) {
        return AsyncGenerator.from(new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public NodeOutput<AgentExecutor.State> next() {
                throw toThrow;
            }
        });
    }

    private static List<EventType> eventTypes(List<AgentEvent> events) {
        return events.stream().map(AgentEvent::getEventType).toList();
    }

    private static int count(List<EventType> types, EventType target) {
        return (int) types.stream().filter(target::equals).count();
    }

    private static AgentEvent firstOfType(List<AgentEvent> events, EventType type) {
        return events.stream().filter(event -> event.getEventType() == type).findFirst().orElseThrow();
    }
}
