package com.astraflow.agent.infrastructure.agent;

import com.astraflow.agent.domain.model.AgentEvent;
import com.astraflow.agent.domain.model.EventType;
import com.astraflow.agent.infrastructure.agent.support.FakeStreamingChatModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentGraphFactory} 路径 C 单例图装配单元测试（agent-loop spec「单例编译图」，design D1）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class AgentGraphFactoryTest {

    @Test
    @DisplayName("pathC 装配产出可 stream 的图，stub 模型流式产出经 mapper 得事件")
    void testBuild_assembledGraphStreamsEvents() throws Exception {
        FakeStreamingChatModel model = FakeStreamingChatModel.create()
                .withOptions(ToolCallingChatOptions.builder().build())
                .streamTokens("你", "好");
        AgentChatService chatService = new AgentChatService(model, List.of());
        ModerationHooks hooks = new ModerationHooks(new NoOpModerationService());

        CompiledGraph<AgentExecutor.State> graph = AgentGraphFactory.build(
                chatService, List.of(), null, hooks.executeToolsHook(), 0);

        RunnableConfig config = RunnableConfig.builder().threadId("graph-factory-test").build();
        var generator = graph.stream(
                Map.of(MessagesState.MESSAGES_STATE, List.of(new UserMessage("在吗"))), config);
        List<AgentEvent> events = AgentEventMapper.toFlux(generator, UUID.randomUUID(), "turn-1")
                .collectList().block();

        assertTrue(events != null && events.stream()
                .anyMatch(event -> event.getEventType() == EventType.ASSISTANT_TEXT),
                "装配的图可流式产出 ASSISTANT_TEXT 事件");
    }
}
