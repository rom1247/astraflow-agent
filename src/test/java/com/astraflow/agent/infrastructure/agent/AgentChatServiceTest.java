package com.astraflow.agent.infrastructure.agent;

import com.astraflow.agent.domain.tool.ToolInputValidator;
import com.astraflow.agent.domain.tool.ToolInvoker;
import com.astraflow.agent.domain.tool.ToolRegistry;
import com.astraflow.agent.domain.tool.builtin.CalculatorTool;
import com.astraflow.agent.infrastructure.agent.support.FakeStreamingChatModel;
import com.astraflow.agent.infrastructure.tool.ToolCallbackAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link AgentChatService} 单元测试（design D2，V9b「工具注入」）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class AgentChatServiceTest {

    @Test
    @DisplayName("chatOptions - 经 mutate().toolCallbacks 注入领域工具回调")
    void testChatOptions_injectsDomainToolCallbacks() {
        FakeStreamingChatModel model = FakeStreamingChatModel.create()
                .withOptions(ToolCallingChatOptions.builder().build());
        ToolCallback adapter = new ToolCallbackAdapter(
                new CalculatorTool(),
                new ToolInvoker(new ToolRegistry(List.of(new CalculatorTool())), new ToolInputValidator()));

        AgentChatService service = new AgentChatService(model, List.of(adapter));

        ChatOptions options = service.chatOptions().orElseThrow(() -> new AssertionError("须提供 chatOptions"));
        assertInstanceOf(ToolCallingChatOptions.class, options, "选项为 ToolCallingChatOptions");
        List<ToolCallback> callbacks = ((ToolCallingChatOptions) options).getToolCallbacks();
        assertEquals(1, callbacks.size(), "注入一个工具回调");
        assertEquals("calculator", callbacks.get(0).getToolDefinition().name(), "工具名源于领域工具");
    }

    @Test
    @DisplayName("chatModel - 暴露注入的模型")
    void testChatModel_returnsInjectedModel() {
        FakeStreamingChatModel model = FakeStreamingChatModel.create();
        AgentChatService service = new AgentChatService(model, List.of());

        assertSame(model, service.chatModel());
    }
}
