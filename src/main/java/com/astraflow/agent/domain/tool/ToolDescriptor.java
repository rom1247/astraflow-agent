package com.astraflow.agent.domain.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具描述项（不可变值对象），即注入 LLM 的工具声明清单单元。
 *
 * <p>仅含 {@code name} + {@code description} + {@code inputSchema} 三要素（不含 {@code isReadOnly}，
 * 因 LLM 工具声明不需要副作用标记），由 {@code ToolRegistry.describeAll()} 输出，可被 Jackson 序列化。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public record ToolDescriptor(String name, String description, JsonNode inputSchema) {
}
