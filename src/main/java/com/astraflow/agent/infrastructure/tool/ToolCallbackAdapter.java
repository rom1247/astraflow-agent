package com.astraflow.agent.infrastructure.tool;

import com.astraflow.agent.domain.tool.Tool;
import com.astraflow.agent.domain.tool.ToolContext;
import com.astraflow.agent.domain.tool.ToolInvoker;
import com.astraflow.agent.domain.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

/**
 * 把领域 {@link Tool}（经 {@link ToolInvoker}）适配为 Spring AI {@link ToolCallback}（#2→#4 核心接缝，design D5）。
 *
 * <p>一身二用，使 AgentLoop 对 LLM 的<strong>工具声明</strong>（注入模型的工具清单）与<strong>工具执行</strong>
 * （节点的工具回调）统一经领域端口流转：
 * <ul>
 *   <li>声明侧：{@link #getToolDefinition()} 暴露 {@link Tool#name()} / {@link Tool#description()} /
 *       {@link Tool#inputSchema()}，喂 {@code AgentChatService} 的 {@code toolCallbacks}</li>
 *   <li>执行侧：{@link #call(String)} 解析 JSON 入参 → 构造领域 {@link ToolContext} →
 *       {@link ToolInvoker#invoke}（自动获得「查找 → JSON Schema 校验 → 执行」与结构化错误）→ 序列化响应</li>
 * </ul>
 *
 * <p>适配 MUST NOT 绕过 {@link ToolInvoker} 的 JSON Schema 校验直接调 {@link Tool#call}（spec 约束）。
 * 校验失败 / 业务错误经 {@link ToolInvoker} 结构化返回，不抛异常外泄。
 *
 * <p>序列化使用 Jackson 2.x（自建 {@link ObjectMapper}，符合 {@code domain-model.md} 红线——禁注入 ObjectMapper）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public class ToolCallbackAdapter implements ToolCallback {

    /** Jackson 2.x 序列化器（自建，禁注入——见 domain-model.md 红线）。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Spring AI ToolContext 中承载领域 sessionId 的键。 */
    private static final String CTX_SESSION_ID = "sessionId";

    /** Spring AI ToolContext 中承载领域 turnId 的键。 */
    private static final String CTX_TURN_ID = "turnId";

    /** Spring AI ToolContext 中承载领域 toolUseId 的键。 */
    private static final String CTX_TOOL_USE_ID = "toolUseId";

    private final Tool tool;

    private final ToolInvoker toolInvoker;

    /**
     * 构造适配器。
     *
     * @param tool        领域工具（声明来源）
     * @param toolInvoker 工具调用编排器（执行经其校验 + 执行）
     */
    public ToolCallbackAdapter(Tool tool, ToolInvoker toolInvoker) {
        this.tool = tool;
        this.toolInvoker = toolInvoker;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return new DefaultToolDefinition(tool.name(), tool.description(), serializeSchema(tool.inputSchema()));
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
        JsonNode input = parseArguments(toolInput);
        ToolContext domainContext = toDomainContext(toolContext, input);
        ToolResult result = toolInvoker.invoke(tool.name(), domainContext);
        return serialize(result);
    }

    /**
     * 把 Spring AI {@link ToolContext}（Map）转换为领域 {@link ToolContext}，提取 sessionId / turnId / toolUseId。
     *
     * @param toolContext Spring AI 工具上下文（可空）
     * @param input       解析后的入参
     * @return 领域工具上下文
     */
    private ToolContext toDomainContext(org.springframework.ai.chat.model.ToolContext toolContext, JsonNode input) {
        java.util.UUID sessionId = null;
        String turnId = null;
        String toolUseId = null;
        if (toolContext != null) {
            Map<String, Object> context = toolContext.getContext();
            sessionId = toUuid(context.get(CTX_SESSION_ID));
            Object turnIdValue = context.get(CTX_TURN_ID);
            if (turnIdValue instanceof String value) {
                turnId = value;
            }
            Object toolUseIdValue = context.get(CTX_TOOL_USE_ID);
            if (toolUseIdValue instanceof String value) {
                toolUseId = value;
            }
        }
        return new ToolContext(sessionId, turnId, toolUseId, input);
    }

    private java.util.UUID toUuid(Object value) {
        if (value instanceof java.util.UUID uuid) {
            return uuid;
        }
        if (value instanceof String text) {
            try {
                return java.util.UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private JsonNode parseArguments(String toolInput) {
        try {
            if (toolInput == null || toolInput.isBlank()) {
                return MAPPER.nullNode();
            }
            return MAPPER.readTree(toolInput);
        } catch (com.fasterxml.jackson.core.JsonProcessingException parseError) {
            // 非法 JSON 入参 → null 节点，交由 ToolInvoker/校验器报结构化 schema 失败
            return MAPPER.nullNode();
        }
    }

    private String serializeSchema(JsonNode schema) {
        try {
            return MAPPER.writeValueAsString(schema);
        } catch (com.fasterxml.jackson.core.JsonProcessingException serializeError) {
            return "{}";
        }
    }

    private String serialize(ToolResult result) {
        try {
            return MAPPER.writeValueAsString(result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException serializeError) {
            return "{}";
        }
    }
}
