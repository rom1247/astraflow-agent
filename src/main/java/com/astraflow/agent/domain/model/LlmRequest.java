package com.astraflow.agent.domain.model;

/**
 * LLM 流式调用请求（领域类型，零 Spring AI 依赖）。
 *
 * @param text 用户输入文本
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public record LlmRequest(String text) {
}
