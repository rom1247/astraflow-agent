package com.astraflow.agent.infrastructure.agent;

import com.astraflow.agent.domain.port.ModerationService;
import com.astraflow.agent.domain.tool.Tool;
import com.astraflow.agent.domain.tool.ToolInvoker;
import com.astraflow.agent.infrastructure.config.AgentProperties;
import com.astraflow.agent.infrastructure.tool.ToolCallbackAdapter;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.agent.Agent;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.agent.CallModelAction;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.spring.ai.serializer.jackson.SpringAIJacksonStateSerializer;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 路径 C 单例图装配（agent-loop spec，design D1，复刻 spike {@code AgentBuilderGraphs.pathC}）。
 *
 * <p>用底层 {@link Agent.Builder} 装配 ReAct 拓扑（{@code START→agent→action→agent|end}，{@code streaming=true}）：
 * 复用预置 {@link CallModelAction}（由自建 {@link AgentChatService} 喂，注入工具回调），
 * action 节点换成生产版 {@link ParallelExecuteToolsAction}（并行），挂 callModelHook（{@link MaxTurnsGuard}）
 * 与 executeToolsHook（{@link ModerationHooks}）。产出<strong>单例 {@code @Bean CompiledGraph}</strong>
 * （拓扑编译一次、跨请求复用，V10 并发安全 GREEN）；<strong>不挂 {@code CheckpointSaver}</strong>（S1，
 * 崩溃恢复由 #6 图驱动，非 checkpoint）。
 *
 * <p>每请求不重建图，只重建初始 messages state（由 {@link AgentEngine} 负责）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentGraphFactory {

    /**
     * 审核挂载点装配单例。
     *
     * @param moderationService 审核端口（默认 NoOpModerationService）
     * @return ModerationHooks
     */
    @Bean
    public ModerationHooks moderationHooks(ModerationService moderationService) {
        return new ModerationHooks(moderationService);
    }

    /**
     * 单例编译图：从领域 {@link Tool} 经 {@link ToolCallbackAdapter} 构建工具回调，装配路径 C 图并编译一次。
     *
     * <p>最大轮次守卫以 langgraph4j 原生递归限制承载（compile 期 {@code recursionLimit = maxTurns * 2}，
     * 每 ReAct 轮次 = agent + action 两步）：超限图抛 {@code GraphRunnerException}，由 {@link AgentEngine}
     * 映射为 {@code DONE(error_max_turns)}（design D8/Open Q1 优先方案）。
     *
     * @param chatModel       经韧性装饰的 ChatModel（@Primary ResilientChatModel）
     * @param tools           已注册的领域工具 bean
     * @param toolInvoker     工具调用编排器（适配器执行经其校验）
     * @param moderationHooks 审核挂载点（提供 executeToolsHook）
     * @param properties      Agent 运行时参数（最大轮次）
     * @return 单例 CompiledGraph
     * @throws GraphStateException 图构建失败
     */
    @Bean
    public CompiledGraph<AgentExecutor.State> agentCompiledGraph(
            ChatModel chatModel,
            List<Tool> tools,
            ToolInvoker toolInvoker,
            ModerationHooks moderationHooks,
            AgentProperties properties) throws GraphStateException {
        List<ToolCallback> callbacks = tools.stream()
                .map(tool -> (ToolCallback) new ToolCallbackAdapter(tool, toolInvoker))
                .toList();
        AgentChatService chatService = new AgentChatService(chatModel, callbacks);
        return build(chatService, callbacks, null, moderationHooks.executeToolsHook(),
                maxRecursionLimit(properties.getMaxTurns()));
    }

    /**
     * 每轮次（agent + action）两步，递归上限取 {@code maxTurns * 2}（保留下限 1）。
     *
     * @param maxTurns 最大轮次
     * @return 递归步数上限
     */
    static int maxRecursionLimit(int maxTurns) {
        return Math.max(1, maxTurns) * 2;
    }

    /**
     * 路径 C 装配并编译（可独立于 Spring 构造，供测试驱动）。
     *
     * @param chatService      自建 ChatService（注入工具回调）
     * @param toolCallbacks    工具回调（喂并行 action）
     * @param callModelHook    包裹 agent 节点的 hook（可空——本 change 不挂载，预留审核 ①②）
     * @param executeToolsHook 包裹 action 节点的 hook（审核 ③④）
     * @param recursionLimit   递归步数上限（最大轮次守卫，0 表示用框架默认）
     * @return 编译后的 ReAct 图
     * @throws GraphStateException 图构建失败
     */
    public static CompiledGraph<AgentExecutor.State> build(
            AgentChatService chatService,
            List<ToolCallback> toolCallbacks,
            NodeHook.WrapCall<AgentExecutor.State> callModelHook,
            EdgeHook.WrapCall<AgentExecutor.State> executeToolsHook,
            int recursionLimit) throws GraphStateException {
        // AgentExecutor.builder() 仅用于配置 chatModel/streaming 并喂给预置 CallModelAction（非用其 build）
        var builder = org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor.builder()
                .chatModel(chatService.chatModel())
                .streaming(true)
                .emitStreamingEnd(true);
        CallModelAction<AgentExecutor.State> callModelAction =
                new CallModelAction<>(chatService.asChatServiceFactory(), builder);
        ParallelExecuteToolsAction<AgentExecutor.State> executeToolsAction =
                new ParallelExecuteToolsAction<>(toolCallbacks);
        var graphBuilder = Agent.<Message, AgentExecutor.State>builder()
                .schema(MessagesState.SCHEMA)
                .stateSerializer(new SpringAIJacksonStateSerializer<>(AgentExecutor.State::new))
                .callModelAction(callModelAction)
                .executeToolsAction(executeToolsAction);
        if (callModelHook != null) {
            graphBuilder.addCallModelHook(callModelHook);
        }
        var stateGraph = graphBuilder
                .addExecuteToolsHook(executeToolsHook)
                .build();
        var compileConfig = org.bsc.langgraph4j.CompileConfig.builder();
        if (recursionLimit > 0) {
            compileConfig.recursionLimit(recursionLimit);
        }
        return stateGraph.compile(compileConfig.build());
    }
}
