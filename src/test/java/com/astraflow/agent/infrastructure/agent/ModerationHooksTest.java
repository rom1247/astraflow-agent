package com.astraflow.agent.infrastructure.agent;

import com.astraflow.agent.domain.model.Decision;
import com.astraflow.agent.domain.model.ModerationBlockedException;
import com.astraflow.agent.domain.port.ModerationService;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.agent.Agent;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModerationHooks} 单元测试（moderation-hook spec「默认 PASS 不影响执行 / BLOCK 终止轮次」）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class ModerationHooksTest {

    private static final RunnableConfig CONFIG = RunnableConfig.builder().threadId("moderation-test").build();

    @Test
    @DisplayName("默认 PASS - 不拦截，工具执行节点正常执行")
    void testExecuteToolsHook_defaultPass_executesAction() {
        ModerationHooks hooks = new ModerationHooks(new NoOpModerationService());
        AtomicBoolean actionCalled = new AtomicBoolean();
        AsyncCommandAction<AgentExecutor.State> action = recordingAction(actionCalled);

        hooks.executeToolsHook().applyWrap("action", stateWithToolCall(), CONFIG, action);

        assertTrue(actionCalled.get(), "默认 PASS 时工具执行节点被调用");
    }

    @Test
    @DisplayName("工具前审核 BLOCK - 抛 ModerationBlockedException 且不执行工具")
    void testExecuteToolsHook_blockInput_throwsAndSkipsAction() {
        ModerationService blocking = (content, node) -> Decision.BLOCK;
        ModerationHooks hooks = new ModerationHooks(blocking);
        AtomicBoolean actionCalled = new AtomicBoolean();
        AsyncCommandAction<AgentExecutor.State> action = recordingAction(actionCalled);

        assertThrows(ModerationBlockedException.class,
                () -> hooks.executeToolsHook().applyWrap("action", stateWithToolCall(), CONFIG, action),
                "BLOCK → 抛 ModerationBlockedException");
        assertFalse(actionCalled.get(), "工具未被执行");
    }

    private static AsyncCommandAction<AgentExecutor.State> recordingAction(AtomicBoolean called) {
        return (state, config) -> {
            called.set(true);
            return CompletableFuture.completedFuture(new Command(Agent.END_LABEL));
        };
    }

    private static AgentExecutor.State stateWithToolCall() {
        AssistantMessage assistant = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("tu1", "function", "calculator", "{\"expression\":\"1+1\"}")))
                .build();
        return new AgentExecutor.State(Map.of(MessagesState.MESSAGES_STATE, List.of(assistant)));
    }
}
