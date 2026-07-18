package com.astraflow.agent.domain.model;

/**
 * LLM 流式输出增量（领域类型，零 Spring AI 依赖）。
 *
 * <p>表示按到达顺序产出的 token 文本增量，调用方可聚合为完整文本。
 *
 * @param text token 文本增量（可能为空）
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public record LlmChunk(String text) {
}
