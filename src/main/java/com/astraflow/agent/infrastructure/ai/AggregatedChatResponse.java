package com.astraflow.agent.infrastructure.ai;

import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;

/**
 * 流式响应聚合结果（不可变值对象）。
 *
 * @param text      拼接后的完整文本（逐 chunk delta 累加）
 * @param toolCalls 保序的结构化工具调用（完整 id / name / arguments）
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public record AggregatedChatResponse(String text, List<AssistantMessage.ToolCall> toolCalls) {
}
