package com.astraflow.agent.domain.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * 工具调用上下文（不可变值对象）。
 *
 * <p>承载会话标识 {@code sessionId}、轮次标识 {@code turnId}、本次调用的 {@code toolUseId}，
 * 以及解析后的工具入参 {@code input}（JSON 结构），供工具实现读取。并行执行时每个调用独占一个本对象。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public record ToolContext(UUID sessionId, String turnId, String toolUseId, JsonNode input) {
}
