package com.astraflow.agent.domain.tool;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 工具执行结构化错误码，消除错误信息中的魔法字符串。
 *
 * <p>序列化为小写下划线 value（{@link JsonValue}），供 LLM / 前端 / 日志统一识别；
 * 区分 schema 校验类、工具业务类、网络 / HTTP 类、出站策略类错误，使 {@link ToolInvoker}
 * 能据错误码区分「校验失败」与「业务失败」（spec「错误码可区分两类失败」）。
 *
 * <p>注：使用 Jackson 2.x 注解（{@code com.fasterxml.jackson.annotation}），与 domain 层
 * 序列化惯例一致（见 {@code domain-model.md}），与 Boot 自动配置的 Jackson 3.x 不同源。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public enum ToolErrorCode {

    /** 入参 JSON Schema 校验失败（缺必填 / 类型不匹配 / 字段超长）。 */
    SCHEMA_VALIDATION_FAILED("schema_validation_failed"),

    /** 工具名未在注册中心登记。 */
    UNKNOWN_TOOL("unknown_tool"),

    /** 算术表达式非法（含非算术字符 / 括号不匹配等）。 */
    INVALID_EXPRESSION("invalid_expression"),

    /** 除零。 */
    DIVISION_BY_ZERO("division_by_zero"),

    /** 网络失败（连接超时 / 目标不可达）。 */
    NETWORK_ERROR("network_error"),

    /** HTTP 错误状态码（4xx / 5xx）。 */
    HTTP_ERROR("http_error"),

    /** 被出站审查策略拦截（目标域名不在白名单，#7 moderation 挂载点）。 */
    BLOCKED_BY_OUTBOUND_POLICY("blocked_by_outbound_policy");

    /** 序列化值（小写下划线）。 */
    private final String value;

    ToolErrorCode(String value) {
        this.value = value;
    }

    /**
     * 获取序列化值。
     *
     * @return 小写下划线字符串
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 按 value 反序列化。
     *
     * @param value 序列化值
     * @return 匹配的枚举
     * @throws IllegalArgumentException 无匹配 value
     */
    @JsonCreator
    public static ToolErrorCode fromValue(String value) {
        return Arrays.stream(values())
                .filter(code -> code.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知工具错误码: " + value));
    }
}
