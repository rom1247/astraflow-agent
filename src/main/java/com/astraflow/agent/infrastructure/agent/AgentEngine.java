package com.astraflow.agent.infrastructure.agent;

import com.astraflow.agent.domain.model.AgentEvent;
import com.astraflow.agent.domain.model.DoneSubtype;
import com.astraflow.agent.domain.model.EventType;
import com.astraflow.agent.domain.model.LlmException;
import com.astraflow.agent.domain.model.ModerationBlockedException;
import com.astraflow.agent.domain.tool.ToolRegistry;
import com.astraflow.agent.infrastructure.config.AgentProperties;
import com.astraflow.agent.infrastructure.config.ChatProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AgentLoop 运行时入口（agent-loop spec，design D3/D8）。
 *
 * <p>{@code run(sessionId, history, userText)} 基于单例编译图驱动一次会话轮次，产出严格有序的 {@link AgentEvent} 流：
 * <ol>
 *   <li>前置发恰好一个 {@link EventType#SESSION_START}（携带 model 与可用工具清单）</li>
 *   <li>包 {@link AgentEventMapper#toFlux} 产出的图节点事件（ASSISTANT_TEXT / TOOL_USE / TOOL_RESULT）</li>
 *   <li>正常收尾补 {@link EventType#TURN_END}（携带 turnId）+ {@link EventType#DONE}（携带 subtype）</li>
 *   <li>异常经 {@code onErrorResume} 映射为 {@code DONE(subtype)}（LlmException → error_during_execution、
 *       MaxTurnsExceededException → error_max_turns、ModerationBlockedException → error_blocked_tool），
 *       不向订阅者传播未封装原始异常</li>
 * </ol>
 *
 * <p>每请求重建独立 messages state（历史 + 本轮用户消息，经 {@link ContextManager} 截断兜底），以 {@code sessionId}
 * 为 {@code threadId} 驱动 {@code graph.stream}。
 *
 * <p><b>最大轮次守卫</b>（design D8/Open Q1 落地）：以 langgraph4j 原生递归限制承载（{@link AgentGraphFactory}
 * compile 期 {@code recursionLimit = maxTurns * 2}）。超限图抛 {@link GraphRunnerException}，本 engine
 * 经 {@code mapSubtype} 映射为 {@code DONE(error_max_turns)}。<em>为何不用 hook 计数</em>：apply 验证发现
 * langgraph4j 1.8.20 的 NodeHook 抛出 / failedFuture <em>不</em>传播为流错误（图会继续推进），engine-side
 * 事件计数又会与图内部异常赛跑；原生递归限制是确定可用的路径。
 *
 * <p>边界（#4）：只产事件流，不分配 seq、不落盘、不 SSE（seq/eventId 由 #6 落盘分配）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
@Slf4j
@Component
public class AgentEngine {

    /** seq 占位值，由 #6 落盘时分配真实序号。 */
    private static final long SEQ_PLACEHOLDER = 0L;

    /** langgraph4j 递归限制超限异常消息标记（1.8.20：{@code Maximum number of iterations (N) reached!}）。 */
    private static final String MAX_ITERATIONS_REACHED_MARKER = "Maximum number of iterations";

    private final CompiledGraph<AgentExecutor.State> graph;

    private final AgentProperties agentProperties;

    private final ChatProperties chatProperties;

    private final ToolRegistry toolRegistry;

    /**
     * 构造运行时入口。
     *
     * @param graph           单例编译图
     * @param agentProperties Agent 运行时参数（截断阈值 / 保留轮数 / 最大轮次）
     * @param chatProperties  LLM 参数（模型名，填 SESSION_START）
     * @param toolRegistry    工具注册中心（填 SESSION_START 工具清单）
     */
    public AgentEngine(CompiledGraph<AgentExecutor.State> graph,
                       AgentProperties agentProperties,
                       ChatProperties chatProperties,
                       ToolRegistry toolRegistry) {
        this.graph = graph;
        this.agentProperties = agentProperties;
        this.chatProperties = chatProperties;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 驱动一次会话轮次，产出有序 {@link AgentEvent} 流。
     *
     * @param sessionId 会话 ID（同时作 threadId）
     * @param history   已加载的历史消息（Spring AI Message）
     * @param userText  本轮用户输入文本
     * @return 有序事件流
     */
    public Flux<AgentEvent> run(UUID sessionId, List<Message> history, String userText) {
        String turnId = UUID.randomUUID().toString();
        String threadId = sessionId.toString();
        log.info("AgentLoop 启动: sessionId={} turnId={}", sessionId, turnId);

        List<Message> messages = new ArrayList<>(history);
        messages.add(new UserMessage(userText));
        List<Message> truncated = ContextManager.truncate(messages, null,
                agentProperties.getContextBudgetTokens(), agentProperties.getKeepRounds());
        Map<String, Object> initialState = Map.of(MessagesState.MESSAGES_STATE, truncated);
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        var generator = graph.stream(initialState, config);
        Flux<AgentEvent> graphEvents = AgentEventMapper.toFlux(generator, sessionId, turnId)
                .filter(event -> event.getEventType() != EventType.DONE);

        AgentEvent sessionStart = eventOf(sessionId, turnId, EventType.SESSION_START, sessionStartPayload());
        AgentEvent turnEnd = eventOf(sessionId, turnId, EventType.TURN_END, turnEndPayload(turnId));

        return Flux.concat(Flux.just(sessionStart), graphEvents)
                .concatWith(Flux.defer(() -> Flux.just(
                        turnEnd,
                        eventOf(sessionId, turnId, EventType.DONE, donePayload(DoneSubtype.SUCCESS)))))
                .onErrorResume(error -> {
                    DoneSubtype subtype = mapSubtype(error);
                    log.warn("AgentLoop 以结构化结束收尾: sessionId={} subtype={}", sessionId, subtype.getValue(), error);
                    return Flux.just(eventOf(sessionId, turnId, EventType.DONE, donePayload(subtype)));
                });
    }

    /**
     * 把异常映射为 {@link DoneSubtype}（沿完整 cause 链查找信号异常——langgraph4j 会用 CompletionException /
     * ExecutionException / GraphRunnerException 等多层包装节点异常）。优先识别具体信号
     * （LlmException / ModerationBlockedException）；图原生递归限制（compile 期 {@code recursionLimit}）抛
     * {@code IllegalStateException: Maximum number of iterations (N) reached!}（langgraph4j 1.8.20）→ {@code error_max_turns}；
     * 其余运行时异常归 {@code error_during_execution}。
     *
     * @param error 异常
     * @return 结束码
     */
    private DoneSubtype mapSubtype(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ModerationBlockedException) {
                return DoneSubtype.ERROR_BLOCKED_TOOL;
            }
            if (current instanceof LlmException) {
                return DoneSubtype.ERROR_DURING_EXECUTION;
            }
            current = current.getCause();
        }
        // 图递归限制（最大轮次守卫）：langgraph4j 1.8.20 以 IllegalStateException 表达「Maximum number of iterations (N) reached!」
        current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(MAX_ITERATIONS_REACHED_MARKER)) {
                return DoneSubtype.ERROR_MAX_TURNS;
            }
            current = current.getCause();
        }
        return DoneSubtype.ERROR_DURING_EXECUTION;
    }

    private AgentEvent eventOf(UUID sessionId, String turnId, EventType type, JsonNode payload) {
        return AgentEvent.create(sessionId, SEQ_PLACEHOLDER, turnId, type, payload);
    }

    private JsonNode sessionStartPayload() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("model", chatProperties.getModel());
        var toolsArray = JsonNodeFactory.instance.arrayNode();
        for (var descriptor : toolRegistry.describeAll()) {
            toolsArray.add(descriptor.name());
        }
        node.set("tools", toolsArray);
        return node;
    }

    private JsonNode turnEndPayload(String turnId) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("turnId", turnId);
        return node;
    }

    private JsonNode donePayload(DoneSubtype subtype) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("subtype", subtype.getValue());
        return node;
    }
}
