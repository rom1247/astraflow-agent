package com.astraflow.agent.infrastructure.agent;

import com.astraflow.agent.domain.tool.Tool;
import com.astraflow.agent.domain.tool.ToolInputValidator;
import com.astraflow.agent.domain.tool.ToolInvoker;
import com.astraflow.agent.domain.tool.ToolRegistry;
import com.astraflow.agent.infrastructure.agent.support.RecordingTool;
import com.astraflow.agent.infrastructure.tool.ToolCallbackAdapter;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.agent.Agent;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ParallelExecuteToolsAction} 单元测试（tool-invocation spec「并行执行 + 保序 + 失败隔离」）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class ParallelExecuteToolsActionTest {

    private static final RunnableConfig CONFIG =
            RunnableConfig.builder().threadId("parallel-action-test").build();

    @Test
    @DisplayName("无工具调用 - 节点直接转向结束")
    void testApply_whenNoToolCalls_returnsEndCommand() {
        AssistantMessage plainAssistant = new AssistantMessage("最终回复");
        AgentExecutor.State state = stateWith(plainAssistant);

        ParallelExecuteToolsAction<AgentExecutor.State> action =
                new ParallelExecuteToolsAction<>(List.of());
        Command command = action.apply(state, CONFIG).join();

        assertEquals(Agent.END_LABEL, command.gotoNodeSafe().orElse(null), "无工具调用 → END");
    }

    @Test
    @DisplayName("多工具并行执行并按 toolUseId 保序回收")
    void testApply_whenMultipleTools_parallelAndOrdered() {
        RecordingTool toolA = new RecordingTool("tool_a", false, 60);
        RecordingTool toolB = new RecordingTool("tool_b", false, 60);
        List<ToolCallback> callbacks = adapters(toolA, toolB);

        AssistantMessage assistant = assistantWithToolCalls(
                new AssistantMessage.ToolCall("tu_A", "function", "tool_a", "{}"),
                new AssistantMessage.ToolCall("tu_B", "function", "tool_b", "{}"));

        ParallelExecuteToolsAction<AgentExecutor.State> action = new ParallelExecuteToolsAction<>(callbacks);
        Command command = action.apply(stateWith(assistant), CONFIG).join();

        assertEquals(Agent.AGENT_LABEL, command.gotoNodeSafe().orElse(null), "有工具调用 → 回 agent");
        List<String> ids = responseIds(command);
        assertEquals(List.of("tu_A", "tu_B"), ids, "回收按 toolCalls 原序");

        // 并行：两个工具均虚拟线程执行且时间窗口重叠
        assertEquals(1, toolA.records().size());
        assertEquals(1, toolB.records().size());
        RecordingTool.Record a = toolA.records().get(0);
        RecordingTool.Record b = toolB.records().get(0);
        assertTrue(a.virtual() && b.virtual(), "在虚拟线程上执行");
        assertTrue(overlap(a, b), "执行时间窗口重叠（并发）");
    }

    @Test
    @DisplayName("同轮一个工具失败不影响其它工具，失败以结构化结果表达")
    void testApply_whenOneFails_othersStillExecute() {
        RecordingTool toolA = new RecordingTool("tool_a", true, 10);
        RecordingTool toolB = new RecordingTool("tool_b", false, 10);
        List<ToolCallback> callbacks = adapters(toolA, toolB);

        AssistantMessage assistant = assistantWithToolCalls(
                new AssistantMessage.ToolCall("tu_A", "function", "tool_a", "{}"),
                new AssistantMessage.ToolCall("tu_B", "function", "tool_b", "{}"));

        ParallelExecuteToolsAction<AgentExecutor.State> action = new ParallelExecuteToolsAction<>(callbacks);
        Command command = action.apply(stateWith(assistant), CONFIG).join();

        // 两个工具都执行（B 不受 A 失败影响），结果按序回收
        assertEquals(1, toolA.records().size(), "tu_A 已执行");
        assertEquals(1, toolB.records().size(), "tu_B 仍执行（不受 tu_A 失败影响）");
        assertEquals(List.of("tu_A", "tu_B"), responseIds(command), "保序回收");

        // tu_A 以结构化 error 表达（响应含失败信息，而非抛异常中断）
        ToolResponseMessage message = toolResponseMessage(command);
        String tuAResponse = findResponse(message, "tu_A");
        assertTrue(tuAResponse.contains("ERROR"), "tu_A 失败以结构化 error 表达");
        assertFalse(command.update().isEmpty(), "Command 含工具结果回灌");
    }

    private List<ToolCallback> adapters(Tool... tools) {
        ToolRegistry registry = new ToolRegistry(List.of(tools));
        ToolInvoker invoker = new ToolInvoker(registry, new ToolInputValidator());
        List<ToolCallback> callbacks = new ArrayList<>();
        for (Tool tool : tools) {
            callbacks.add(new ToolCallbackAdapter(tool, invoker));
        }
        return callbacks;
    }

    private static AgentExecutor.State stateWith(org.springframework.ai.chat.messages.Message message) {
        return new AgentExecutor.State(Map.of(MessagesState.MESSAGES_STATE, List.of(message)));
    }

    private static AssistantMessage assistantWithToolCalls(AssistantMessage.ToolCall... calls) {
        return AssistantMessage.builder().toolCalls(List.of(calls)).build();
    }

    private static List<String> responseIds(Command command) {
        return toolResponseMessage(command).getResponses().stream()
                .map(ToolResponseMessage.ToolResponse::id)
                .toList();
    }

    private static ToolResponseMessage toolResponseMessage(Command command) {
        Object value = command.update().get(MessagesState.MESSAGES_STATE);
        return (ToolResponseMessage) value;
    }

    private static String findResponse(ToolResponseMessage message, String id) {
        return message.getResponses().stream()
                .filter(r -> id.equals(r.id()))
                .map(ToolResponseMessage.ToolResponse::responseData)
                .findFirst().orElse("");
    }

    private static boolean overlap(RecordingTool.Record a, RecordingTool.Record b) {
        long maxStart = Math.max(a.startNanos(), b.startNanos());
        long minEnd = Math.min(a.endNanos(), b.endNanos());
        return maxStart < minEnd;
    }
}
