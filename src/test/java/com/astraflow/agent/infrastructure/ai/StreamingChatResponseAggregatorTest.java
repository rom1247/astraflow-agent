package com.astraflow.agent.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StreamingChatResponseAggregator} 测试（纯单测，不加载 Spring 上下文）。
 *
 * <p>覆盖 spec llm-streaming S1.1/S1.2/S2.1/S2.2：文本增量聚合、空响应、单 toolCall 跨 chunk、多 toolCall 保序。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class StreamingChatResponseAggregatorTest {

    private final StreamingChatResponseAggregator aggregator = new StreamingChatResponseAggregator();

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("多段文本增量聚合为完整文本")
    void testAggregate_concatsTextDeltas() {
        Flux<ChatResponse> chunks = Flux.just(textResponse("你好"), textResponse("，世界"));

        AggregatedChatResponse result = aggregator.aggregate(chunks).block();

        assertThat(result.text()).isEqualTo("你好，世界");
    }

    @Test
    @DisplayName("空响应（无 delta）聚合为空字符串、不抛异常")
    void testAggregate_emptyResponseYieldsEmptyText() {
        AggregatedChatResponse result = aggregator.aggregate(Flux.empty()).block();

        assertThat(result.text()).isEmpty();
        assertThat(result.toolCalls()).isEmpty();
    }

    @Test
    @DisplayName("单个 toolCall 的 arguments 跨两 chunk 分段 → 聚合得完整可解析结果")
    void testAggregate_singleToolCallAcrossChunks() throws Exception {
        Flux<ChatResponse> chunks = Flux.just(
                toolCallResponse(toolCall(null, "calc", "{\"expr\":")),
                toolCallResponse(toolCall("tu_A", "calc", "{\"expr\":\"1+2\"}"))
        );

        AggregatedChatResponse result = aggregator.aggregate(chunks).block();

        assertThat(result.toolCalls()).hasSize(1);
        AssistantMessage.ToolCall toolCall = result.toolCalls().get(0);
        assertThat(toolCall.id()).isEqualTo("tu_A");
        assertThat(toolCall.name()).isEqualTo("calc");
        // arguments 可解析为合法 JSON
        mapper.readTree(toolCall.arguments());
    }

    @Test
    @DisplayName("一轮两个 toolCall 交错 → 保序聚合为两个结构化结果")
    void testAggregate_multipleToolCallsPreserveOrder() {
        Flux<ChatResponse> chunks = Flux.just(
                toolCallResponse(toolCall("tu_A", "calc", "{\"a\":1}")),
                toolCallResponse(List.of(
                        toolCall("tu_A", "calc", "{\"a\":1}"),
                        toolCall("tu_B", "http", "{\"b\":2}")
                ))
        );

        AggregatedChatResponse result = aggregator.aggregate(chunks).block();

        assertThat(result.toolCalls()).hasSize(2);
        assertThat(result.toolCalls().get(0).id()).isEqualTo("tu_A");
        assertThat(result.toolCalls().get(1).id()).isEqualTo("tu_B");
    }

    private ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private ChatResponse toolCallResponse(AssistantMessage.ToolCall single) {
        return toolCallResponse(List.of(single));
    }

    private ChatResponse toolCallResponse(List<AssistantMessage.ToolCall> toolCalls) {
        AssistantMessage message = AssistantMessage.builder().content("").toolCalls(toolCalls).build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private AssistantMessage.ToolCall toolCall(String id, String name, String arguments) {
        return new AssistantMessage.ToolCall(id, "function", name, arguments);
    }
}
