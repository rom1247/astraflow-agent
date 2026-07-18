package com.astraflow.agent.domain.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolResult} 值对象测试。
 *
 * <p>覆盖 spec「成功结果携带输出」「错误结果携带结构化信息」。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class ToolResultTest {

    /** 领域值对象序列化用 Jackson 2.x（与 domain-model.md 一致，非 Boot 自动配置的 3.x）。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("success - status 为 SUCCESS 且 output 可被 Jackson 序列化")
    void testSuccess_statusIsSuccessAndOutputSerializable() throws Exception {
        ToolResult result = ToolResult.success(9);

        assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(result.output()).isEqualTo(9);

        String json = objectMapper.writeValueAsString(result);
        assertThat(json).contains("\"status\":\"SUCCESS\"");
        assertThat(json).contains("\"output\":9");
    }

    @Test
    @DisplayName("error - status 为 ERROR 且携带错误码与中文描述")
    void testError_carriesErrorCodeAndMessage() throws Exception {
        ToolResult result = ToolResult.error(ToolErrorCode.INVALID_EXPRESSION, "表达式非法");

        assertThat(result.status()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.INVALID_EXPRESSION);
        assertThat(result.errorMessage()).isEqualTo("表达式非法");

        String json = objectMapper.writeValueAsString(result);
        assertThat(json).contains("\"errorCode\":\"invalid_expression\"");
        assertThat(json).contains("\"errorMessage\":\"表达式非法\"");
    }
}
