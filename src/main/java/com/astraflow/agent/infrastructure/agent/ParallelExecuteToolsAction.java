package com.astraflow.agent.infrastructure.agent;

import com.astraflow.agent.domain.tool.ToolErrorCode;
import com.astraflow.agent.domain.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.agent.Agent;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.Objects.requireNonNull;

/**
 * 自定义并行工具执行节点（tool-invocation spec，design D4，复刻 spike V7）。
 *
 * <p>当一轮中模型请求多个工具调用时，以虚拟线程 {@code fan-out} 并发执行，按模型给出的 {@code toolUseId}
 * 原序回收结果、组装 {@link ToolResponseMessage} 回灌图。同一轮的各个工具调用互相独立——
 * 一个工具的失败（校验失败或业务错误）MUST NOT 中断同轮其它工具（spec「同轮独立性」）。
 *
 * <p>执行经 {@link ToolCallback#call(String)}，生产版的 {@link ToolCallback} 是
 * {@code ToolCallbackAdapter}——其 {@code call} 内部经 {@code ToolInvoker}，自动获得
 * 「查找 → JSON Schema 校验 → 执行」与结构化错误，故 action 代码几乎不变，适配器透明地把执行路由到领域端口。
 *
 * <p>模型本轮无工具调用时，节点直接转向图结束（{@link Agent#END_LABEL}），不执行任何工具。
 *
 * @param <State> 图状态类型，须继承 {@link MessagesState}（messages 通道 appender）
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public class ParallelExecuteToolsAction<State extends MessagesState<Message>> implements AsyncCommandAction<State> {

    /** Jackson 2.x 序列化器（自建，禁注入——见 domain-model.md 红线）。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, ToolCallback> tools;

    /**
     * @param callbacks 已注册的工具回调（按名解析 tool_call；生产版为 {@code ToolCallbackAdapter}）
     */
    public ParallelExecuteToolsAction(List<ToolCallback> callbacks) {
        requireNonNull(callbacks, "callbacks 不能为空");
        this.tools = callbacks.stream()
                .collect(Collectors.toMap(callback -> callback.getToolDefinition().name(), Function.identity()));
    }

    @Override
    public CompletableFuture<Command> apply(State state, RunnableConfig runnableConfig) {
        var lastMessage = state.lastMessage();
        if (lastMessage.isEmpty()) {
            return failedFuture(new IllegalArgumentException("缺少输入消息"));
        }
        if (!(lastMessage.get() instanceof AssistantMessage assistantMessage)) {
            return failedFuture(new IllegalArgumentException("最后一条消息须为 AssistantMessage"));
        }
        if (!assistantMessage.hasToolCalls()) {
            return completedFuture(new Command(Agent.END_LABEL));
        }

        var toolCalls = assistantMessage.getToolCalls();
        // 并行 fan-out：每个 tool_call 一个虚拟线程；保序回收（按 toolCalls 原序 = toolUseId 序）
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = toolCalls.stream()
                    .map(toolCall -> executor.submit(() -> new ToolResponseMessage.ToolResponse(
                            toolCall.id(), toolCall.name(), invokeTool(toolCall))))
                    .toList();
            for (var future : futures) {
                responses.add(future.get());
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return failedFuture(interruptedException);
        } catch (Throwable throwable) {
            return failedFuture(throwable);
        }

        ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                .responses(responses)
                .build();
        return completedFuture(new Command(Agent.AGENT_LABEL,
                Map.of(MessagesState.MESSAGES_STATE, toolResponseMessage)));
    }

    /**
     * 按名查找工具回调并执行（经 {@code ToolCallbackAdapter} → {@code ToolInvoker}，获得校验 + 结构化错误）。
     *
     * @param toolCall 工具调用
     * @return 序列化后的工具响应数据
     */
    private String invokeTool(AssistantMessage.ToolCall toolCall) {
        ToolCallback callback = tools.get(toolCall.name());
        if (callback == null) {
            return serialize(ToolResult.error(ToolErrorCode.UNKNOWN_TOOL, "未注册工具: " + toolCall.name()));
        }
        return callback.call(toolCall.arguments());
    }

    private String serialize(ToolResult result) {
        try {
            return MAPPER.writeValueAsString(result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException serializeError) {
            return "{}";
        }
    }
}
