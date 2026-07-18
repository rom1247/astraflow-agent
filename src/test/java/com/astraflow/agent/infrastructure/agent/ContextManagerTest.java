package com.astraflow.agent.infrastructure.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ContextManager} 上下文截断单元测试（context-truncation spec，design D6）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class ContextManagerTest {

    @Test
    @DisplayName("未超预算 - 完整保留不丢弃")
    void testTruncate_whenUnderBudget_keepsAll() {
        List<Message> messages = List.of(new UserMessage("你好"), new AssistantMessage("在的"));

        List<Message> result = ContextManager.truncate(messages, "你是助手", 10_000, 4);

        assertEquals(3, result.size(), "systemPrompt + 2 条历史全部保留");
        assertInstanceOf(SystemMessage.class, result.get(0));
        assertInstanceOf(UserMessage.class, result.get(1));
        assertInstanceOf(AssistantMessage.class, result.get(2));
    }

    @Test
    @DisplayName("超预算 - 截断早期历史并保留本轮用户消息")
    void testTruncate_whenOverBudget_dropsEarlyAndKeepsCurrent() {
        Message early = new UserMessage("x".repeat(50));
        Message middle = new UserMessage("y".repeat(50));
        Message current = new UserMessage("本轮消息");

        List<Message> result = ContextManager.truncate(List.of(early, middle, current), null, 60, 1);

        assertTrue(result.contains(current), "本轮用户消息始终保留");
        assertFalse(result.contains(early), "早期历史被截断");
        // 截断后估算 ≤ 预算（保留至少 keepRounds 单元）
        assertTrue(ContextManager.estimateTokens(result) <= 60 + 1,
                "截断后总估算回落到预算附近");
    }

    @Test
    @DisplayName("配对完整 - assistant(toolCalls) 与其 tool_result 同留或同删")
    void testTruncate_whenUnitSpansToolPair_neverSplits() {
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall("tu1", "function", "calculator", "{}");
        AssistantMessage assistantWithTools = AssistantMessage.builder().toolCalls(List.of(call)).build();
        ToolResponseMessage toolResult = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("tu1", "calculator", "42")))
                .build();
        // A+TR 需被整体丢弃（预算极小，仅够本轮用户消息）
        Message current = new UserMessage("本轮消息");
        List<Message> messages = List.of(assistantWithTools, toolResult, current);

        List<Message> result = ContextManager.truncate(messages, null, 5, 1);

        boolean keepsPair = result.contains(assistantWithTools) && result.contains(toolResult);
        boolean dropsPair = !result.contains(assistantWithTools) && !result.contains(toolResult);
        assertTrue(keepsPair || dropsPair, "assistant(toolCalls) 与 tool_result 要么同留要么同删，无孤儿");
        assertTrue(result.contains(current), "本轮用户消息保留");
    }

    @Test
    @DisplayName("配对完整 - 截断保留同侧时不拆散 assistant+tool_result")
    void testTruncate_keepsPairedUnitTogetherWhenRetained() {
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall("tu1", "function", "calculator", "{}");
        AssistantMessage assistantWithTools = AssistantMessage.builder().toolCalls(List.of(call)).build();
        ToolResponseMessage toolResult = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("tu1", "calculator", "42")))
                .build();
        // 早期长消息触发截断，但保留侧含完整 assistant+tool_result
        List<Message> messages = List.of(
                new UserMessage("z".repeat(60)),
                assistantWithTools,
                toolResult,
                new UserMessage("本轮"));

        List<Message> result = ContextManager.truncate(messages, null, 30, 1);

        assertTrue(result.contains(assistantWithTools), "保留侧含 assistant(toolCalls)");
        assertTrue(result.contains(toolResult), "保留侧同时含其 tool_result");
        assertFalse(result.contains(messages.get(0)), "早期长消息被截断");
    }

    @Test
    @DisplayName("保序 - 截断后剩余消息相对顺序与原序一致")
    void testTruncate_preservesRelativeOrder() {
        Message a = new UserMessage("a".repeat(40));
        Message b = new UserMessage("bb");
        Message c = new UserMessage("c".repeat(40));

        List<Message> result = ContextManager.truncate(List.of(a, b, c), null, 50, 1);

        // 剩余消息须保持原相对顺序：c 在 b 之后等
        int idxB = result.indexOf(b);
        int idxC = result.indexOf(c);
        assertTrue(idxB >= 0 && idxC >= 0, "b 与 c 均保留");
        assertTrue(idxB < idxC, "保留消息相对顺序与原序一致");
    }

    @Test
    @DisplayName("systemPrompt - 始终位于首位且不被截断")
    void testTruncate_systemPromptAlwaysFirst() {
        Message longHistory = new UserMessage("h".repeat(200));

        List<Message> result = ContextManager.truncate(List.of(longHistory), "你是助手", 50, 1);

        assertEquals("你是助手", result.get(0).getText(), "首位为 systemPrompt");
        assertInstanceOf(SystemMessage.class, result.get(0));
    }

    @Test
    @DisplayName("token 估算 - 超长消息估算值显著大于短消息，且非负")
    void testEstimateTokens_monotonicAndNonNegative() {
        Message longMsg = new UserMessage("a".repeat(100));
        Message shortMsg = new UserMessage("ab");

        assertTrue(ContextManager.estimateTokens(longMsg) > ContextManager.estimateTokens(shortMsg),
                "超长消息估算值显著大于短消息");
        assertTrue(ContextManager.estimateTokens(shortMsg) >= 0, "估算值非负");
        assertEquals(0, ContextManager.estimateTokens((String) null), "null 文本估算为 0");
    }

    private boolean endsWithCurrent(List<Message> result) {
        return !result.isEmpty() && result.get(result.size() - 1) instanceof UserMessage;
    }
}
