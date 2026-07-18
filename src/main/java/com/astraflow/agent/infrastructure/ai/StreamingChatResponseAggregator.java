package com.astraflow.agent.infrastructure.ai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流式片段聚合工具（design D5）。
 *
 * <p>把 {@code Flux<ChatResponse>} 聚合为 {@link AggregatedChatResponse}（完整文本 + 结构化 toolCalls）：
 * <ul>
 *   <li><b>文本</b>：逐 chunk 取 {@code getResult().getOutput().getText()} 拼接，null 跳过（空响应得空串，不抛异常）</li>
 *   <li><b>toolCalls</b>：复刻 spike V2 workaround——中间 chunk 的 toolCalls 不可靠（id 常为 null），
 *       完整值只在流终止可得；按 List 位置累积，id 非空（终止完整）时重置累积器，id 为 null（片段）时累积 arguments</li>
 * </ul>
 *
 * <p>属 LLM 适配层（infra），操作纯 Spring AI 类型，不依赖 LangGraph4j / AgentEvent。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public class StreamingChatResponseAggregator {

    /**
     * 聚合流式响应。
     *
     * @param flux 流式 chunk 序列
     * @return 聚合结果（完整文本 + 保序 toolCalls）
     */
    public Mono<AggregatedChatResponse> aggregate(Flux<ChatResponse> flux) {
        return flux.reduceWith(AggregatorState::new, AggregatorState::accumulate)
                .map(AggregatorState::build);
    }

    /** 聚合过程的可变累积态（reduce 串行，无需并发保护）。 */
    private static final class AggregatorState {

        private final StringBuilder text = new StringBuilder();

        private final Map<Integer, ToolCallAccumulator> toolCalls = new LinkedHashMap<>();

        AggregatorState accumulate(ChatResponse chunk) {
            AssistantMessage output = extractOutput(chunk);
            if (output == null) {
                return this;
            }
            appendText(output.getText());
            if (output.hasToolCalls()) {
                List<AssistantMessage.ToolCall> calls = output.getToolCalls();
                for (int position = 0; position < calls.size(); position++) {
                    toolCalls.computeIfAbsent(position, key -> new ToolCallAccumulator())
                            .accumulate(calls.get(position));
                }
            }
            return this;
        }

        AggregatedChatResponse build() {
            List<AssistantMessage.ToolCall> calls = toolCalls.values().stream()
                    .map(ToolCallAccumulator::build)
                    .toList();
            return new AggregatedChatResponse(text.toString(), calls);
        }

        private void appendText(String chunkText) {
            if (chunkText != null) {
                text.append(chunkText);
            }
        }

        private static AssistantMessage extractOutput(ChatResponse chunk) {
            if (chunk == null) {
                return null;
            }
            Generation generation = chunk.getResult();
            return generation != null ? generation.getOutput() : null;
        }
    }

    /** 单个 toolCall 的累积器：id 非空重置（终止完整），id 为 null 累积（中间片段）。 */
    private static final class ToolCallAccumulator {

        private String id;
        private String type;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        void accumulate(AssistantMessage.ToolCall toolCall) {
            if (toolCall == null) {
                return;
            }
            if (isPresent(toolCall.id())) {
                resetWith(toolCall);
            } else {
                mergeFragment(toolCall);
            }
        }

        AssistantMessage.ToolCall build() {
            return new AssistantMessage.ToolCall(id, type, name, arguments.toString());
        }

        private void resetWith(AssistantMessage.ToolCall complete) {
            this.id = complete.id();
            this.type = complete.type();
            this.name = complete.name();
            this.arguments.setLength(0);
            if (complete.arguments() != null) {
                this.arguments.append(complete.arguments());
            }
        }

        private void mergeFragment(AssistantMessage.ToolCall fragment) {
            if (isPresent(fragment.type())) {
                this.type = fragment.type();
            }
            if (isPresent(fragment.name())) {
                this.name = fragment.name();
            }
            if (fragment.arguments() != null) {
                this.arguments.append(fragment.arguments());
            }
        }

        private static boolean isPresent(String value) {
            return value != null && !value.isBlank();
        }
    }
}
