package com.astraflow.agent.infrastructure.agent;

import com.astraflow.agent.domain.model.AgentEvent;
import com.astraflow.agent.domain.model.DoneSubtype;
import com.astraflow.agent.domain.model.EventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.UUID;

/**
 * 将 LangGraph4j 的 {@code AsyncGenerator<NodeOutput>} 映射为 {@code Flux<AgentEvent>}（design D3，复刻 spike V5）。
 *
 * <p>映射规则（已据 1.8.20 API 落实，应用 V2 workaround）：
 * <ul>
 *   <li>{@link EventType#ASSISTANT_TEXT} ← {@link StreamingOutput#chunk()}（流式 token；中途 state 为 null）</li>
 *   <li>{@link EventType#TOOL_USE} ← agent 节点完成后 {@code state.lastMessage()} 的
 *       {@link AssistantMessage#getToolCalls()}（流式 chunk 结构上无 tool_use_id，节点关闭后取——V2 workaround）</li>
 *   <li>{@link EventType#TOOL_RESULT} ← action 节点的 {@link ToolResponseMessage}</li>
 *   <li>{@link EventType#DONE} ← {@link NodeOutput#isEND()}</li>
 * </ul>
 *
 * <p>生命周期事件 {@code SESSION_START} / {@code TURN_END} 与带 subtype 的 {@code DONE} / {@code ERROR}
 * 由 {@code AgentEngine} 层产出（非图节点）；本映射器仅产出图节点衍生事件 + 完成信号。
 *
 * <p>{@code seq} 暂置 0 占位——{@code seq}/{@code eventId} 由 #6 的 {@code AgentEventRepository.append} 落盘时分配。
 * {@code eventJson} 用 Jackson 2.x（自建 {@link ObjectMapper}，禁注入——见 domain-model.md 红线）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public final class AgentEventMapper {

    /** Jackson 2.x 序列化器（自建，禁注入——见 domain-model.md 红线）。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** seq 占位值，由 #6 落盘时分配真实序号。 */
    private static final long SEQ_PLACEHOLDER = 0L;

    private AgentEventMapper() {
    }

    /**
     * 把图的流式输出转换为 {@code Flux<AgentEvent>}。
     *
     * @param gen       图的 {@code stream(...)} 产物
     * @param sessionId 所属会话 ID
     * @param turnId    轮次 ID
     * @return 类型化事件流
     */
    public static Flux<AgentEvent> toFlux(AsyncGenerator<? extends NodeOutput<AgentExecutor.State>> gen,
                                          UUID sessionId, String turnId) {
        // 单参 create 默认 BUFFER 背压（不丢事件）；next() 阻塞拉取故在虚拟线程上迭代
        return Flux.<AgentEvent>create(sink -> Thread.startVirtualThread(() -> {
            try {
                for (NodeOutput<AgentExecutor.State> output : gen) {
                    if (output instanceof StreamingOutput streaming && streaming.isStreamingEnd()) {
                        // StreamingOutputEnd 终态哨兵：turn 边界，DONE 由 isEND() 统一发，避免重复
                    } else if (output instanceof StreamingOutput streaming) {
                        String chunk = streaming.chunk();
                        if (chunk != null && !chunk.isEmpty()) {
                            sink.next(event(sessionId, turnId, EventType.ASSISTANT_TEXT, assistantTextPayload(chunk)));
                        }
                    } else if (output.state() != null) {
                        output.state().lastMessage().ifPresent(message -> emitNodeMessage(sink, message, sessionId, turnId));
                    }
                    if (output.isEND()) {
                        sink.next(event(sessionId, turnId, EventType.DONE, donePayload(DoneSubtype.SUCCESS)));
                    }
                }
                sink.complete();
            } catch (Throwable throwable) {
                sink.error(throwable);
            }
        }));
    }

    /**
     * 按节点完成后最后一条消息产出 TOOL_USE / TOOL_RESULT（纯文本最终答案已由流式 chunk 发出，此处不重复）。
     */
    private static void emitNodeMessage(FluxSink<AgentEvent> sink, Message message, UUID sessionId, String turnId) {
        if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
            for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls()) {
                sink.next(event(sessionId, turnId, EventType.TOOL_USE, toolUsePayload(toolCall)));
            }
        } else if (message instanceof ToolResponseMessage toolResponse) {
            for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
                sink.next(event(sessionId, turnId, EventType.TOOL_RESULT, toolResultPayload(response)));
            }
        }
    }

    private static AgentEvent event(UUID sessionId, String turnId, EventType type, JsonNode payload) {
        return AgentEvent.create(sessionId, SEQ_PLACEHOLDER, turnId, type, payload);
    }

    private static JsonNode assistantTextPayload(String delta) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("delta", delta);
        return node;
    }

    private static JsonNode toolUsePayload(AssistantMessage.ToolCall toolCall) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("toolUseId", toolCall.id());
        node.put("tool", toolCall.name());
        node.put("inputJson", toolCall.arguments());
        return node;
    }

    private static JsonNode toolResultPayload(ToolResponseMessage.ToolResponse response) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("toolUseId", response.id());
        node.put("tool", response.name());
        node.put("preview", response.responseData());
        return node;
    }

    private static JsonNode donePayload(DoneSubtype subtype) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("subtype", subtype.getValue());
        return node;
    }
}
