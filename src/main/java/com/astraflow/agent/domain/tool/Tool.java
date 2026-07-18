package com.astraflow.agent.domain.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具端口（框架无关的领域端口）。
 *
 * <p>统一描述可被 AgentLoop 调用的工具：每个工具暴露唯一 {@code name}、给 LLM 的 {@code description}、
 * JSON Schema 形式的入参契约 {@code inputSchema}、副作用标记 {@code isReadOnly}，
 * 以及同步执行入口 {@code call(ToolContext)}（并行执行不在本端口职责内，由下游 ToolExecutor 负责）。
 *
 * <p>本端口零框架依赖；IO 型工具（如 http_get）下沉 infrastructure 实现，依赖经 {@link ToolContext} 传入。
 * 对下游 change（#4）的稳定接缝契约即本端口四元组 + {@link ToolContext}。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public interface Tool {

    /**
     * 工具唯一标识（全小写下划线）。
     *
     * @return 工具名
     */
    String name();

    /**
     * 给 LLM 的工具用途说明。
     *
     * @return 描述文本
     */
    String description();

    /**
     * 工具入参的 JSON Schema 契约（用于调用前校验 + 注入 LLM 的工具声明）。
     *
     * @return JSON Schema 节点（com.fasterxml.jackson 2.x）
     */
    JsonNode inputSchema();

    /**
     * 是否无副作用（纯计算 / 只读），用于区分写操作。
     *
     * @return true 表示工具无副作用
     */
    boolean isReadOnly();

    /**
     * 同步执行工具。
     *
     * @param context 工具调用上下文（含入参）
     * @return 执行结果（成功携带输出，失败携带结构化错误，不抛异常外泄）
     */
    ToolResult call(ToolContext context);
}
