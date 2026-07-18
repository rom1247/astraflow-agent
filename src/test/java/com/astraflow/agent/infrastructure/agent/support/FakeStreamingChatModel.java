package com.astraflow.agent.infrastructure.agent.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 可编程的流式 {@link ChatModel} 桩（add-agent-loop 单测基建）。
 *
 * <p>供 {@code AgentEngine} / {@code ParallelExecuteToolsAction} / {@code AgentEventMapper} 测试驱动：
 * 按脚本顺序产出 token 序列、工具调用（带 toolUseId）或抛出指定异常。脚本以「每次 {@code stream(Prompt)}
 * 消费一条」推进，故多轮 ReAct 循环可编排为多条脚本项。所有 {@code stream(Prompt)} 收到的 prompt 记录入
 * {@link #receivedPrompts()}，供「历史消息被纳入本轮上下文」断言。
 *
 * <p>正确性由后续 engine/action/mapper 用例间接验证（task 1.3 无独立红）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public class FakeStreamingChatModel implements ChatModel {

    /** Spring AI 工具调用类型固定值（避免散落魔法字符串）。 */
    private static final String TOOL_CALL_TYPE = "function";

    private final Queue<Response> script = new LinkedList<>();

    private final List<Prompt> receivedPrompts = new CopyOnWriteArrayList<>();

    private ChatOptions options;

    /** 无状态响应器（设置后每次 stream 独立计算，供并发隔离测试，不依赖脚本队列）。 */
    private Function<Prompt, Flux<ChatResponse>> responder;

    private FakeStreamingChatModel() {
    }

    /**
     * 创建空脚本的桩模型，随后以 {@code streamTokens/requestTool(s)/throwError} 链式编排。
     *
     * @return 桩模型
     */
    public static FakeStreamingChatModel create() {
        return new FakeStreamingChatModel();
    }

    /**
     * 下一次 {@code stream} 产出给定 token 序列（纯文本，无工具调用）。
     *
     * @param tokens token 序列（每个 token 一个流式 chunk）
     * @return 本桩（链式）
     */
    public FakeStreamingChatModel streamTokens(String... tokens) {
        script.add(new TokenResponse(List.of(tokens)));
        return this;
    }

    /**
     * 下一次 {@code stream} 请求单个工具调用。
     *
     * @param toolUseId 工具调用 ID（{@code tool_use_id}）
     * @param toolName  工具名
     * @param arguments 入参 JSON 字符串
     * @return 本桩（链式）
     */
    public FakeStreamingChatModel requestTool(String toolUseId, String toolName, String arguments) {
        script.add(new ToolResponse(List.of(new ToolCallSpec(toolUseId, toolName, arguments))));
        return this;
    }

    /**
     * 下一次 {@code stream} 在同一轮请求多个工具调用（按给定顺序保序产出）。
     *
     * @param calls 工具调用规格
     * @return 本桩（链式）
     */
    public FakeStreamingChatModel requestTools(List<ToolCallSpec> calls) {
        script.add(new ToolResponse(new ArrayList<>(calls)));
        return this;
    }

    /**
     * 下一次 {@code stream} 抛出指定异常（如 {@code LlmException}）。
     *
     * @param throwable 待抛异常
     * @return 本桩（链式）
     */
    public FakeStreamingChatModel throwError(Throwable throwable) {
        script.add(new ErrorResponse(throwable));
        return this;
    }

    /**
     * 设置 {@link #getOptions()} 返回值（供 {@code AgentChatService} 判定 {@code ToolCallingChatOptions} 分支）。
     *
     * @param options 模型选项
     * @return 本桩（链式）
     */
    public FakeStreamingChatModel withOptions(ChatOptions options) {
        this.options = options;
        return this;
    }

    /**
     * 切换为「回显最后一条用户消息」的无状态模式：每次 {@code stream} 独立从 prompt 取最后一条用户消息文本回显，
     * 不消费脚本队列，故多会话并发无串扰（供 agent-loop「多会话并发互不串扰」测试）。
     *
     * @return 本桩（链式）
     */
    public FakeStreamingChatModel echoLastUserMessage() {
        this.responder = prompt -> {
            String text = prompt.getInstructions().stream()
                    .filter(message -> message instanceof UserMessage)
                    .map(Message::getText)
                    .reduce((first, second) -> second)
                    .orElse("");
            return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
        };
        return this;
    }

    /**
     * @return 所有 {@code stream(Prompt)} 收到的 prompt（按到达顺序），供历史纳入断言
     */
    public List<Prompt> receivedPrompts() {
        return receivedPrompts;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        receivedPrompts.add(prompt);
        if (responder != null) {
            return responder.apply(prompt);
        }
        Response next = script.poll();
        if (next == null) {
            return Flux.error(new IllegalStateException("FakeStreamingChatModel 脚本已耗尽：stream 调用多于编排项"));
        }
        return next.toFlux();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // 图主循环走 stream；call 仅取脚本首项聚合返回，供非流式便利场景。
        receivedPrompts.add(prompt);
        Response next = script.poll();
        if (next == null) {
            throw new IllegalStateException("FakeStreamingChatModel 脚本已耗尽：call 调用多于编排项");
        }
        return next.aggregate();
    }

    @Override
    public ChatOptions getOptions() {
        return options;
    }

    /** 单个工具调用规格（id + name + arguments）。 */
    public record ToolCallSpec(String toolUseId, String toolName, String arguments) {
    }

    /** 脚本项：token 序列 / 工具调用 / 异常。 */
    private sealed interface Response permits TokenResponse, ToolResponse, ErrorResponse {
        Flux<ChatResponse> toFlux();

        ChatResponse aggregate();
    }

    /** 产出纯文本 token 序列。 */
    private record TokenResponse(List<String> tokens) implements Response {
        @Override
        public Flux<ChatResponse> toFlux() {
            return Flux.fromIterable(tokens)
                    .map(token -> new ChatResponse(List.of(new Generation(new AssistantMessage(token)))));
        }

        @Override
        public ChatResponse aggregate() {
            String full = String.join("", tokens);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(full))));
        }
    }

    /** 产出工具调用（同一轮可含多个，按序保序）。 */
    private record ToolResponse(List<ToolCallSpec> calls) implements Response {
        @Override
        public Flux<ChatResponse> toFlux() {
            return Flux.just(toChatResponse());
        }

        @Override
        public ChatResponse aggregate() {
            return toChatResponse();
        }

        private ChatResponse toChatResponse() {
            List<AssistantMessage.ToolCall> toolCalls = calls.stream()
                    .map(spec -> new AssistantMessage.ToolCall(
                            spec.toolUseId(), TOOL_CALL_TYPE, spec.toolName(), spec.arguments()))
                    .toList();
            AssistantMessage assistant = AssistantMessage.builder()
                    .toolCalls(toolCalls)
                    .build();
            return new ChatResponse(List.of(new Generation(assistant)));
        }
    }

    /** 抛出指定异常。 */
    private record ErrorResponse(Throwable throwable) implements Response {
        @Override
        public Flux<ChatResponse> toFlux() {
            return Flux.error(throwable);
        }

        @Override
        public ChatResponse aggregate() {
            throw new IllegalStateException("ErrorResponse 不支持聚合（call）", throwable);
        }
    }
}
