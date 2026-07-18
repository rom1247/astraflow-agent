package com.astraflow.agent.domain.tool;

/**
 * 工具执行结果（不可变值对象）。
 *
 * <p>以 {@link ToolResultStatus} 区分成功 / 失败：成功携带 {@code output}（可 Jackson 序列化的执行结果），
 * 失败携带 {@link ToolErrorCode} + 中文 {@code errorMessage}。工具失败时不应抛异常外泄给调用方，
 * 统一以本值对象表达（spec「错误结果携带结构化信息」）。
 *
 * <p>使用 record 保证不可变（无 setter，满足 {@code red-lines.md} 禁 {@code @Setter}/{@code @Data}）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public record ToolResult(ToolResultStatus status, Object output, ToolErrorCode errorCode, String errorMessage) {

    /**
     * 构造成功结果。
     *
     * @param output 可 Jackson 序列化的执行结果（数值 / 字符串 / Map / JsonNode 等）
     * @return status 为 {@link ToolResultStatus#SUCCESS} 的结果
     */
    public static ToolResult success(Object output) {
        return new ToolResult(ToolResultStatus.SUCCESS, output, null, null);
    }

    /**
     * 构造失败结果。
     *
     * @param errorCode    结构化错误码
     * @param errorMessage 中文错误描述（含定位信息）
     * @return status 为 {@link ToolResultStatus#ERROR} 的结果
     */
    public static ToolResult error(ToolErrorCode errorCode, String errorMessage) {
        return new ToolResult(ToolResultStatus.ERROR, null, errorCode, errorMessage);
    }
}
