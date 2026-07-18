package com.astraflow.agent.infrastructure.agent;

import org.bsc.langgraph4j.spring.ai.agent.ReactAgent;
import org.bsc.langgraph4j.spring.ai.agent.ReactAgentBuilder;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Optional;

/**
 * 自建 {@link ReactAgent.ChatService}，把经 {@code ToolCallbackAdapter} 转换的工具回调注入模型（design D2，V9b）。
 *
 * <p>预置 {@code CallModelAction} 需要一个 {@link ReactAgent.ChatService} 驱动 {@link ChatModel} 并把工具注入模型；
 * langgraph4j 的 {@code DefaultChatService} 包级私有、跨包不可见，故须自建本类。
 *
 * <p>工具注入：当模型选项为 {@link ToolCallingChatOptions} 时，经 {@code mutate().toolCallbacks(...)} 把工具回调
 * 挂入选项，使模型工具清单源于领域 {@code ToolRegistry}（经适配器）。工具执行由图的 action 节点
 * （{@code ParallelExecuteToolsAction}）驱动——Spring AI 2.0 已无 {@code internalToolExecutionEnabled} 选项
 * （V9b 验证：不设置该标志时图节点正确驱动执行，1.8.20 + Spring AI 2.0 端到端 GREEN）。
 *
 * <p>{@code chatModel} 即 #3 的 {@code @Primary ResilientChatModel}，透明获得重试 / 熔断韧性。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public class AgentChatService implements ReactAgent.ChatService {

    private final ChatModel chatModel;

    private final ChatOptions chatOptionsWithTools;

    /**
     * 构造 ChatService。
     *
     * @param chatModel     经韧性装饰的 ChatModel（生产为 ResilientChatModel）
     * @param toolCallbacks 经 ToolCallbackAdapter 转换的工具回调
     */
    public AgentChatService(ChatModel chatModel, List<ToolCallback> toolCallbacks) {
        this.chatModel = chatModel;
        this.chatOptionsWithTools = buildOptionsWithTools(chatModel, toolCallbacks);
    }

    @Override
    public ChatModel chatModel() {
        return chatModel;
    }

    @Override
    public Optional<ChatOptions> chatOptions() {
        return Optional.ofNullable(chatOptionsWithTools);
    }

    /**
     * 提供喂给预置 {@code CallModelAction} 的 ChatService 工厂（{@code builder -> this}）。
     *
     * @return ChatService 工厂函数
     */
    public java.util.function.Function<ReactAgentBuilder<?, ?>, ReactAgent.ChatService> asChatServiceFactory() {
        return builder -> this;
    }

    private static ChatOptions buildOptionsWithTools(ChatModel chatModel, List<ToolCallback> toolCallbacks) {
        ChatOptions baseOptions = chatModel.getOptions();
        if (baseOptions instanceof ToolCallingChatOptions toolCallingOptions) {
            return toolCallingOptions.mutate().toolCallbacks(toolCallbacks).build();
        }
        // 模型选项非 ToolCallingChatOptions 时无工具注入（chatOptions() 返回 empty）
        return null;
    }
}
